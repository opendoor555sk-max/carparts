import os
import uuid
import json
import logging
import asyncio
from pathlib import Path
from datetime import datetime, timezone, timedelta
from typing import List, Optional, Dict, Any

import jwt
import bcrypt
import requests
from cryptography.fernet import Fernet
from fastapi import FastAPI, APIRouter, HTTPException, Depends, UploadFile, File, Form, Header
from fastapi.responses import Response
from fastapi.concurrency import run_in_threadpool
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseModel, Field

ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / '.env')

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger("kabadi")

# ---------------- Mongo ----------------
mongo_url = os.environ['MONGO_URL']
client = AsyncIOMotorClient(mongo_url)
db = client[os.environ['DB_NAME']]

# ---------------- Config ----------------
JWT_SECRET = os.environ['JWT_SECRET']
JWT_ISSUER = os.environ.get('JWT_ISSUER', 'kabadi-api')
ACCESS_MINUTES = int(os.environ.get('ACCESS_MINUTES', '720'))
EMERGENT_LLM_KEY = os.environ.get('EMERGENT_LLM_KEY')
GEMINI_API_KEY = os.environ.get('GEMINI_API_KEY')
GEMINI_MODEL = os.environ.get('GEMINI_MODEL', 'gemini-3.7-flash')
GEMINI_GROUNDING = os.environ.get('GEMINI_GROUNDING', 'false').lower() == 'true'
TAVILY_API_KEY = os.environ.get('TAVILY_API_KEY')

# ---------------- Object storage ----------------
STORAGE_BASE = (os.environ.get("INTEGRATION_PROXY_URL") or "").strip() or "https://integrations.emergentagent.com"
STORAGE_URL = STORAGE_BASE.rstrip("/") + "/objstore/api/v1/storage"
APP_NAME = "kabadi-market-hisab"
_storage_key = None


def init_storage():
    global _storage_key
    if _storage_key:
        return _storage_key
    resp = requests.post(f"{STORAGE_URL}/init", json={"emergent_key": EMERGENT_LLM_KEY}, timeout=30)
    resp.raise_for_status()
    _storage_key = resp.json()["storage_key"]
    return _storage_key


def put_object(path: str, data: bytes, content_type: str) -> dict:
    key = init_storage()
    resp = requests.put(f"{STORAGE_URL}/objects/{path}",
                        headers={"X-Storage-Key": key, "Content-Type": content_type}, data=data, timeout=120)
    resp.raise_for_status()
    return resp.json()


def get_object(path: str):
    key = init_storage()
    resp = requests.get(f"{STORAGE_URL}/objects/{path}", headers={"X-Storage-Key": key}, timeout=60)
    resp.raise_for_status()
    return resp.content, resp.headers.get("Content-Type", "application/octet-stream")


# ---------------- App ----------------
app = FastAPI(title="Kabadi Market Hisab API")
api = APIRouter(prefix="/api")


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def new_id() -> str:
    return str(uuid.uuid4())


# ---------------- Permissions ----------------
ALL_PERMISSIONS = [
    "search", "buy", "sell", "requirement", "manage_parts",
    "view_price", "manage_limits", "manage_users", "ai_approve", "view_stats"
]
STAFF_DEFAULT = ["search", "buy", "sell", "requirement", "manage_parts"]

COMPANIES = ["Hyundai+Kia", "Maruti", "Tata", "Mahindra", "All"]

CATEGORY_MASTER = [
    {"group": "Control Modules", "items": [
        "Engine Control Module (ECM/ECU)", "Body Control Module (BCM)", "Transmission Control Module (TCM)",
        "Airbag Control Module (SRS)", "ABS Control Module", "Power Steering Control Module (EPS)",
        "Immobilizer Control Unit", "Smart Key Control Module", "Instrument Cluster Control",
        "Climate Control Module (AC ECU)", "Fuel Pump Control Module", "Suspension Control Module",
        "Gateway Control Module", "Battery Management Module (BMS)", "Telematics Control Unit (TCU)",
    ]},
    {"group": "Sensors", "items": [
        "Oxygen Sensor (O2)", "MAP Sensor", "MAF Sensor", "Crankshaft Position Sensor",
        "Camshaft Position Sensor", "Knock Sensor", "Coolant Temperature Sensor",
        "Intake Air Temperature Sensor", "Throttle Position Sensor", "ABS Wheel Speed Sensor",
        "Parking Sensor", "Steering Angle Sensor", "Fuel Level Sensor", "Oil Pressure Sensor",
        "EGR Sensor", "NOx Sensor", "Rain/Light Sensor", "Tyre Pressure Sensor (TPMS)",
    ]},
    {"group": "Motors & Actuators", "items": [
        "Starter Motor", "Alternator", "Wiper Motor", "Power Window Motor", "Radiator Fan Motor",
        "Blower Motor", "Fuel Pump Motor", "Throttle Body Actuator", "EGR Valve Actuator",
        "Door Lock Actuator", "Seat Adjust Motor", "Sunroof Motor", "Mirror Fold Motor",
        "Idle Air Control Valve", "Turbo Actuator",
    ]},
    {"group": "Switches & Electrical", "items": [
        "Ignition Switch", "Power Window Switch", "Headlight Switch", "Combination Switch",
        "Hazard Switch", "Door Lock Switch", "Brake Light Switch", "Multipurpose Relay",
        "Fuse Box / Junction Box", "Wiring Harness", "Horn", "Battery", "Ignition Coil",
    ]},
    {"group": "Interior / Electronic", "items": [
        "Infotainment Head Unit", "Instrument Cluster (Speedometer)", "AC Control Panel",
        "Steering Wheel Controls", "Rear View Camera", "Speaker / Amplifier", "Antenna Module",
        "USB / AUX Module", "Interior Light Module", "Power Seat Module", "Digital Clock / Display",
        "Dashcam / DVR Module",
    ]},
]

CONDITIONS = ["Working", "Testing", "Repairable", "Damaged", "Incomplete", "Scrap", "Unknown"]


# ---------------- Auth helpers ----------------
def hash_pw(pw: str) -> str:
    return bcrypt.hashpw(pw.encode(), bcrypt.gensalt()).decode()


def verify_pw(pw: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(pw.encode(), hashed.encode())
    except Exception:
        return False


# Reversible encryption for admin-viewable passwords (internal single-shop tool).
# NOTE: bcrypt hash is used for login; this encrypted copy exists ONLY so the
# admin can reveal a staff password on demand. Key lives in backend/.env.
_fernet = Fernet(os.environ["FERNET_KEY"].encode())


def encrypt_pw(pw: str) -> str:
    return _fernet.encrypt(pw.encode()).decode()


def decrypt_pw(token: str) -> Optional[str]:
    try:
        return _fernet.decrypt(token.encode()).decode()
    except Exception:
        return None


def make_token(user: dict) -> str:
    now = datetime.now(timezone.utc)
    claims = {
        "sub": user["id"], "username": user["username"], "role": user["role"],
        "iat": now, "exp": now + timedelta(minutes=ACCESS_MINUTES), "iss": JWT_ISSUER,
    }
    return jwt.encode(claims, JWT_SECRET, algorithm="HS256")


def public_user(u: dict) -> dict:
    perms = ALL_PERMISSIONS if u["role"] == "admin" else u.get("permissions", [])
    return {"id": u["id"], "name": u["name"], "username": u["username"], "role": u["role"],
            "permissions": perms, "disabled": u.get("disabled", False),
            "has_google_key": bool(u.get("google_api_key") and u.get("google_cx"))}


async def get_current_user(authorization: Optional[str] = Header(None)) -> dict:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Not authenticated")
    token = authorization.split(" ", 1)[1]
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=["HS256"], issuer=JWT_ISSUER,
                             options={"require": ["sub", "exp", "iat"]})
    except Exception:
        raise HTTPException(401, "Invalid or expired token")
    user = await db.users.find_one({"id": payload["sub"]}, {"_id": 0})
    if not user or user.get("disabled"):
        raise HTTPException(401, "User not found or disabled")
    return user


def require(permission: str):
    async def dep(user=Depends(get_current_user)):
        perms = ALL_PERMISSIONS if user["role"] == "admin" else user.get("permissions", [])
        if permission not in perms:
            raise HTTPException(403, f"Permission denied: {permission}")
        return user
    return dep


async def require_admin(user=Depends(get_current_user)):
    if user.get("role") != "admin":
        raise HTTPException(403, "ફક્ત Admin આ કરી શકે")
    return user


# ---------------- Models ----------------
class LoginIn(BaseModel):
    username: str
    password: str


class ApiSettingsIn(BaseModel):
    google_api_key: Optional[str] = None
    google_cx: Optional[str] = None


class ChangePasswordIn(BaseModel):
    current_password: str
    new_password: str


class UserCreate(BaseModel):
    name: str
    username: str
    password: str
    role: str = "staff"
    permissions: Optional[List[str]] = None


class UserUpdate(BaseModel):
    name: Optional[str] = None
    username: Optional[str] = None
    permissions: Optional[List[str]] = None
    disabled: Optional[bool] = None
    password: Optional[str] = None


class PartCreate(BaseModel):
    part_number: str
    company: Optional[str] = "All"
    name: Optional[str] = ""
    category: Optional[str] = ""
    compatible_vehicles: Optional[List[str]] = []
    variant: Optional[str] = ""
    year: Optional[str] = ""
    old_number: Optional[str] = ""
    new_number: Optional[str] = ""
    barcode: Optional[str] = ""
    sticker_color: Optional[str] = ""
    technical_info: Optional[str] = ""
    photos: Optional[List[str]] = []
    source: Optional[str] = "Manual"


class PartUpdate(BaseModel):
    company: Optional[str] = None
    name: Optional[str] = None
    category: Optional[str] = None
    compatible_vehicles: Optional[List[str]] = None
    variant: Optional[str] = None
    year: Optional[str] = None
    old_number: Optional[str] = None
    new_number: Optional[str] = None
    sticker_color: Optional[str] = None
    technical_info: Optional[str] = None
    photos: Optional[List[str]] = None


class BuyIn(BaseModel):
    part_number: str
    company: Optional[str] = "All"
    name: Optional[str] = ""
    category: Optional[str] = ""
    compatible_vehicles: Optional[List[str]] = []
    variant: Optional[str] = ""
    condition: str = "Unknown"
    location: Optional[Dict[str, str]] = {}
    price: Optional[float] = None
    photos: Optional[List[str]] = []
    barcode: Optional[str] = ""
    override: bool = False


class SellIn(BaseModel):
    part_number: str
    unit_id: Optional[str] = None
    price: Optional[float] = None
    buyer: Optional[str] = ""


class KnownPartIn(BaseModel):
    part_number: str
    company: Optional[str] = "All"
    name: Optional[str] = ""
    category: Optional[str] = ""
    note: Optional[str] = ""


class RequirementIn(BaseModel):
    part_number: str
    company: Optional[str] = "All"
    name: Optional[str] = ""
    category: Optional[str] = ""
    priority: str = "Medium"
    quantity: int = 1
    note: Optional[str] = ""


class RequirementUpdate(BaseModel):
    status: Optional[str] = None
    priority: Optional[str] = None
    quantity: Optional[int] = None


class LimitIn(BaseModel):
    part_number: str
    limit: Optional[int] = None
    enabled: bool = True


class GlobalLimitIn(BaseModel):
    global_default: Optional[int] = None
    global_enabled: bool = False


class AiResearchIn(BaseModel):
    part_number: str
    company: Optional[str] = "All"


class PartEditIn(BaseModel):
    name: Optional[str] = None
    category: Optional[str] = None
    company: Optional[str] = None
    compatible_vehicles: Optional[List[str]] = None
    variant: Optional[str] = None
    year: Optional[str] = None
    old_number: Optional[str] = None
    new_number: Optional[str] = None
    sticker_color: Optional[str] = None
    technical_info: Optional[str] = None


# ---------------- Startup ----------------
@app.on_event("startup")
async def startup():
    # indexes
    await db.users.create_index("username", unique=True)
    await db.users.create_index("id", unique=True)
    await db.parts.create_index("part_number", unique=True)
    # seed admin
    admin_username = os.environ.get("ADMIN_USERNAME", "abdul").lower()
    existing = await db.users.find_one({"username": admin_username})
    if not existing:
        await db.users.insert_one({
            "id": new_id(),
            "name": os.environ.get("ADMIN_NAME", "Abdul Salam"),
            "username": admin_username,
            "password_hash": hash_pw(os.environ.get("ADMIN_PASSWORD", "Salam@123")),
            "password_enc": encrypt_pw(os.environ.get("ADMIN_PASSWORD", "Salam@123")),
            "role": "admin", "permissions": ALL_PERMISSIONS, "disabled": False,
            "created_at": now_iso(),
        })
        logger.info("Seeded main admin user")
    elif not existing.get("password_enc"):
        # backfill encrypted copy for the seeded admin so it can be revealed
        await db.users.update_one({"id": existing["id"]},
                                  {"$set": {"password_enc": encrypt_pw(os.environ.get("ADMIN_PASSWORD", "Salam@123"))}})
    # settings
    if not await db.settings.find_one({"key": "purchase_limit"}):
        await db.settings.insert_one({"key": "purchase_limit", "global_enabled": False, "global_default": None})
    try:
        await run_in_threadpool(init_storage)
    except Exception as e:
        logger.warning(f"Object storage init failed (non-fatal): {e}")


@app.on_event("shutdown")
async def shutdown():
    client.close()


# ---------------- Auth routes ----------------
@api.get("/")
async def root():
    return {"app": "Kabadi Market Hisab", "status": "ok"}


@api.post("/auth/login")
async def login(body: LoginIn):
    user = await db.users.find_one({"username": body.username.lower().strip()})
    if not user or not verify_pw(body.password, user["password_hash"]):
        raise HTTPException(401, "ખોટું username અથવા password")
    if user.get("disabled"):
        raise HTTPException(403, "User disabled")
    return {"access_token": make_token(user), "token_type": "bearer", "user": public_user(user)}


@api.get("/auth/me")
async def me(user=Depends(get_current_user)):
    return public_user(user)


@api.get("/auth/settings")
async def get_settings(user=Depends(get_current_user)):
    return {"google_cx": user.get("google_cx", ""), "has_google_key": bool(user.get("google_api_key"))}


@api.post("/auth/change-password")
async def change_password(body: ChangePasswordIn, user=Depends(get_current_user)):
    if not verify_pw(body.current_password, user["password_hash"]):
        raise HTTPException(400, "વર્તમાન password ખોટો છે")
    new_pw = body.new_password
    if len(new_pw) < 6:
        raise HTTPException(422, "નવો password ઓછામાં ઓછો 6 અક્ષર હોવો જોઈએ")
    if new_pw == body.current_password:
        raise HTTPException(422, "નવો password જૂના કરતાં અલગ હોવો જોઈએ")
    await db.users.update_one(
        {"id": user["id"]},
        {"$set": {"password_hash": hash_pw(new_pw), "password_enc": encrypt_pw(new_pw), "password_changed_at": now_iso()}},
    )
    return {"ok": True}


@api.post("/auth/settings")
async def save_settings(body: ApiSettingsIn, user=Depends(get_current_user)):
    updates = {}
    if body.google_api_key is not None:
        updates["google_api_key"] = body.google_api_key.strip()
    if body.google_cx is not None:
        updates["google_cx"] = body.google_cx.strip()
    if updates:
        await db.users.update_one({"id": user["id"]}, {"$set": updates})
    fresh = await db.users.find_one({"id": user["id"]})
    return {"ok": True, "google_cx": fresh.get("google_cx", ""), "has_google_key": bool(fresh.get("google_api_key"))}


# ---------------- BYO-Key Google Custom Search + keyword autofill ----------------
_CS_MODELS = ["Grand i10 Nios", "Grand i10", "Creta", "Seltos", "Venue", "Verna", "Alcazar",
              "Carens", "Sonet", "i20", "i10", "Aura", "Nios", "Exter", "Tucson", "Kona", "Elantra",
              "Santro", "Xcent", "Rio", "Carnival", "EV6", "Nexon", "Harrier", "Safari", "Tiago",
              "Tigor", "Altroz", "Punch", "Curvv", "Scorpio N", "Scorpio", "XUV700", "XUV300", "XUV400",
              "Thar", "Bolero", "Marazzo", "Swift", "Baleno", "Brezza", "Ertiga", "Dzire", "Wagon R",
              "Alto K10", "Alto", "Celerio", "Ciaz", "Fronx", "Jimny", "XL6", "Grand Vitara", "Ignis"]
_CS_BRANDS = ["Hyundai Mobis", "Hyundai", "Kia", "Maruti Suzuki", "Maruti", "Suzuki", "Tata", "Mahindra", "Mobis"]
_CS_VARIANTS = ["Smart Key", "Push Button Start", "Push Button", "Keyless", "N-Line", "SX(O)", "SX",
                "HTX", "HTK", "HTC", "GTX", "GTK", "VXI", "LXI", "ZXI", "ZDI", "VDI", "XZA", "XZ", "XM",
                "XE", "Turbo", "Diesel", "Petrol", "CNG", "BS6", "Facelift", "Top", "Mid", "Base"]


def _extract_matches(text: str, terms: list) -> list:
    low = text.lower()
    found = []
    for t in terms:
        if t.lower() in low and t not in found:
            found.append(t)
    return found


@api.post("/search/web")
async def web_search(body: AiResearchIn, user=Depends(require("search"))):
    pn = body.part_number.strip()
    if not pn:
        raise HTTPException(400, "Part number required")

    # CACHE — if this part is already verified in the master DB, reuse it (saves user quota).
    verified = await db.parts.find_one({"part_number": pn, "verification_status": "Verified"}, {"_id": 0})
    if verified:
        return {
            "cached": True,
            "company": verified.get("company", ""),
            "brands": [verified.get("company", "")] if verified.get("company") else [],
            "models": verified.get("compatible_vehicles", []),
            "variants": [verified.get("variant")] if verified.get("variant") else [],
            "name": verified.get("name", ""),
            "sources": verified.get("ai_sources", []),
        }

    key = user.get("google_api_key")
    cx = user.get("google_cx")
    if not key or not cx:
        raise HTTPException(400, detail={"code": "NO_KEY",
                                          "message": "પહેલા Settings માં તમારી Google API Key + Search Engine ID (CX) નાખો"})

    def _google_cse():
        return requests.get("https://www.googleapis.com/customsearch/v1",
                            params={"key": key, "cx": cx,
                                    "q": f'"{pn}" Hyundai Kia Maruti Tata Mahindra OEM part which car model variant',
                                    "num": 10}, timeout=30)

    try:
        resp = await run_in_threadpool(_google_cse)
    except Exception as e:
        logger.warning(f"Google CSE call failed: {e}")
        raise HTTPException(400, detail={"code": "SEARCH_ERR",
                                          "message": "Google search failed — key/CX check કરો"})

    if resp.status_code == 429 or resp.status_code == 403:
        raise HTTPException(429, detail={"code": "QUOTA",
                                          "message": "તમારી Google API limit પૂરી થઈ / key invalid — Google Console માં check કરો"})
    if resp.status_code != 200:
        raise HTTPException(400, detail={"code": "SEARCH_ERR",
                                          "message": f"Google search error {resp.status_code} — key/CX check કરો"})

    items = resp.json().get("items", [])
    blob = " ".join(f"{it.get('title', '')} {it.get('snippet', '')}" for it in items)
    sources = [it.get("title") or it.get("link") for it in items[:6]]
    models = _extract_matches(blob, _CS_MODELS)
    brands = _extract_matches(blob, _CS_BRANDS)
    variants = _extract_matches(blob, _CS_VARIANTS)
    company = brands[0] if brands else ""
    # log the search (no stock change)
    await db.search_history.update_one({"part_number": pn},
                                       {"$inc": {"count": 1}, "$set": {"last_searched": now_iso()}},
                                       upsert=True)
    return {
        "cached": False, "company": company, "brands": brands, "models": models,
        "variants": variants, "name": "", "sources": sources, "result_count": len(items),
    }


# ---------------- Admin: users ----------------
@api.get("/admin/users")
async def list_users(user=Depends(require("manage_users"))):
    users = await db.users.find({"deleted_at": {"$exists": False}}, {"_id": 0, "password_hash": 0}).to_list(500)
    return [{**u, "permissions": ALL_PERMISSIONS if u["role"] == "admin" else u.get("permissions", [])} for u in users]


@api.delete("/admin/users/{user_id}")
async def remove_user(user_id: str, user=Depends(require("manage_users"))):
    target = await db.users.find_one({"id": user_id})
    if not target:
        raise HTTPException(404, "User not found")
    if target["role"] == "admin":
        raise HTTPException(400, "Main Admin ને remove ન કરાય")
    # Soft delete — never destroy data.
    await db.users.update_one({"id": user_id}, {"$set": {"deleted_at": now_iso(), "disabled": True}})
    return {"ok": True}


@api.post("/admin/users")
async def create_user(body: UserCreate, user=Depends(require("manage_users"))):
    if await db.users.find_one({"username": body.username.lower().strip()}):
        raise HTTPException(400, "Username already exists")
    doc = {
        "id": new_id(), "name": body.name, "username": body.username.lower().strip(),
        "password_hash": hash_pw(body.password),
        "password_enc": encrypt_pw(body.password),
        "role": "admin" if body.role == "admin" else "staff",
        "permissions": body.permissions if body.permissions is not None else STAFF_DEFAULT,
        "disabled": False, "created_at": now_iso(),
    }
    await db.users.insert_one(doc)
    return public_user(doc)


@api.patch("/admin/users/{user_id}")
async def update_user(user_id: str, body: UserUpdate, user=Depends(require("manage_users"))):
    target = await db.users.find_one({"id": user_id})
    if not target:
        raise HTTPException(404, "User not found")
    updates = {}
    if body.name is not None and body.name.strip():
        updates["name"] = body.name.strip()
    if body.username is not None and body.username.strip():
        new_un = body.username.lower().strip()
        clash = await db.users.find_one({"username": new_un, "id": {"$ne": user_id}})
        if clash:
            raise HTTPException(400, "Username already exists")
        updates["username"] = new_un
    if body.permissions is not None:
        updates["permissions"] = body.permissions
    if body.disabled is not None:
        updates["disabled"] = body.disabled
    if body.password:
        if len(body.password) < 6:
            raise HTTPException(422, "Password ઓછામાં ઓછો 6 અક્ષર")
        updates["password_hash"] = hash_pw(body.password)
        updates["password_enc"] = encrypt_pw(body.password)
    if updates:
        await db.users.update_one({"id": user_id}, {"$set": updates})
    fresh = await db.users.find_one({"id": user_id}, {"_id": 0})
    return public_user(fresh)


@api.get("/admin/users/{user_id}/password")
async def view_user_password(user_id: str, user=Depends(require_admin)):
    target = await db.users.find_one({"id": user_id})
    if not target:
        raise HTTPException(404, "User not found")
    enc = target.get("password_enc")
    pw = decrypt_pw(enc) if enc else None
    if not pw:
        raise HTTPException(404, detail={"code": "NO_STORED_PW",
                                         "message": "આ user નો password જૂનો છે — reset કરો પછી દેખાશે"})
    return {"username": target["username"], "password": pw}


@api.get("/permissions")
async def permissions_list(user=Depends(get_current_user)):
    return {"all": ALL_PERMISSIONS, "staff_default": STAFF_DEFAULT}


# ---------------- Meta ----------------
@api.get("/companies")
async def companies(user=Depends(get_current_user)):
    return COMPANIES


@api.get("/categories")
async def categories(user=Depends(get_current_user)):
    total = sum(len(g["items"]) for g in CATEGORY_MASTER)
    return {"groups": CATEGORY_MASTER, "total": total}


@api.get("/conditions")
async def conditions(user=Depends(get_current_user)):
    return CONDITIONS


# ---------------- Limit helpers ----------------
async def compute_limit(part_number: str) -> dict:
    part = await db.parts.find_one({"part_number": part_number}, {"_id": 0})
    existing_stock = await db.stock.count_documents({"part_number": part_number, "sold": {"$ne": True}})
    settings = await db.settings.find_one({"key": "purchase_limit"})
    limit_enabled = False
    allowed = None
    source = "none"
    if part and part.get("limit_enabled") and part.get("purchase_limit") is not None:
        limit_enabled = True
        allowed = part["purchase_limit"]
        source = "part"
    elif settings and settings.get("global_enabled") and settings.get("global_default") is not None:
        limit_enabled = True
        allowed = settings["global_default"]
        source = "global"
    remaining = None
    status = "OK"
    if limit_enabled:
        remaining = allowed - existing_stock
        if remaining <= 0:
            status = "STOP"
        elif remaining <= max(1, int(allowed * 0.2)):
            status = "WARNING"
    return {
        "part_number": part_number, "existing_stock": existing_stock,
        "limit_enabled": limit_enabled, "allowed_limit": allowed,
        "remaining": remaining, "status": status, "source": source,
    }


# ---------------- Search ----------------
async def part_status(part_number: str) -> dict:
    part = await db.parts.find_one({"part_number": part_number}, {"_id": 0})
    stock_count = await db.stock.count_documents({"part_number": part_number, "sold": {"$ne": True}})
    known = await db.known_parts.find_one({"part_number": part_number}, {"_id": 0})
    requirement = await db.requirements.find_one(
        {"part_number": part_number, "status": {"$in": ["Pending", "Purchased"]}}, {"_id": 0})
    if stock_count > 0:
        status = "IN STOCK"
    elif requirement:
        status = "REQUIREMENT"
    elif part or known:
        status = "KNOWN PART"
    else:
        status = "NEW PART"
    return {
        "status": status, "part_number": part_number, "part": part,
        "stock_count": stock_count, "known": known, "requirement": requirement,
    }


@api.get("/search")
async def search(q: str, user=Depends(require("search"))):
    pn = q.strip()
    if not pn:
        raise HTTPException(400, "Empty query")
    result = await part_status(pn)
    # log search history / demand (SEARCH does not change stock)
    await db.search_history.update_one(
        {"part_number": pn},
        {"$inc": {"count": 1}, "$set": {"last_searched": now_iso(), "last_status": result["status"]},
         "$setOnInsert": {"first_searched": now_iso()}},
        upsert=True,
    )
    limit = await compute_limit(pn)
    return {**result, "limit": limit}


# ---------------- Parts ----------------
@api.get("/parts")
async def list_parts(company: Optional[str] = None, category: Optional[str] = None,
                     q: Optional[str] = None, user=Depends(get_current_user)):
    query: Dict[str, Any] = {}
    if company and company != "All":
        query["company"] = company
    if category:
        query["category"] = category
    if q:
        query["part_number"] = {"$regex": q.strip(), "$options": "i"}
    parts = await db.parts.find(query, {"_id": 0}).sort("created_at", -1).to_list(500)
    for p in parts:
        p["stock_count"] = await db.stock.count_documents({"part_number": p["part_number"], "sold": {"$ne": True}})
    return parts


@api.post("/parts")
async def create_part(body: PartCreate, user=Depends(require("manage_parts"))):
    pn = body.part_number.strip()
    if not pn:
        raise HTTPException(400, "Part number required")
    if await db.parts.find_one({"part_number": pn}):
        raise HTTPException(400, "Part master already exists (no duplicate)")
    doc = body.dict()
    doc["part_number"] = pn
    doc.update({
        "id": new_id(), "verification_status": "Unverified", "created_at": now_iso(),
        "created_by": user["username"], "purchase_limit": None, "limit_enabled": False,
    })
    await db.parts.insert_one(doc)
    doc.pop("_id", None)
    return doc


@api.get("/parts/{part_number}")
async def get_part(part_number: str, user=Depends(get_current_user)):
    part = await db.parts.find_one({"part_number": part_number}, {"_id": 0})
    if not part:
        raise HTTPException(404, "Part not found")
    units = await db.stock.find({"part_number": part_number, "sold": {"$ne": True}}, {"_id": 0}).to_list(200)
    part["units"] = units
    part["stock_count"] = len(units)
    part["limit"] = await compute_limit(part_number)
    return part


@api.patch("/parts/{part_number}")
async def update_part(part_number: str, body: PartUpdate, user=Depends(require("manage_parts"))):
    updates = {k: v for k, v in body.dict().items() if v is not None}
    if not updates:
        return await get_part(part_number, user)
    r = await db.parts.update_one({"part_number": part_number}, {"$set": updates})
    if r.matched_count == 0:
        raise HTTPException(404, "Part not found")
    return await db.parts.find_one({"part_number": part_number}, {"_id": 0})


# ---------------- Buy (increases stock) ----------------
@api.post("/buy")
async def buy(body: BuyIn, user=Depends(require("buy"))):
    pn = body.part_number.strip()
    if not pn:
        raise HTTPException(400, "Part number required")
    # ensure part master exists (auto-create as Unverified NEW PART)
    part = await db.parts.find_one({"part_number": pn}, {"_id": 0})
    if not part:
        part = {
            "id": new_id(), "part_number": pn, "company": body.company or "All",
            "name": body.name or "", "category": body.category or "",
            "compatible_vehicles": body.compatible_vehicles or [], "variant": body.variant or "",
            "year": "", "old_number": "", "new_number": "", "barcode": body.barcode or "",
            "sticker_color": "", "technical_info": "", "photos": [], "source": "Buy",
            "verification_status": "Unverified", "created_at": now_iso(),
            "created_by": user["username"], "purchase_limit": None, "limit_enabled": False,
        }
        await db.parts.insert_one(dict(part))
    else:
        # Auto-log compatibility under this part number WITHOUT overwriting existing data.
        fill: Dict[str, Any] = {}
        if body.name and not part.get("name"):
            fill["name"] = body.name
        if body.category and not part.get("category"):
            fill["category"] = body.category
        if body.company and body.company != "All" and (not part.get("company") or part.get("company") == "All"):
            fill["company"] = body.company
        if body.compatible_vehicles and not part.get("compatible_vehicles"):
            fill["compatible_vehicles"] = body.compatible_vehicles
        if body.variant and not part.get("variant"):
            fill["variant"] = body.variant
        if fill:
            await db.parts.update_one({"part_number": pn}, {"$set": fill})
    # limit check
    limit = await compute_limit(pn)
    if limit["limit_enabled"] and limit["remaining"] is not None and limit["remaining"] <= 0 and not body.override:
        raise HTTPException(409, detail={"code": "LIMIT_REACHED", "message": "DO NOT BUY — purchase limit reached",
                                          "limit": limit})
    unit = {
        "id": new_id(), "part_number": pn, "condition": body.condition,
        "location": body.location or {}, "photos": body.photos or [],
        "barcode": body.barcode or "", "sold": False, "created_at": now_iso(),
        "added_by": user["username"], "overridden": bool(body.override and limit.get("status") == "STOP"),
    }
    await db.stock.insert_one(dict(unit))
    txn = {"id": new_id(), "type": "buy", "part_number": pn, "unit_id": unit["id"],
           "price": body.price if user["role"] == "admin" else body.price,
           "location": body.location or {}, "by": user["username"], "at": now_iso()}
    await db.transactions.insert_one(dict(txn))
    unit.pop("_id", None)
    new_limit = await compute_limit(pn)
    return {"ok": True, "unit": unit, "limit": new_limit}


# ---------------- Sell (decreases stock) ----------------
@api.post("/sell")
async def sell(body: SellIn, user=Depends(require("sell"))):
    pn = body.part_number.strip()
    query = {"part_number": pn, "sold": {"$ne": True}}
    if body.unit_id:
        query["id"] = body.unit_id
    unit = await db.stock.find_one(query)
    if not unit:
        raise HTTPException(409, detail={"code": "NO_STOCK", "message": "કોઈ stock available નથી — sell ન થાય"})
    await db.stock.update_one({"id": unit["id"]}, {"$set": {"sold": True, "sold_at": now_iso(), "sold_by": user["username"]}})
    txn = {"id": new_id(), "type": "sell", "part_number": pn, "unit_id": unit["id"],
           "price": body.price, "buyer": body.buyer or "", "by": user["username"], "at": now_iso()}
    await db.transactions.insert_one(dict(txn))
    remaining = await db.stock.count_documents({"part_number": pn, "sold": {"$ne": True}})
    return {"ok": True, "remaining_stock": remaining}


# ---------------- Inventory ----------------
@api.get("/inventory")
async def inventory(condition: Optional[str] = None, q: Optional[str] = None, user=Depends(get_current_user)):
    query: Dict[str, Any] = {"sold": {"$ne": True}}
    if condition:
        query["condition"] = condition
    if q:
        query["part_number"] = {"$regex": q.strip(), "$options": "i"}
    units = await db.stock.find(query, {"_id": 0}).sort("created_at", -1).to_list(500)
    # attach part name
    for u in units:
        p = await db.parts.find_one({"part_number": u["part_number"]}, {"_id": 0, "name": 1, "company": 1, "category": 1})
        u["part_name"] = p.get("name", "") if p else ""
        u["company"] = p.get("company", "") if p else ""
    return units


# ---------------- Stock adjust / delete (Admin only) ----------------
class StockAdjustIn(BaseModel):
    part_number: str
    delta: int = 0
    condition: Optional[str] = None
    location: Optional[Dict[str, Any]] = None


@api.post("/stock/adjust")
async def stock_adjust(body: StockAdjustIn, user=Depends(require_admin)):
    pn = body.part_number.strip()
    part = await db.parts.find_one({"part_number": pn})
    if not part:
        raise HTTPException(404, "Part મળ્યો નથી")
    delta = int(body.delta)
    added, removed = 0, 0
    if delta > 0:
        for _ in range(delta):
            unit = {"id": new_id(), "part_number": pn, "condition": body.condition or "Unknown",
                    "location": body.location or {}, "photos": [], "barcode": "",
                    "sold": False, "created_at": now_iso(), "added_by": user["username"], "adjusted": True}
            await db.stock.insert_one(dict(unit))
            added += 1
        await db.transactions.insert_one({"id": new_id(), "type": "adjust_add", "part_number": pn,
                                          "quantity": added, "by": user["username"], "at": now_iso()})
    elif delta < 0:
        units = await db.stock.find({"part_number": pn, "sold": {"$ne": True}}).sort("created_at", -1).to_list(-delta)
        for u in units:
            await db.stock.update_one({"id": u["id"]}, {"$set": {"sold": True, "sold_at": now_iso(),
                                                                 "sold_by": user["username"], "removed_reason": "adjust"}})
            removed += 1
        await db.transactions.insert_one({"id": new_id(), "type": "adjust_remove", "part_number": pn,
                                          "quantity": removed, "by": user["username"], "at": now_iso()})
    remaining = await db.stock.count_documents({"part_number": pn, "sold": {"$ne": True}})
    return {"ok": True, "added": added, "removed": removed, "remaining_stock": remaining}


@api.delete("/stock/unit/{unit_id}")
async def delete_unit(unit_id: str, user=Depends(require_admin)):
    unit = await db.stock.find_one({"id": unit_id})
    if not unit:
        raise HTTPException(404, "Unit મળ્યું નથી")
    await db.stock.delete_one({"id": unit_id})
    await db.transactions.insert_one({"id": new_id(), "type": "delete_unit", "part_number": unit["part_number"],
                                      "unit_id": unit_id, "by": user["username"], "at": now_iso()})
    remaining = await db.stock.count_documents({"part_number": unit["part_number"], "sold": {"$ne": True}})
    return {"ok": True, "remaining_stock": remaining}


# ---------------- Physical stock verification (Admin only) ----------------
async def _expected_stock() -> Dict[str, int]:
    units = await db.stock.find({"sold": {"$ne": True}}, {"_id": 0, "part_number": 1}).to_list(10000)
    counts: Dict[str, int] = {}
    for u in units:
        counts[u["part_number"]] = counts.get(u["part_number"], 0) + 1
    return counts


@api.get("/stock/verification")
async def verification_list(user=Depends(require_admin)):
    counts = await _expected_stock()
    out = []
    for pn, qty in counts.items():
        p = await db.parts.find_one({"part_number": pn}, {"_id": 0, "name": 1, "company": 1})
        out.append({"part_number": pn, "expected": qty,
                    "part_name": (p or {}).get("name", ""), "company": (p or {}).get("company", "")})
    out.sort(key=lambda x: x["part_number"])
    last = await db.verifications.find_one({}, {"_id": 0}, sort=[("at", -1)])
    return {"items": out, "last": last}


class VerifyCount(BaseModel):
    part_number: str
    counted: int


class VerifyIn(BaseModel):
    counts: List[VerifyCount]


@api.post("/stock/verify")
async def verify_stock(body: VerifyIn, user=Depends(require_admin)):
    expected = await _expected_stock()
    counted_map = {c.part_number.strip(): int(c.counted) for c in body.counts}
    discrepancies = []
    for pn in set(expected) | set(counted_map):
        exp = expected.get(pn, 0)
        got = counted_map.get(pn, 0)
        if exp != got:
            p = await db.parts.find_one({"part_number": pn}, {"_id": 0, "name": 1})
            discrepancies.append({"part_number": pn, "part_name": (p or {}).get("name", ""),
                                  "expected": exp, "counted": got, "diff": got - exp,
                                  "status": "MISSING" if got < exp else "EXTRA"})
    total = len(set(expected) | set(counted_map))
    report = {"id": new_id(), "at": now_iso(), "by": user["username"], "total_parts": total,
              "ok_count": total - len(discrepancies), "discrepancies": discrepancies}
    await db.verifications.insert_one(dict(report))
    report.pop("_id", None)
    return report


# ---------------- Known parts ----------------
@api.post("/known-parts")
async def add_known(body: KnownPartIn, user=Depends(require("manage_parts"))):
    pn = body.part_number.strip()
    doc = body.dict()
    doc["part_number"] = pn
    doc.update({"id": new_id(), "created_at": now_iso(), "by": user["username"]})
    await db.known_parts.update_one({"part_number": pn}, {"$set": doc}, upsert=True)
    doc.pop("_id", None)
    return doc


@api.get("/known-parts")
async def list_known(user=Depends(get_current_user)):
    return await db.known_parts.find({}, {"_id": 0}).sort("created_at", -1).to_list(500)


# ---------------- Requirements ----------------
@api.post("/requirements")
async def add_requirement(body: RequirementIn, user=Depends(require("requirement"))):
    doc = body.dict()
    doc["part_number"] = doc["part_number"].strip()
    doc.update({"id": new_id(), "status": "Pending", "created_at": now_iso(), "by": user["username"]})
    await db.requirements.insert_one(dict(doc))
    doc.pop("_id", None)
    return doc


@api.get("/requirements")
async def list_requirements(status: Optional[str] = None, user=Depends(get_current_user)):
    query: Dict[str, Any] = {}
    if status:
        query["status"] = status
    reqs = await db.requirements.find(query, {"_id": 0}).sort("created_at", -1).to_list(500)
    for r in reqs:
        r["stock_count"] = await db.stock.count_documents({"part_number": r["part_number"], "sold": {"$ne": True}})
    return reqs


@api.patch("/requirements/{req_id}")
async def update_requirement(req_id: str, body: RequirementUpdate, user=Depends(require("requirement"))):
    updates = {k: v for k, v in body.dict().items() if v is not None}
    r = await db.requirements.update_one({"id": req_id}, {"$set": updates})
    if r.matched_count == 0:
        raise HTTPException(404, "Requirement not found")
    return await db.requirements.find_one({"id": req_id}, {"_id": 0})


# ---------------- Purchase Limits (admin) ----------------
@api.get("/limits/global")
async def get_global_limit(user=Depends(get_current_user)):
    s = await db.settings.find_one({"key": "purchase_limit"}, {"_id": 0})
    return s or {"global_enabled": False, "global_default": None}


@api.post("/limits/global")
async def set_global_limit(body: GlobalLimitIn, user=Depends(require("manage_limits"))):
    await db.settings.update_one({"key": "purchase_limit"},
                                 {"$set": {"global_enabled": body.global_enabled, "global_default": body.global_default}})
    return await db.settings.find_one({"key": "purchase_limit"}, {"_id": 0})


@api.post("/limits/part")
async def set_part_limit(body: LimitIn, user=Depends(require("manage_limits"))):
    pn = body.part_number.strip()
    r = await db.parts.update_one({"part_number": pn},
                                  {"$set": {"purchase_limit": body.limit, "limit_enabled": body.enabled}})
    if r.matched_count == 0:
        raise HTTPException(404, "Part not found")
    return await compute_limit(pn)


@api.get("/limits/{part_number}")
async def get_part_limit(part_number: str, user=Depends(get_current_user)):
    return await compute_limit(part_number)


# ---------------- Buying Trip ----------------
@api.post("/buying-trip/scan")
async def buying_trip_scan(body: AiResearchIn, user=Depends(require("buying_trip"))):
    pn = body.part_number.strip()
    st = await part_status(pn)
    limit = await compute_limit(pn)
    # buy decision
    if limit["limit_enabled"] and limit["remaining"] is not None and limit["remaining"] <= 0:
        buy_status = "DO NOT BUY"
    elif st["status"] == "REQUIREMENT":
        buy_status = "BUY — REQUIRED"
    elif st["status"] == "IN STOCK" and limit["status"] == "WARNING":
        buy_status = "BUY WITH CAUTION"
    elif st["status"] == "IN STOCK":
        buy_status = "ALREADY IN STOCK"
    else:
        buy_status = "OK TO BUY"
    return {
        "part_number": pn, "status": st["status"], "stock_count": st["stock_count"],
        "requirement": st["requirement"], "limit": limit, "buy_status": buy_status,
    }


# ---------------- AI Research (Gemini) ----------------
GEMINI_SYSTEM = (
    "You are a precise Automobile Spare Parts Database Engine for Indian vehicles "
    "(Hyundai, Kia, Maruti Suzuki, Tata, Mahindra). Given an OEM part number or barcode, identify it. "
    "IMPORTANT numbering knowledge: Hyundai/Kia OEM part numbers are usually 10 alphanumeric chars "
    "(e.g. 954A0-CCAF0). The first 2-3 digits indicate the system group: 91/95/96 = electrical/body "
    "(95xxx often = BCM / IBU / smart junction, 96xxx = infotainment/audio, 93xxx = switches). "
    "STRICT RULES: "
    "1. NEVER invent or guess a part number. "
    "2. You ARE given REAL web search results with each request — treat them as the PRIMARY source "
    "of truth, cross-check them, and base your answer on them. If the web results (and your knowledge) "
    "still cannot identify the part, set status='NOT_FOUND' and confidence=0. Put the source URLs/titles "
    "you relied on into the sources array. "
    "3. The part number alone rarely pins the EXACT trim/variant/fuel. If you are not highly certain of "
    "a single exact model, you MUST list ALL plausible platform-sharing models (across Hyundai AND Kia) "
    "in compatible_models and compatible_vehicles, set confidence<=60. Do NOT claim one model as certain. "
    "4. cross_reference = other OEM numbers (old/new/superseded or sibling-brand equivalents) if known. "
    "Return ONLY strict minified JSON, no markdown, no commentary, with exactly these keys: "
    '{"name":string,"category":string,"company":string,"manufacturer":string,"car_model":string,'
    '"variant":string,"model_years":string,"year":string,"compatible_vehicles":[string],'
    '"compatible_models":[{"company":string,"car_name":string,"variant":string,"model_years":string}],'
    '"cross_reference":[string],"technical_info":string,"confidence":number(0-100),'
    '"conflict":boolean,"sources":[string],"notes":string,"status":"SUCCESS"|"NOT_FOUND"}'
)


def tavily_search(query: str) -> dict:
    headers = {"Content-Type": "application/json"}
    if TAVILY_API_KEY:
        headers["Authorization"] = f"Bearer {TAVILY_API_KEY}"
    else:
        headers["X-Tavily-Access-Mode"] = "keyless"
    body = {"query": query, "search_depth": "advanced", "max_results": 6, "include_answer": True}
    r = requests.post("https://api.tavily.com/search", json=body, headers=headers, timeout=45)
    r.raise_for_status()
    return r.json()


async def run_gemini(part_number: str, company: str) -> dict:
    # STEP 1 — Real web search (Tavily) for card-free Google-style grounding.
    web_context = ""
    sources: List[str] = []
    web_answer = ""
    try:
        query = (f'"{part_number}" Hyundai Kia OEM part number — which exact car model and variant '
                 f'(Creta Seltos Verna Alcazar Carens Venue etc), smart key or body control module')
        tv = await run_in_threadpool(tavily_search, query)
        web_answer = tv.get("answer") or ""
        for res in (tv.get("results") or [])[:6]:
            title = res.get("title", "")
            content = (res.get("content") or "")[:450]
            url = res.get("url", "")
            web_context += f"- {title}: {content} (URL: {url})\n"
            if url:
                sources.append(title or url)
    except Exception as e:
        logger.warning(f"Tavily search failed: {e}")

    prompt = (
        f"OEM auto electrical part number: {part_number}. Company gate hint: {company}.\n\n"
        f"REAL WEB SEARCH RESULTS (treat as the PRIMARY evidence — cross-check and rely on these):\n"
        f"{web_context or '(no web results found)'}\n"
        f"Web summary: {web_answer}\n\n"
        f"Using MAINLY the web results above, identify the part. If the web results clearly name the "
        f"vehicle(s), list ALL of them in compatible_vehicles. Return the strict JSON only (no markdown)."
    )

    # STEP 2 — LLM extraction/formatting via Emergent key (free, reliable). Tavily gives the accuracy.
    if not EMERGENT_LLM_KEY:
        raise Exception("No AI provider available")
    from emergentintegrations.llm.chat import LlmChat, UserMessage
    chat = LlmChat(api_key=EMERGENT_LLM_KEY, session_id=f"ai-{new_id()}",
                   system_message=GEMINI_SYSTEM).with_model("gemini", "gemini-3-flash-preview")
    text = await chat.send_message(UserMessage(text=prompt))
    return {"text": text, "sources": sources, "grounded": bool(sources)}


def parse_json_block(text: str) -> dict:
    text = text.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.lstrip().lower().startswith("json"):
            text = text.lstrip()[4:]
    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1:
        raise ValueError("No JSON in AI response")
    return json.loads(text[start:end + 1])


@api.post("/ai/research")
async def ai_research(body: AiResearchIn, user=Depends(get_current_user)):
    pn = body.part_number.strip()
    if not pn:
        raise HTTPException(400, "Part number required")

    # STEP 1 — DB-FIRST: if this part is already Verified in YOUR library, that is the
    # authoritative 100% answer. Return it instantly without calling AI.
    verified = await db.parts.find_one(
        {"part_number": pn, "verification_status": "Verified"}, {"_id": 0})
    if verified:
        result = {
            "name": verified.get("name", ""),
            "category": verified.get("category", ""),
            "company": verified.get("company", "All"),
            "manufacturer": verified.get("company", ""),
            "car_model": (verified.get("compatible_vehicles") or [""])[0],
            "variant": verified.get("variant", ""),
            "model_years": verified.get("year", ""),
            "year": verified.get("year", ""),
            "compatible_vehicles": verified.get("compatible_vehicles", []),
            "compatible_models": [],
            "cross_reference": [x for x in [verified.get("old_number"), verified.get("new_number")] if x],
            "technical_info": verified.get("technical_info", ""),
            "confidence": 100,
            "conflict": False,
            "sources": verified.get("ai_sources") or ["Your Verified Library"],
            "notes": "Match found in your verified parts library (Admin approved).",
            "status": "SUCCESS",
        }
        doc = {
            "id": new_id(), "part_number": pn, "company": verified.get("company", "All"),
            "result": result, "confidence": 100, "conflict": False,
            "verification": "Verified", "sources": result["sources"],
            "approval_status": "Approved", "from_database": True,
            "created_at": now_iso(), "by": user["username"],
        }
        await db.ai_research.insert_one(dict(doc))
        doc.pop("_id", None)
        return doc

    # STEP 2 — Unknown part: AI enrichment (still Requires Verification until Admin approves).
    if not (GEMINI_API_KEY or EMERGENT_LLM_KEY):
        raise HTTPException(503, "AI key not configured")
    try:
        res = await run_gemini(pn, body.company or "All")
        data = parse_json_block(res["text"])
    except Exception as e:
        logger.error(f"AI research failed: {e}")
        raise HTTPException(502, f"AI research failed: {e}")
    grounded = bool(res.get("grounded"))
    # Prefer real Google-Search source URLs when grounding actually returned them.
    if res.get("sources"):
        data["sources"] = res["sources"]
    conflict = bool(data.get("conflict"))
    confidence = int(data.get("confidence", 0) or 0)
    # AI can NEVER self-mark Verified — the model may hallucinate.
    # Every AI suggestion stays "Requires Verification" until the Admin reviews & approves.
    verification = "Requires Verification"
    # Backward-compat: keep compatible_vehicles populated even if model returned compatible_models.
    if not data.get("compatible_vehicles") and data.get("compatible_models"):
        data["compatible_vehicles"] = [
            " ".join([m.get("company", ""), m.get("car_name", ""), m.get("variant", "")]).strip()
            for m in data.get("compatible_models", [])
        ]
    doc = {
        "id": new_id(), "part_number": pn, "company": body.company or "All",
        "result": data, "confidence": confidence, "conflict": conflict,
        "verification": verification, "sources": data.get("sources", []),
        "approval_status": "Pending", "from_database": False, "grounded": grounded,
        "created_at": now_iso(), "by": user["username"],
    }
    await db.ai_research.insert_one(dict(doc))
    doc.pop("_id", None)
    return doc


@api.get("/ai/research")
async def list_ai_research(status: Optional[str] = None, part_number: Optional[str] = None,
                           user=Depends(get_current_user)):
    query: Dict[str, Any] = {}
    if status:
        query["approval_status"] = status
    if part_number:
        query["part_number"] = part_number.strip()
    return await db.ai_research.find(query, {"_id": 0}).sort("created_at", -1).to_list(200)


@api.post("/ai/research/{research_id}/approve")
async def approve_ai(research_id: str, edits: Optional[PartEditIn] = None, user=Depends(require("ai_approve"))):
    doc = await db.ai_research.find_one({"id": research_id})
    if not doc:
        raise HTTPException(404, "Research not found")
    r = doc["result"]
    pn = doc["part_number"]
    # Admin-edited values take precedence over the raw AI suggestion.
    e = edits.dict(exclude_none=True) if edits else {}
    part_updates = {
        "name": e.get("name", r.get("name", "")),
        "category": e.get("category", r.get("category", "")),
        "company": e.get("company") or r.get("company") or doc.get("company", "All"),
        "compatible_vehicles": e.get("compatible_vehicles", r.get("compatible_vehicles", [])),
        "variant": e.get("variant", r.get("variant", "")),
        "year": e.get("year", r.get("year", "")),
        "technical_info": e.get("technical_info", r.get("technical_info", "")),
        "verification_status": "Verified", "source": "AI (Admin approved)",
        "ai_sources": doc.get("sources", []),
    }
    if e.get("old_number") is not None:
        part_updates["old_number"] = e["old_number"]
    if e.get("new_number") is not None:
        part_updates["new_number"] = e["new_number"]
    if e.get("sticker_color") is not None:
        part_updates["sticker_color"] = e["sticker_color"]
    existing = await db.parts.find_one({"part_number": pn})
    if existing:
        await db.parts.update_one({"part_number": pn}, {"$set": part_updates})
    else:
        newp = {"id": new_id(), "part_number": pn, "barcode": "", "old_number": "",
                "new_number": "", "sticker_color": "", "photos": [], "created_at": now_iso(),
                "created_by": user["username"], "purchase_limit": None, "limit_enabled": False, **part_updates}
        await db.parts.insert_one(newp)
    await db.ai_research.update_one({"id": research_id},
                                    {"$set": {"approval_status": "Approved", "approved_by": user["username"],
                                              "approved_at": now_iso(), "final_result": part_updates}})
    return {"ok": True, "part_number": pn}


@api.post("/ai/research/{research_id}/reject")
async def reject_ai(research_id: str, user=Depends(require("ai_approve"))):
    r = await db.ai_research.update_one({"id": research_id},
                                        {"$set": {"approval_status": "Rejected", "approved_by": user["username"],
                                                  "approved_at": now_iso()}})
    if r.matched_count == 0:
        raise HTTPException(404, "Research not found")
    return {"ok": True}


# ---------------- Search history / demand / stats ----------------
@api.get("/search-history")
async def search_history(user=Depends(get_current_user)):
    return await db.search_history.find({}, {"_id": 0}).sort("count", -1).to_list(200)


@api.get("/demand")
async def demand(user=Depends(get_current_user)):
    # high demand = searched a lot but low/no stock
    hist = await db.search_history.find({}, {"_id": 0}).sort("count", -1).to_list(200)
    out = []
    for h in hist:
        sc = await db.stock.count_documents({"part_number": h["part_number"], "sold": {"$ne": True}})
        if h["count"] >= 2 and sc == 0:
            out.append({**h, "stock_count": sc, "demand": "HIGH"})
    return out


@api.get("/stats")
async def stats(user=Depends(require("view_stats"))):
    total_parts = await db.parts.count_documents({})
    in_stock_units = await db.stock.count_documents({"sold": {"$ne": True}})
    sold_units = await db.stock.count_documents({"sold": True})
    pending_reqs = await db.requirements.count_documents({"status": "Pending"})
    pending_ai = await db.ai_research.count_documents({"approval_status": "Pending"})
    verified_parts = await db.parts.count_documents({"verification_status": "Verified"})
    unverified_parts = await db.parts.count_documents({"verification_status": "Unverified"})
    known = await db.known_parts.count_documents({})
    buys = await db.transactions.count_documents({"type": "buy"})
    sells = await db.transactions.count_documents({"type": "sell"})
    return {
        "total_parts": total_parts, "in_stock_units": in_stock_units, "sold_units": sold_units,
        "pending_requirements": pending_reqs, "pending_ai": pending_ai,
        "verified_parts": verified_parts, "unverified_parts": unverified_parts,
        "known_parts": known, "total_buys": buys, "total_sells": sells,
    }


# ---------------- Photo upload ----------------
@api.post("/upload")
async def upload(file: UploadFile = File(...), user=Depends(get_current_user)):
    data = await file.read()
    ext = (file.filename or "img.jpg").split(".")[-1].lower()
    path = f"{APP_NAME}/uploads/{user['id']}/{new_id()}.{ext}"
    try:
        result = await run_in_threadpool(put_object, path, data, file.content_type or "image/jpeg")
    except Exception as e:
        logger.error(f"upload failed: {e}")
        raise HTTPException(502, "Upload failed")
    await db.files.insert_one({"path": result["path"], "owner_id": user["id"], "created_at": now_iso()})
    return {"path": result["path"], "url": f"/api/files/{result['path']}"}


@api.get("/files/{path:path}")
async def files(path: str, token: Optional[str] = None, authorization: Optional[str] = Header(None)):
    # accept token via query (web img) or header (native)
    tok = token
    if not tok and authorization and authorization.startswith("Bearer "):
        tok = authorization.split(" ", 1)[1]
    if not tok:
        raise HTTPException(401, "Missing token")
    try:
        jwt.decode(tok, JWT_SECRET, algorithms=["HS256"], issuer=JWT_ISSUER)
    except Exception:
        raise HTTPException(401, "Invalid token")
    rec = await db.files.find_one({"path": path})
    if not rec:
        raise HTTPException(404, "Not found")
    try:
        content, ctype = await run_in_threadpool(get_object, path)
    except Exception:
        raise HTTPException(404, "Not found")
    return Response(content=content, media_type=ctype)


# ---------------- Transactions history + bulk delete (Admin) ----------------
@api.get("/transactions")
async def list_transactions(type: Optional[str] = None, user=Depends(require_admin)):
    q: Dict[str, Any] = {"type": {"$in": ["buy", "sell"]}}
    if type in ("buy", "sell"):
        q["type"] = type
    txns = await db.transactions.find(q, {"_id": 0}).sort("at", -1).to_list(2000)
    for t in txns:
        p = await db.parts.find_one({"part_number": t.get("part_number")}, {"_id": 0, "name": 1})
        t["part_name"] = (p or {}).get("name", "")
    return txns


class TxnDeleteIn(BaseModel):
    ids: List[str]
    remove_stock: bool = True


@api.post("/transactions/delete")
async def delete_transactions(body: TxnDeleteIn, user=Depends(require_admin)):
    deleted_txn = 0
    removed_units = 0
    for tid in body.ids:
        t = await db.transactions.find_one({"id": tid})
        if not t:
            continue
        if body.remove_stock and t.get("unit_id"):
            r = await db.stock.delete_one({"id": t["unit_id"]})
            removed_units += r.deleted_count
        await db.transactions.delete_one({"id": tid})
        deleted_txn += 1
    return {"ok": True, "deleted": deleted_txn, "removed_units": removed_units}


# ---------------- Backup: export / import (Admin) ----------------
BACKUP_COLLECTIONS = ["parts", "stock", "transactions", "users", "settings",
                      "known_parts", "requirements", "verifications"]


@api.get("/backup/export")
async def backup_export(user=Depends(require_admin)):
    data: Dict[str, Any] = {"app": APP_NAME, "exported_at": now_iso(), "collections": {}}
    for col in BACKUP_COLLECTIONS:
        docs = await db[col].find({}, {"_id": 0}).to_list(100000)
        data["collections"][col] = docs
    return data


class BackupImportIn(BaseModel):
    collections: Dict[str, List[Dict[str, Any]]]


@api.post("/backup/import")
async def backup_import(body: BackupImportIn, user=Depends(require_admin)):
    summary = {}
    for col, docs in body.collections.items():
        if col not in BACKUP_COLLECTIONS:
            continue
        count = 0
        for d in docs:
            d.pop("_id", None)
            key = d.get("id") or d.get("part_number") or d.get("username") or d.get("key")
            if key is None:
                await db[col].insert_one(dict(d))
            else:
                if d.get("id"):
                    flt = {"id": d["id"]}
                elif d.get("part_number"):
                    flt = {"part_number": d["part_number"]}
                elif d.get("username"):
                    flt = {"username": d["username"]}
                else:
                    flt = {"key": d["key"]}
                await db[col].update_one(flt, {"$set": d}, upsert=True)
            count += 1
        summary[col] = count
    return {"ok": True, "imported": summary}


@api.get("/backup/excel")
async def backup_excel(user=Depends(require_admin)):
    from openpyxl import Workbook
    import io

    wb = Workbook()
    wb.remove(wb.active)
    sheets = {
        "Parts": ("parts", ["part_number", "name", "company", "category", "variant", "verification_status"]),
        "Stock": ("stock", ["part_number", "condition", "sold", "added_by", "created_at"]),
        "Transactions": ("transactions", ["type", "part_number", "price", "by", "at"]),
        "Users": ("users", ["name", "username", "role", "disabled"]),
    }
    for sheet_name, (col, cols) in sheets.items():
        ws = wb.create_sheet(sheet_name)
        ws.append(cols)
        docs = await db[col].find({}, {"_id": 0}).to_list(100000)
        for d in docs:
            ws.append([str(d.get(c, "")) for c in cols])
    buf = io.BytesIO()
    wb.save(buf)
    buf.seek(0)
    return Response(
        content=buf.read(),
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": "attachment; filename=kabadi_backup.xlsx"},
    )


app.include_router(api)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
