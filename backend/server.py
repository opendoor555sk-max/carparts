import os
import uuid
import json
import logging
from pathlib import Path
from datetime import datetime, timezone, timedelta
from typing import List, Optional, Dict, Any

import jwt
import bcrypt
import requests
from cryptography.fernet import Fernet
from fastapi import FastAPI, APIRouter, HTTPException, Depends, UploadFile, File, Header
from fastapi.responses import Response
from fastapi.concurrency import run_in_threadpool
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
from pydantic import BaseModel

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
app = FastAPI(title="Auto Parts Store API")
api = APIRouter(prefix="/api")


@app.get("/health")
def health():
    return {"status": "ok"}


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

COMPANIES = ["All", "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Kia", "Toyota", "Honda",
             "Nissan", "Renault", "Ford", "Volkswagen", "Skoda", "MG", "Datsun", "Chevrolet",
             "Fiat", "Jeep", "Citroen", "Isuzu"]

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
    {"group": "Engine & Components", "items": [
        "Cylinder Head", "Engine Block", "Piston", "Piston Ring", "Crankshaft", "Camshaft",
        "Connecting Rod", "Engine Valve", "Timing Chain", "Timing Belt Kit", "Oil Pump",
        "Water Pump", "Head Gasket", "Engine Mounting", "Flywheel", "Turbocharger",
        "Intercooler", "Intake Manifold", "Exhaust Manifold",
    ]},
    {"group": "Fuel System", "items": [
        "Fuel Injector", "Fuel Pump", "Fuel Filter", "Fuel Tank", "Carburettor", "Throttle Body",
        "Fuel Rail", "Fuel Hose", "Diesel Nozzle",
    ]},
    {"group": "Cooling System", "items": [
        "Radiator", "Radiator Fan", "Coolant Hose", "Thermostat", "Coolant Reservoir",
        "Radiator Cap", "Heater Core",
    ]},
    {"group": "Transmission & Clutch", "items": [
        "Clutch Plate", "Pressure Plate", "Clutch Release Bearing", "Clutch Master Cylinder",
        "Clutch Slave Cylinder", "Gearbox Assembly", "Gear Set", "CV Joint", "Drive Shaft / Axle",
        "Propeller Shaft", "Differential", "Transmission Mounting",
    ]},
    {"group": "Braking System", "items": [
        "Brake Pad", "Brake Shoe", "Brake Disc / Rotor", "Brake Drum", "Brake Caliper",
        "Brake Master Cylinder", "Wheel Cylinder", "Brake Booster", "Brake Hose",
        "Brake Fluid Reservoir", "Handbrake Cable", "ABS Modulator",
    ]},
    {"group": "Suspension & Steering", "items": [
        "Shock Absorber", "Strut Assembly", "Coil Spring", "Leaf Spring", "Control Arm",
        "Ball Joint", "Tie Rod End", "Steering Rack", "Steering Column", "Power Steering Pump",
        "Stabilizer Link", "Bush Kit", "Wheel Bearing", "Wheel Hub",
    ]},
    {"group": "Body & Exterior", "items": [
        "Bonnet / Hood", "Front Bumper", "Rear Bumper", "Fender / Mudguard", "Door Shell",
        "Door Handle", "Boot / Tailgate", "Grille", "Side Mirror", "Windshield / Windscreen",
        "Window Glass", "Roof Panel", "Body Panel", "Emblem / Logo", "Wheel Arch", "Running Board",
    ]},
    {"group": "Lighting", "items": [
        "Headlight Assembly", "Tail Light", "Fog Lamp", "Indicator / Turn Signal",
        "Number Plate Light", "Reverse Light", "DRL", "Cabin Light", "Bulb / LED",
        "Headlight Ballast",
    ]},
    {"group": "Ignition & Electrical (Mech)", "items": [
        "Spark Plug", "Glow Plug", "Distributor", "HT Cable", "Starter Solenoid",
        "Voltage Regulator", "Battery Terminal",
    ]},
    {"group": "Filters & Fluids", "items": [
        "Air Filter", "Oil Filter", "Cabin / AC Filter", "Engine Oil", "Coolant", "Brake Fluid",
        "Power Steering Fluid", "Transmission Fluid", "Grease",
    ]},
    {"group": "AC & Heating", "items": [
        "AC Compressor", "Condenser", "Evaporator", "Expansion Valve", "AC Hose",
        "Blower Motor", "Cooling Coil", "Receiver Drier", "Cabin Blower",
    ]},
    {"group": "Exhaust System", "items": [
        "Silencer / Muffler", "Exhaust Pipe", "Catalytic Converter", "Exhaust Manifold",
        "DPF", "Resonator", "Exhaust Gasket",
    ]},
    {"group": "Interior & Trim", "items": [
        "Seat", "Seat Cover", "Dashboard", "Door Trim / Panel", "Steering Wheel", "Gear Knob",
        "Floor Mat", "Sun Visor", "Armrest", "Handbrake Lever", "Pedal Assembly",
    ]},
    {"group": "Wheels & Tyres", "items": [
        "Alloy Wheel", "Steel Rim", "Tyre", "Tube", "Wheel Cap / Cover", "Wheel Nut / Bolt",
        "TPMS Valve", "Spare Wheel Carrier",
    ]},
    {"group": "Belts, Hoses & Bearings", "items": [
        "Timing Belt", "Fan Belt / V-Belt", "Serpentine Belt", "Tensioner", "Idler Pulley",
        "Radiator Hose", "Vacuum Hose", "Wheel Bearing", "Release Bearing",
    ]},
    {"group": "Gaskets & Seals", "items": [
        "Head Gasket", "Valve Cover Gasket", "Oil Seal", "Crank Seal", "Manifold Gasket",
        "O-Ring Kit", "Sump Gasket",
    ]},
    {"group": "Wipers & Washer", "items": [
        "Wiper Blade", "Wiper Motor", "Wiper Linkage", "Washer Pump", "Washer Tank",
        "Washer Nozzle",
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


# Reversible encryption for admin-viewable passwords (store owner tool).
# The bcrypt hash is used for login; this encrypted copy exists ONLY so a store
# owner can reveal a staff password on demand — scoped to their own store.
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
        "store_id": user.get("store_id"),
        "iat": now, "exp": now + timedelta(minutes=ACCESS_MINUTES), "iss": JWT_ISSUER,
    }
    return jwt.encode(claims, JWT_SECRET, algorithm="HS256")


def public_user(u: dict) -> dict:
    perms = ALL_PERMISSIONS if u["role"] in ("admin", "super_admin") else u.get("permissions", [])
    return {"id": u["id"], "name": u["name"], "username": u["username"], "role": u["role"],
            "store_id": u.get("store_id"), "store_name": u.get("store_name", ""),
            "store_gst": u.get("store_gst", ""), "store_phone": u.get("store_phone", ""),
            "store_address": u.get("store_address", ""), "store_logo": u.get("store_logo", ""),
            "store_bank": u.get("store_bank", ""),
            "permissions": perms, "disabled": u.get("disabled", False),
            "has_google_key": bool(u.get("google_api_key") and u.get("google_cx"))}


async def _attach_store(user: dict) -> None:
    if user.get("store_id"):
        store = await db.stores.find_one({"id": user["store_id"]}, {"_id": 0})
        if store:
            user["store_name"] = store.get("name", "")
            user["store_gst"] = store.get("gst", "")
            user["store_phone"] = store.get("phone", "")
            user["store_address"] = store.get("address", "")
            user["store_logo"] = store.get("logo_path", "")
            user["store_bank"] = store.get("bank", "")


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
    await _attach_store(user)
    return user


def require(permission: str):
    async def dep(user=Depends(get_current_user)):
        role = user.get("role")
        perms = ALL_PERMISSIONS if role in ("admin", "super_admin") else user.get("permissions", [])
        if permission not in perms:
            raise HTTPException(403, f"Permission denied: {permission}")
        return user
    return dep


async def require_admin(user=Depends(get_current_user)):
    if user.get("role") not in ("admin", "super_admin"):
        raise HTTPException(403, "ફક્ત Admin આ કરી શકે")
    return user


async def require_super_admin(user=Depends(get_current_user)):
    if user.get("role") != "super_admin":
        raise HTTPException(403, "ફક્ત Super Admin આ કરી શકે")
    return user


# ---------------- Multi-tenant store scoping ----------------
def resolve_store(user: dict, store_id_param: Optional[str] = None, require_write: bool = False) -> Optional[str]:
    """Return the store_id a request should operate on.

    Normal users (admin/staff) are ALWAYS locked to their own store — the client
    cannot override it. Super-admin may target any store via store_id_param, or
    (for reads) see all stores when no param is given.
    """
    if user.get("role") == "super_admin":
        if require_write and not store_id_param:
            raise HTTPException(400, "Super Admin: પહેલા store પસંદ કરો (store_id)")
        return store_id_param
    return user.get("store_id")


def sq(user: dict, extra: Optional[dict] = None, store_id_param: Optional[str] = None) -> dict:
    q = dict(extra or {})
    sid = resolve_store(user, store_id_param)
    if sid is not None:
        q["store_id"] = sid
    return q


# ---------------- Models ----------------
class LoginIn(BaseModel):
    username: str
    password: str


class RegisterIn(BaseModel):
    store_name: str
    name: str
    username: str
    password: str
    contact: str


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
    gps: Optional[str] = ""


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
    await db.users.create_index("username", unique=True)
    await db.users.create_index("id", unique=True)
    await db.stores.create_index("id", unique=True)
    # parts are now unique per store (was globally unique before multi-tenancy)
    try:
        await db.parts.drop_index("part_number_1")
    except Exception:
        pass
    try:
        await db.parts.create_index([("store_id", 1), ("part_number", 1)], unique=True, sparse=True)
    except Exception as e:
        logger.warning(f"parts index create warning: {e}")

    # Super Admin (app developer / god-view). Existing 'abdul' is upgraded to super_admin.
    sa_username = os.environ.get("ADMIN_USERNAME", "abdul").lower()
    existing = await db.users.find_one({"username": sa_username})
    if not existing:
        await db.users.insert_one({
            "id": new_id(),
            "name": os.environ.get("ADMIN_NAME", "Abdul Salam"),
            "username": sa_username,
            "password_hash": hash_pw(os.environ.get("ADMIN_PASSWORD", "Salam@123")),
            "password_enc": encrypt_pw(os.environ.get("ADMIN_PASSWORD", "Salam@123")),
            "role": "super_admin", "store_id": None, "permissions": ALL_PERMISSIONS,
            "disabled": False, "created_at": now_iso(),
        })
        logger.info("Seeded super admin user")
    else:
        upd = {"role": "super_admin"}
        if not existing.get("password_enc"):
            upd["password_enc"] = encrypt_pw(os.environ.get("ADMIN_PASSWORD", "Salam@123"))
        await db.users.update_one({"id": existing["id"]}, {"$set": upd})

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
    return {"app": "Auto Parts Store", "status": "ok"}


@api.post("/auth/register")
async def register(body: RegisterIn):
    username = body.username.lower().strip()
    if not username or not body.password or not body.store_name.strip():
        raise HTTPException(422, "Store name, username and password are required")
    if not body.contact.strip():
        raise HTTPException(422, "Contact number is required")
    if len(body.password) < 6:
        raise HTTPException(422, "Password must be at least 6 characters")
    if await db.users.find_one({"username": username}):
        raise HTTPException(400, "આ username પહેલેથી વપરાયેલ છે — બીજું પસંદ કરો")
    store_id = new_id()
    store = {"id": store_id, "name": body.store_name.strip(), "owner_username": username,
             "contact": body.contact.strip(), "created_at": now_iso()}
    await db.stores.insert_one(dict(store))
    user = {
        "id": new_id(), "name": body.name.strip() or body.store_name.strip(), "username": username,
        "password_hash": hash_pw(body.password), "password_enc": encrypt_pw(body.password),
        "role": "admin", "store_id": store_id, "permissions": ALL_PERMISSIONS,
        "contact": body.contact.strip(), "disabled": False, "created_at": now_iso(),
    }
    await db.users.insert_one(dict(user))
    # per-store purchase limit settings
    await db.settings.insert_one({"key": "purchase_limit", "store_id": store_id,
                                  "global_enabled": False, "global_default": None})
    user["store_name"] = store["name"]
    return {"access_token": make_token(user), "token_type": "bearer", "user": public_user(user)}


@api.post("/auth/login")
async def login(body: LoginIn):
    user = await db.users.find_one({"username": body.username.lower().strip()})
    if not user or not verify_pw(body.password, user["password_hash"]):
        raise HTTPException(401, "ખોટું username અથવા password")
    if user.get("disabled"):
        raise HTTPException(403, "User disabled")
    await _attach_store(user)
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


class StoreProfileIn(BaseModel):
    name: Optional[str] = None
    gst: Optional[str] = None
    phone: Optional[str] = None
    address: Optional[str] = None
    bank: Optional[str] = None
    logo_path: Optional[str] = None


@api.get("/store/profile")
async def get_store_profile(user=Depends(require_admin)):
    sid = resolve_store(user, None, require_write=True)
    s = await db.stores.find_one({"id": sid}, {"_id": 0})
    return s or {}


@api.post("/store/profile")
async def set_store_profile(body: StoreProfileIn, user=Depends(require_admin)):
    sid = resolve_store(user, None, require_write=True)
    upd = {k: v for k, v in body.dict().items() if v is not None}
    if upd:
        await db.stores.update_one({"id": sid}, {"$set": upd})
    return await db.stores.find_one({"id": sid}, {"_id": 0})


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

    # CACHE — if this part is already verified in THIS store's DB, reuse it.
    verified = await db.parts.find_one(sq(user, {"part_number": pn, "verification_status": "Verified"}), {"_id": 0})
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
    sid = resolve_store(user)
    await db.search_history.update_one({"part_number": pn, "store_id": sid},
                                       {"$inc": {"count": 1}, "$set": {"last_searched": now_iso()}},
                                       upsert=True)
    return {
        "cached": False, "company": company, "brands": brands, "models": models,
        "variants": variants, "name": "", "sources": sources, "result_count": len(items),
    }


# ---------------- Super Admin: stores ----------------
@api.get("/admin/stores")
async def list_stores(user=Depends(require_super_admin)):
    stores = await db.stores.find({}, {"_id": 0}).sort("created_at", -1).to_list(1000)
    out = []
    for s in stores:
        sid = s["id"]
        out.append({
            **s,
            "users": await db.users.count_documents({"store_id": sid, "deleted_at": {"$exists": False}}),
            "parts": await db.parts.count_documents({"store_id": sid}),
            "in_stock": await db.stock.count_documents({"store_id": sid, "sold": {"$ne": True}}),
            "owner": (await db.users.find_one({"store_id": sid, "role": "admin"}, {"_id": 0, "name": 1, "username": 1})) or {},
        })
    return out


# ---------------- Admin: users ----------------
@api.get("/admin/users")
async def list_users(store_id: Optional[str] = None, user=Depends(require("manage_users"))):
    # SEC-004: never leak password_enc / google keys
    proj = {"_id": 0, "password_hash": 0, "password_enc": 0, "google_api_key": 0, "google_cx": 0}
    users = await db.users.find(sq(user, {"deleted_at": {"$exists": False}}, store_id), proj).to_list(500)
    return [{**u, "permissions": ALL_PERMISSIONS if u["role"] in ("admin", "super_admin") else u.get("permissions", [])}
            for u in users]


@api.delete("/admin/users/{user_id}")
async def remove_user(user_id: str, user=Depends(require("manage_users"))):
    target = await db.users.find_one({"id": user_id})
    if not target:
        raise HTTPException(404, "User not found")
    # store isolation
    if user.get("role") != "super_admin" and target.get("store_id") != user.get("store_id"):
        raise HTTPException(403, "બીજા store નો user remove ન કરાય")
    if target["role"] in ("admin", "super_admin"):
        raise HTTPException(400, "Store owner / Admin ને remove ન કરાય")
    await db.users.update_one({"id": user_id}, {"$set": {"deleted_at": now_iso(), "disabled": True}})
    return {"ok": True}


@api.post("/admin/users")
async def create_user(body: UserCreate, store_id: Optional[str] = None, user=Depends(require("manage_users"))):
    if await db.users.find_one({"username": body.username.lower().strip()}):
        raise HTTPException(400, "Username already exists")
    sid = resolve_store(user, store_id, require_write=True)
    # SEC-001: only super_admin may create admin-role users; store staff can only make staff.
    role = "staff"
    if body.role == "admin" and user.get("role") == "super_admin":
        role = "admin"
    doc = {
        "id": new_id(), "name": body.name, "username": body.username.lower().strip(),
        "password_hash": hash_pw(body.password),
        "password_enc": encrypt_pw(body.password),
        "role": role, "store_id": sid,
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
    # store isolation
    if user.get("role") != "super_admin" and target.get("store_id") != user.get("store_id"):
        raise HTTPException(403, "બીજા store નો user edit ન કરાય")
    # SEC-001: a non-super-admin cannot modify an admin/super_admin account other than self.
    if target["role"] in ("admin", "super_admin") and user.get("role") != "super_admin" and target["id"] != user["id"]:
        raise HTTPException(403, "Admin account બીજા staff દ્વારા બદલી ન શકાય")
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
    if user.get("role") != "super_admin" and target.get("store_id") != user.get("store_id"):
        raise HTTPException(403, "બીજા store નો password ન જોઈ શકાય")
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
async def compute_limit(store_id: Optional[str], part_number: str) -> dict:
    base = {"store_id": store_id} if store_id is not None else {}
    part = await db.parts.find_one({**base, "part_number": part_number}, {"_id": 0})
    existing_stock = await db.stock.count_documents({**base, "part_number": part_number, "sold": {"$ne": True}})
    settings = await db.settings.find_one({"key": "purchase_limit", "store_id": store_id})
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
async def part_status(store_id: Optional[str], part_number: str) -> dict:
    base = {"store_id": store_id} if store_id is not None else {}
    part = await db.parts.find_one({**base, "part_number": part_number}, {"_id": 0})
    stock_count = await db.stock.count_documents({**base, "part_number": part_number, "sold": {"$ne": True}})
    known = await db.known_parts.find_one({**base, "part_number": part_number}, {"_id": 0})
    requirement = await db.requirements.find_one(
        {**base, "part_number": part_number, "status": {"$in": ["Pending", "Purchased"]}}, {"_id": 0})
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
async def search(q: str, store_id: Optional[str] = None, user=Depends(require("search"))):
    pn = q.strip()
    if not pn:
        raise HTTPException(400, "Empty query")
    sid = resolve_store(user, store_id)
    result = await part_status(sid, pn)
    await db.search_history.update_one(
        {"part_number": pn, "store_id": sid},
        {"$inc": {"count": 1}, "$set": {"last_searched": now_iso(), "last_status": result["status"]},
         "$setOnInsert": {"first_searched": now_iso()}},
        upsert=True,
    )
    limit = await compute_limit(sid, pn)
    return {**result, "limit": limit}


# ---------------- Parts ----------------
@api.get("/parts")
async def list_parts(company: Optional[str] = None, category: Optional[str] = None,
                     q: Optional[str] = None, store_id: Optional[str] = None,
                     user=Depends(get_current_user)):
    query: Dict[str, Any] = sq(user, None, store_id)
    if company and company != "All":
        query["company"] = company
    if category:
        query["category"] = category
    if q:
        query["part_number"] = {"$regex": q.strip()[:64], "$options": "i"}
    parts = await db.parts.find(query, {"_id": 0}).sort("created_at", -1).to_list(500)
    for p in parts:
        p["stock_count"] = await db.stock.count_documents(
            {"store_id": p.get("store_id"), "part_number": p["part_number"], "sold": {"$ne": True}})
    return parts


@api.post("/parts")
async def create_part(body: PartCreate, store_id: Optional[str] = None, user=Depends(require("manage_parts"))):
    pn = body.part_number.strip()
    if not pn:
        raise HTTPException(400, "Part number required")
    sid = resolve_store(user, store_id, require_write=True)
    if await db.parts.find_one({"store_id": sid, "part_number": pn}):
        raise HTTPException(400, "Part master already exists (no duplicate)")
    doc = body.dict()
    doc["part_number"] = pn
    doc.update({
        "id": new_id(), "store_id": sid, "verification_status": "Unverified", "created_at": now_iso(),
        "created_by": user["username"], "purchase_limit": None, "limit_enabled": False,
    })
    await db.parts.insert_one(doc)
    doc.pop("_id", None)
    return doc


@api.get("/parts/{part_number}")
async def get_part(part_number: str, store_id: Optional[str] = None, user=Depends(get_current_user)):
    sid = resolve_store(user, store_id)
    part = await db.parts.find_one(sq(user, {"part_number": part_number}, store_id), {"_id": 0})
    if not part:
        raise HTTPException(404, "Part not found")
    units = await db.stock.find({"store_id": part.get("store_id"), "part_number": part_number, "sold": {"$ne": True}},
                                {"_id": 0}).to_list(200)
    part["units"] = units
    part["stock_count"] = len(units)
    part["limit"] = await compute_limit(part.get("store_id"), part_number)
    return part


@api.patch("/parts/{part_number}")
async def update_part(part_number: str, body: PartUpdate, store_id: Optional[str] = None,
                      user=Depends(require("manage_parts"))):
    updates = {k: v for k, v in body.dict().items() if v is not None}
    if not updates:
        return await get_part(part_number, store_id, user)
    r = await db.parts.update_one(sq(user, {"part_number": part_number}, store_id), {"$set": updates})
    if r.matched_count == 0:
        raise HTTPException(404, "Part not found")
    return await db.parts.find_one(sq(user, {"part_number": part_number}, store_id), {"_id": 0})


# ---------------- Buy (increases stock) ----------------
@api.post("/buy")
async def buy(body: BuyIn, store_id: Optional[str] = None, user=Depends(require("buy"))):
    pn = body.part_number.strip()
    if not pn:
        raise HTTPException(400, "Part number required")
    sid = resolve_store(user, store_id, require_write=True)
    part = await db.parts.find_one({"store_id": sid, "part_number": pn}, {"_id": 0})
    if not part:
        part = {
            "id": new_id(), "store_id": sid, "part_number": pn, "company": body.company or "All",
            "name": body.name or "", "category": body.category or "",
            "compatible_vehicles": body.compatible_vehicles or [], "variant": body.variant or "",
            "year": "", "old_number": "", "new_number": "", "barcode": body.barcode or "",
            "sticker_color": "", "technical_info": "", "photos": [], "source": "Buy",
            "verification_status": "Unverified", "created_at": now_iso(),
            "created_by": user["username"], "purchase_limit": None, "limit_enabled": False,
        }
        await db.parts.insert_one(dict(part))
    else:
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
            await db.parts.update_one({"store_id": sid, "part_number": pn}, {"$set": fill})
    limit = await compute_limit(sid, pn)
    if limit["limit_enabled"] and limit["remaining"] is not None and limit["remaining"] <= 0 and not body.override:
        raise HTTPException(409, detail={"code": "LIMIT_REACHED", "message": "DO NOT BUY — purchase limit reached",
                                          "limit": limit})
    unit = {
        "id": new_id(), "store_id": sid, "part_number": pn, "condition": body.condition,
        "location": body.location or {}, "photos": body.photos or [],
        "barcode": body.barcode or "", "sold": False, "created_at": now_iso(),
        "added_by": user["username"], "overridden": bool(body.override and limit.get("status") == "STOP"),
    }
    await db.stock.insert_one(dict(unit))
    txn = {"id": new_id(), "store_id": sid, "type": "buy", "part_number": pn, "unit_id": unit["id"],
           "price": body.price, "location": body.location or {}, "by": user["username"], "at": now_iso()}
    await db.transactions.insert_one(dict(txn))
    unit.pop("_id", None)
    new_limit = await compute_limit(sid, pn)
    return {"ok": True, "unit": unit, "limit": new_limit}


# ---------------- Sell (decreases stock) ----------------
@api.post("/sell")
async def sell(body: SellIn, store_id: Optional[str] = None, user=Depends(require("sell"))):
    pn = body.part_number.strip()
    sid = resolve_store(user, store_id, require_write=True)
    query = {"store_id": sid, "part_number": pn, "sold": {"$ne": True}}
    if body.unit_id:
        query["id"] = body.unit_id
    unit = await db.stock.find_one(query)
    if not unit:
        raise HTTPException(409, detail={"code": "NO_STOCK", "message": "કોઈ stock available નથી — sell ન થાય"})
    await db.stock.update_one({"id": unit["id"]}, {"$set": {"sold": True, "sold_at": now_iso(), "sold_by": user["username"]}})
    txn = {"id": new_id(), "store_id": sid, "type": "sell", "part_number": pn, "unit_id": unit["id"],
           "price": body.price, "buyer": body.buyer or "", "by": user["username"], "at": now_iso()}
    await db.transactions.insert_one(dict(txn))
    remaining = await db.stock.count_documents({"store_id": sid, "part_number": pn, "sold": {"$ne": True}})
    return {"ok": True, "remaining_stock": remaining}


# ---------------- Inventory ----------------
@api.get("/inventory")
async def inventory(condition: Optional[str] = None, q: Optional[str] = None, store_id: Optional[str] = None,
                    company: Optional[str] = None, category: Optional[str] = None,
                    date_from: Optional[str] = None, date_to: Optional[str] = None,
                    user=Depends(get_current_user)):
    query: Dict[str, Any] = sq(user, {"sold": {"$ne": True}}, store_id)
    if condition:
        query["condition"] = condition
    if q:
        query["part_number"] = {"$regex": q.strip()[:64], "$options": "i"}
    if date_from or date_to:
        rng: Dict[str, Any] = {}
        if date_from:
            rng["$gte"] = date_from
        if date_to:
            rng["$lte"] = date_to + "T23:59:59"
        query["created_at"] = rng
    units = await db.stock.find(query, {"_id": 0}).sort("created_at", -1).to_list(2000)
    out = []
    for u in units:
        p = await db.parts.find_one({"store_id": u.get("store_id"), "part_number": u["part_number"]},
                                    {"_id": 0, "name": 1, "company": 1, "category": 1})
        u["part_name"] = p.get("name", "") if p else ""
        u["company"] = (p.get("company", "") if p else "") or "All"
        u["category"] = (p.get("category", "") if p else "") or "Uncategorized"
        if company and company != "All" and u["company"] != company:
            continue
        if category and u["category"] != category:
            continue
        out.append(u)
    return out


# ---------------- Stock adjust / delete (Admin only) ----------------
class StockAdjustIn(BaseModel):
    part_number: str
    delta: int = 0
    condition: Optional[str] = None
    location: Optional[Dict[str, Any]] = None


@api.post("/stock/adjust")
async def stock_adjust(body: StockAdjustIn, store_id: Optional[str] = None, user=Depends(require_admin)):
    pn = body.part_number.strip()
    sid = resolve_store(user, store_id, require_write=True)
    part = await db.parts.find_one({"store_id": sid, "part_number": pn})
    if not part:
        raise HTTPException(404, "Part મળ્યો નથી")
    delta = int(body.delta)
    added, removed = 0, 0
    if delta > 0:
        for _ in range(delta):
            unit = {"id": new_id(), "store_id": sid, "part_number": pn, "condition": body.condition or "Unknown",
                    "location": body.location or {}, "photos": [], "barcode": "",
                    "sold": False, "created_at": now_iso(), "added_by": user["username"], "adjusted": True}
            await db.stock.insert_one(dict(unit))
            added += 1
        await db.transactions.insert_one({"id": new_id(), "store_id": sid, "type": "adjust_add", "part_number": pn,
                                          "quantity": added, "by": user["username"], "at": now_iso()})
    elif delta < 0:
        units = await db.stock.find({"store_id": sid, "part_number": pn, "sold": {"$ne": True}}).sort("created_at", -1).to_list(-delta)
        for u in units:
            await db.stock.update_one({"id": u["id"]}, {"$set": {"sold": True, "sold_at": now_iso(),
                                                                 "sold_by": user["username"], "removed_reason": "adjust"}})
            removed += 1
        await db.transactions.insert_one({"id": new_id(), "store_id": sid, "type": "adjust_remove", "part_number": pn,
                                          "quantity": removed, "by": user["username"], "at": now_iso()})
    remaining = await db.stock.count_documents({"store_id": sid, "part_number": pn, "sold": {"$ne": True}})
    return {"ok": True, "added": added, "removed": removed, "remaining_stock": remaining}


@api.delete("/stock/unit/{unit_id}")
async def delete_unit(unit_id: str, user=Depends(require_admin)):
    unit = await db.stock.find_one({"id": unit_id})
    if not unit:
        raise HTTPException(404, "Unit મળ્યું નથી")
    if user.get("role") != "super_admin" and unit.get("store_id") != user.get("store_id"):
        raise HTTPException(403, "બીજા store નું unit delete ન કરાય")
    await db.stock.delete_one({"id": unit_id})
    await db.transactions.insert_one({"id": new_id(), "store_id": unit.get("store_id"), "type": "delete_unit",
                                      "part_number": unit["part_number"], "unit_id": unit_id,
                                      "by": user["username"], "at": now_iso()})
    remaining = await db.stock.count_documents({"store_id": unit.get("store_id"),
                                                "part_number": unit["part_number"], "sold": {"$ne": True}})
    return {"ok": True, "remaining_stock": remaining}


# ---------------- Physical stock verification (Admin only) ----------------
async def _expected_stock(store_id: Optional[str]) -> Dict[str, int]:
    base = {"store_id": store_id} if store_id is not None else {}
    units = await db.stock.find({**base, "sold": {"$ne": True}}, {"_id": 0, "part_number": 1}).to_list(10000)
    counts: Dict[str, int] = {}
    for u in units:
        counts[u["part_number"]] = counts.get(u["part_number"], 0) + 1
    return counts


@api.get("/stock/verification")
async def verification_list(store_id: Optional[str] = None, user=Depends(require_admin)):
    sid = resolve_store(user, store_id)
    counts = await _expected_stock(sid)
    out = []
    for pn, qty in counts.items():
        p = await db.parts.find_one({"store_id": sid, "part_number": pn}, {"_id": 0, "name": 1, "company": 1})
        out.append({"part_number": pn, "expected": qty,
                    "part_name": (p or {}).get("name", ""), "company": (p or {}).get("company", "")})
    out.sort(key=lambda x: x["part_number"])
    last = await db.verifications.find_one(sq(user, None, store_id), {"_id": 0}, sort=[("at", -1)])
    return {"items": out, "last": last}


class VerifyCount(BaseModel):
    part_number: str
    counted: int


class VerifyIn(BaseModel):
    counts: List[VerifyCount]


@api.post("/stock/verify")
async def verify_stock(body: VerifyIn, store_id: Optional[str] = None, user=Depends(require_admin)):
    sid = resolve_store(user, store_id, require_write=True)
    expected = await _expected_stock(sid)
    counted_map = {c.part_number.strip(): int(c.counted) for c in body.counts}
    discrepancies = []
    for pn in set(expected) | set(counted_map):
        exp = expected.get(pn, 0)
        got = counted_map.get(pn, 0)
        if exp != got:
            p = await db.parts.find_one({"store_id": sid, "part_number": pn}, {"_id": 0, "name": 1})
            discrepancies.append({"part_number": pn, "part_name": (p or {}).get("name", ""),
                                  "expected": exp, "counted": got, "diff": got - exp,
                                  "status": "MISSING" if got < exp else "EXTRA"})
    total = len(set(expected) | set(counted_map))
    report = {"id": new_id(), "store_id": sid, "at": now_iso(), "by": user["username"], "total_parts": total,
              "ok_count": total - len(discrepancies), "discrepancies": discrepancies}
    await db.verifications.insert_one(dict(report))
    report.pop("_id", None)
    return report


# ---------------- Known parts ----------------
@api.post("/known-parts")
async def add_known(body: KnownPartIn, store_id: Optional[str] = None, user=Depends(require("manage_parts"))):
    pn = body.part_number.strip()
    sid = resolve_store(user, store_id, require_write=True)
    doc = body.dict()
    doc["part_number"] = pn
    doc.update({"id": new_id(), "store_id": sid, "created_at": now_iso(), "by": user["username"]})
    await db.known_parts.update_one({"store_id": sid, "part_number": pn}, {"$set": doc}, upsert=True)
    doc.pop("_id", None)
    return doc


@api.get("/known-parts")
async def list_known(store_id: Optional[str] = None, user=Depends(get_current_user)):
    return await db.known_parts.find(sq(user, None, store_id), {"_id": 0}).sort("created_at", -1).to_list(500)


# ---------------- Requirements ----------------
@api.post("/requirements")
async def add_requirement(body: RequirementIn, store_id: Optional[str] = None, user=Depends(require("requirement"))):
    sid = resolve_store(user, store_id, require_write=True)
    doc = body.dict()
    doc["part_number"] = doc["part_number"].strip()
    doc.update({"id": new_id(), "store_id": sid, "status": "Pending", "created_at": now_iso(),
                "by": user["username"], "by_contact": user.get("contact", "")})
    await db.requirements.insert_one(dict(doc))
    doc.pop("_id", None)
    return doc


@api.get("/requirements")
async def list_requirements(status: Optional[str] = None, store_id: Optional[str] = None,
                            user=Depends(get_current_user)):
    query: Dict[str, Any] = sq(user, None, store_id)
    if status:
        query["status"] = status
    reqs = await db.requirements.find(query, {"_id": 0}).sort("created_at", -1).to_list(500)
    for r in reqs:
        r["stock_count"] = await db.stock.count_documents(
            {"store_id": r.get("store_id"), "part_number": r["part_number"], "sold": {"$ne": True}})
    return reqs


@api.patch("/requirements/{req_id}")
async def update_requirement(req_id: str, body: RequirementUpdate, user=Depends(require("requirement"))):
    updates = {k: v for k, v in body.dict().items() if v is not None}
    r = await db.requirements.update_one(sq(user, {"id": req_id}), {"$set": updates})
    if r.matched_count == 0:
        raise HTTPException(404, "Requirement not found")
    return await db.requirements.find_one({"id": req_id}, {"_id": 0})


# ---------------- Purchase Limits (admin) ----------------
@api.get("/limits/global")
async def get_global_limit(store_id: Optional[str] = None, user=Depends(get_current_user)):
    sid = resolve_store(user, store_id)
    s = await db.settings.find_one({"key": "purchase_limit", "store_id": sid}, {"_id": 0})
    return s or {"global_enabled": False, "global_default": None}


@api.post("/limits/global")
async def set_global_limit(body: GlobalLimitIn, store_id: Optional[str] = None, user=Depends(require("manage_limits"))):
    sid = resolve_store(user, store_id, require_write=True)
    await db.settings.update_one({"key": "purchase_limit", "store_id": sid},
                                 {"$set": {"global_enabled": body.global_enabled, "global_default": body.global_default}},
                                 upsert=True)
    return await db.settings.find_one({"key": "purchase_limit", "store_id": sid}, {"_id": 0})


@api.post("/limits/part")
async def set_part_limit(body: LimitIn, store_id: Optional[str] = None, user=Depends(require("manage_limits"))):
    pn = body.part_number.strip()
    sid = resolve_store(user, store_id, require_write=True)
    r = await db.parts.update_one({"store_id": sid, "part_number": pn},
                                  {"$set": {"purchase_limit": body.limit, "limit_enabled": body.enabled}})
    if r.matched_count == 0:
        raise HTTPException(404, "Part not found")
    return await compute_limit(sid, pn)


@api.get("/limits/{part_number}")
async def get_part_limit(part_number: str, store_id: Optional[str] = None, user=Depends(get_current_user)):
    return await compute_limit(resolve_store(user, store_id), part_number)


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
async def ai_research(body: AiResearchIn, store_id: Optional[str] = None, user=Depends(get_current_user)):
    pn = body.part_number.strip()
    if not pn:
        raise HTTPException(400, "Part number required")
    sid = resolve_store(user, store_id, require_write=True)

    verified = await db.parts.find_one(
        {"store_id": sid, "part_number": pn, "verification_status": "Verified"}, {"_id": 0})
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
            "id": new_id(), "store_id": sid, "part_number": pn, "company": verified.get("company", "All"),
            "result": result, "confidence": 100, "conflict": False,
            "verification": "Verified", "sources": result["sources"],
            "approval_status": "Approved", "from_database": True,
            "created_at": now_iso(), "by": user["username"],
        }
        await db.ai_research.insert_one(dict(doc))
        doc.pop("_id", None)
        return doc

    if not (GEMINI_API_KEY or EMERGENT_LLM_KEY):
        raise HTTPException(503, "AI key not configured")
    try:
        res = await run_gemini(pn, body.company or "All")
        data = parse_json_block(res["text"])
    except Exception as e:
        logger.error(f"AI research failed: {e}")
        raise HTTPException(502, f"AI research failed: {e}")
    grounded = bool(res.get("grounded"))
    if res.get("sources"):
        data["sources"] = res["sources"]
    conflict = bool(data.get("conflict"))
    confidence = int(data.get("confidence", 0) or 0)
    verification = "Requires Verification"
    if not data.get("compatible_vehicles") and data.get("compatible_models"):
        data["compatible_vehicles"] = [
            " ".join([m.get("company", ""), m.get("car_name", ""), m.get("variant", "")]).strip()
            for m in data.get("compatible_models", [])
        ]
    doc = {
        "id": new_id(), "store_id": sid, "part_number": pn, "company": body.company or "All",
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
                           store_id: Optional[str] = None, user=Depends(get_current_user)):
    query: Dict[str, Any] = sq(user, None, store_id)
    if status:
        query["approval_status"] = status
    if part_number:
        query["part_number"] = part_number.strip()
    return await db.ai_research.find(query, {"_id": 0}).sort("created_at", -1).to_list(200)


@api.post("/ai/research/{research_id}/approve")
async def approve_ai(research_id: str, edits: Optional[PartEditIn] = None, user=Depends(require("ai_approve"))):
    doc = await db.ai_research.find_one(sq(user, {"id": research_id}))
    if not doc:
        raise HTTPException(404, "Research not found")
    r = doc["result"]
    pn = doc["part_number"]
    sid = doc.get("store_id")
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
    existing = await db.parts.find_one({"store_id": sid, "part_number": pn})
    if existing:
        await db.parts.update_one({"store_id": sid, "part_number": pn}, {"$set": part_updates})
    else:
        newp = {"id": new_id(), "store_id": sid, "part_number": pn, "barcode": "", "old_number": "",
                "new_number": "", "sticker_color": "", "photos": [], "created_at": now_iso(),
                "created_by": user["username"], "purchase_limit": None, "limit_enabled": False, **part_updates}
        await db.parts.insert_one(newp)
    await db.ai_research.update_one({"id": research_id},
                                    {"$set": {"approval_status": "Approved", "approved_by": user["username"],
                                              "approved_at": now_iso(), "final_result": part_updates}})
    return {"ok": True, "part_number": pn}


@api.post("/ai/research/{research_id}/reject")
async def reject_ai(research_id: str, user=Depends(require("ai_approve"))):
    r = await db.ai_research.update_one(sq(user, {"id": research_id}),
                                        {"$set": {"approval_status": "Rejected", "approved_by": user["username"],
                                                  "approved_at": now_iso()}})
    if r.matched_count == 0:
        raise HTTPException(404, "Research not found")
    return {"ok": True}


# ---------------- Search history / demand / stats ----------------
@api.get("/search-history")
async def search_history(store_id: Optional[str] = None,
                         date_from: Optional[str] = None, date_to: Optional[str] = None,
                         company: Optional[str] = None, category: Optional[str] = None,
                         user=Depends(get_current_user)):
    q: Dict[str, Any] = sq(user, None, store_id)
    if date_from or date_to:
        rng: Dict[str, Any] = {}
        if date_from:
            rng["$gte"] = date_from
        if date_to:
            rng["$lte"] = date_to + "T23:59:59"
        q["last_searched"] = rng
    hist = await db.search_history.find(q, {"_id": 0}).sort("count", -1).to_list(500)
    out = []
    for h in hist:
        p = await db.parts.find_one({"store_id": h.get("store_id"), "part_number": h["part_number"]},
                                    {"_id": 0, "name": 1, "company": 1, "category": 1})
        h["part_name"] = (p or {}).get("name", "")
        h["company"] = (p or {}).get("company", "") or "All"
        h["category"] = (p or {}).get("category", "") or "Uncategorized"
        if company and company != "All" and h["company"] != company:
            continue
        if category and h["category"] != category:
            continue
        out.append(h)
    return out


@api.get("/demand")
async def demand(store_id: Optional[str] = None, user=Depends(get_current_user)):
    hist = await db.search_history.find(sq(user, None, store_id), {"_id": 0}).sort("count", -1).to_list(200)
    out = []
    for h in hist:
        sc = await db.stock.count_documents(
            {"store_id": h.get("store_id"), "part_number": h["part_number"], "sold": {"$ne": True}})
        if h["count"] >= 2 and sc == 0:
            out.append({**h, "stock_count": sc, "demand": "HIGH"})
    return out


@api.get("/stats")
async def stats(store_id: Optional[str] = None, user=Depends(require("view_stats"))):
    base = sq(user, None, store_id)
    total_parts = await db.parts.count_documents(base)
    in_stock_units = await db.stock.count_documents({**base, "sold": {"$ne": True}})
    sold_units = await db.stock.count_documents({**base, "sold": True})
    pending_reqs = await db.requirements.count_documents({**base, "status": "Pending"})
    pending_ai = await db.ai_research.count_documents({**base, "approval_status": "Pending"})
    verified_parts = await db.parts.count_documents({**base, "verification_status": "Verified"})
    unverified_parts = await db.parts.count_documents({**base, "verification_status": "Unverified"})
    known = await db.known_parts.count_documents(base)
    buys = await db.transactions.count_documents({**base, "type": "buy"})
    sells = await db.transactions.count_documents({**base, "type": "sell"})
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
    await db.files.insert_one({"path": result["path"], "owner_id": user["id"],
                               "store_id": user.get("store_id"), "created_at": now_iso()})
    return {"path": result["path"], "url": f"/api/files/{result['path']}"}


@api.get("/files/{path:path}")
async def files(path: str, token: Optional[str] = None, authorization: Optional[str] = Header(None)):
    tok = token
    if not tok and authorization and authorization.startswith("Bearer "):
        tok = authorization.split(" ", 1)[1]
    if not tok:
        raise HTTPException(401, "Missing token")
    # SEC-003: validate token fully (existence + not disabled), then enforce store ownership.
    try:
        payload = jwt.decode(tok, JWT_SECRET, algorithms=["HS256"], issuer=JWT_ISSUER,
                             options={"require": ["sub", "exp", "iat"]})
    except Exception:
        raise HTTPException(401, "Invalid token")
    viewer = await db.users.find_one({"id": payload["sub"]}, {"_id": 0})
    if not viewer or viewer.get("disabled"):
        raise HTTPException(401, "User not found or disabled")
    rec = await db.files.find_one({"path": path})
    if not rec:
        raise HTTPException(404, "Not found")
    if viewer.get("role") != "super_admin" and rec.get("store_id") not in (None, viewer.get("store_id")):
        raise HTTPException(403, "Not allowed")
    try:
        content, ctype = await run_in_threadpool(get_object, path)
    except Exception:
        raise HTTPException(404, "Not found")
    return Response(content=content, media_type=ctype)


# ---------------- Transactions history + bulk delete (Admin) ----------------
@api.get("/transactions")
async def list_transactions(type: Optional[str] = None, store_id: Optional[str] = None,
                            date_from: Optional[str] = None, date_to: Optional[str] = None,
                            company: Optional[str] = None, category: Optional[str] = None,
                            user=Depends(require_admin)):
    q: Dict[str, Any] = sq(user, {"type": {"$in": ["buy", "sell"]}}, store_id)
    if type in ("buy", "sell"):
        q["type"] = type
    if date_from or date_to:
        rng: Dict[str, Any] = {}
        if date_from:
            rng["$gte"] = date_from
        if date_to:
            rng["$lte"] = date_to + "T23:59:59"
        q["at"] = rng
    txns = await db.transactions.find(q, {"_id": 0}).sort("at", -1).to_list(5000)
    out = []
    for t in txns:
        p = await db.parts.find_one({"store_id": t.get("store_id"), "part_number": t.get("part_number")},
                                    {"_id": 0, "name": 1, "company": 1, "category": 1})
        t["part_name"] = (p or {}).get("name", "")
        t["company"] = (p or {}).get("company", "") or "All"
        t["category"] = (p or {}).get("category", "") or "Uncategorized"
        if company and company != "All" and t["company"] != company:
            continue
        if category and t["category"] != category:
            continue
        out.append(t)
    return out


class TxnDeleteIn(BaseModel):
    ids: List[str]
    remove_stock: bool = True


@api.post("/transactions/delete")
async def delete_transactions(body: TxnDeleteIn, user=Depends(require_admin)):
    deleted_txn = 0
    removed_units = 0
    for tid in body.ids:
        t = await db.transactions.find_one(sq(user, {"id": tid}))
        if not t:
            continue
        if body.remove_stock and t.get("unit_id"):
            r = await db.stock.delete_one({"id": t["unit_id"]})
            removed_units += r.deleted_count
        await db.transactions.delete_one({"id": tid})
        deleted_txn += 1
    return {"ok": True, "deleted": deleted_txn, "removed_units": removed_units}


# ---------------- Backup: export / import (Admin, per store) ----------------
BACKUP_COLLECTIONS = ["parts", "stock", "transactions", "settings",
                      "known_parts", "requirements", "verifications"]


@api.get("/backup/export")
async def backup_export(user=Depends(require_admin)):
    sid = resolve_store(user, None, require_write=True)
    data: Dict[str, Any] = {"app": APP_NAME, "store_id": sid, "exported_at": now_iso(), "collections": {}}
    for col in BACKUP_COLLECTIONS:
        docs = await db[col].find({"store_id": sid}, {"_id": 0}).to_list(100000)
        data["collections"][col] = docs
    return data


class BackupImportIn(BaseModel):
    collections: Dict[str, List[Dict[str, Any]]]


@api.post("/backup/import")
async def backup_import(body: BackupImportIn, user=Depends(require_admin)):
    sid = resolve_store(user, None, require_write=True)
    summary = {}
    for col, docs in body.collections.items():
        if col not in BACKUP_COLLECTIONS:
            continue
        count = 0
        for d in docs:
            d.pop("_id", None)
            d["store_id"] = sid  # force into caller's store — never cross-store import
            if d.get("id"):
                flt = {"id": d["id"], "store_id": sid}
            elif d.get("part_number"):
                flt = {"part_number": d["part_number"], "store_id": sid}
            elif d.get("key"):
                flt = {"key": d["key"], "store_id": sid}
            else:
                await db[col].insert_one(dict(d))
                count += 1
                continue
            await db[col].update_one(flt, {"$set": d}, upsert=True)
            count += 1
        summary[col] = count
    return {"ok": True, "imported": summary}


@api.get("/backup/excel")
async def backup_excel(user=Depends(require_admin)):
    from openpyxl import Workbook
    import io

    sid = resolve_store(user, None, require_write=True)
    wb = Workbook()
    wb.remove(wb.active)
    sheets = {
        "Parts": ("parts", ["part_number", "name", "company", "category", "variant", "verification_status"]),
        "Stock": ("stock", ["part_number", "condition", "sold", "added_by", "created_at"]),
        "Transactions": ("transactions", ["type", "part_number", "price", "by", "at"]),
    }
    for sheet_name, (col, cols) in sheets.items():
        ws = wb.create_sheet(sheet_name)
        ws.append(cols)
        docs = await db[col].find({"store_id": sid}, {"_id": 0}).to_list(100000)
        for d in docs:
            ws.append([str(d.get(c, "")) for c in cols])
    buf = io.BytesIO()
    wb.save(buf)
    buf.seek(0)
    return Response(
        content=buf.read(),
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": "attachment; filename=store_backup.xlsx"},
    )


# ---------------- AI Sticker Scanner (Gemini 3 Pro vision) ----------------
class StickerScanReq(BaseModel):
    image_base64: str  # raw base64 (no data: prefix)


STICKER_SCAN_PROMPT = """You are an OCR + layout extraction engine for product stickers/labels (e.g. automotive ECU labels).
Analyze the sticker in the image and return ONLY a strict JSON object (no markdown, no explanation) with this schema:
{
  "aspect": <number, sticker width divided by height, e.g. 1.45>,
  "part_number": "<the single most important part number on the label, best guess>",
  "lines": [
    {
      "text": "<exact text of this line>",
      "x": <number 0-100, left edge % of the text block>,
      "y": <number 0-100, top edge % of the text block>,
      "size": <number 2-20, font height as % of sticker height>,
      "bold": <true|false>,
      "align": "left" | "center" | "right"
    }
  ],
  "logos": [
    { "label": "<brand name if known e.g. Hyundai>", "x": <0-100>, "y": <0-100>, "w": <0-100>, "h": <0-100> }
  ],
  "codes": [
    { "type": "qr" | "barcode" | "datamatrix", "value": "<decoded value if readable else best guess or empty>", "x": <0-100>, "y": <0-100>, "w": <0-100>, "h": <0-100> }
  ]
}
Rules:
- Capture EVERY visible text line in reading order (top to bottom), however many there are.
- x/y/w/h are percentages relative to the STICKER's bounding box (not the whole photo).
- If the image is rotated/upside-down, read it in its correct upright orientation.
- Include manufacturer logos as logo entries with their bounding box.
- Detect any QR / barcode / datamatrix and its bounding box and type.
- Return valid JSON only."""


@api.post("/scan-sticker")
async def scan_sticker(req: StickerScanReq, user=Depends(get_current_user)):
    if not EMERGENT_LLM_KEY:
        raise HTTPException(500, "AI key not configured")
    b64 = req.image_base64
    if "," in b64 and b64.strip().startswith("data:"):
        b64 = b64.split(",", 1)[1]
    try:
        from emergentintegrations.llm.chat import LlmChat, UserMessage, ImageContent
        chat = LlmChat(
            api_key=EMERGENT_LLM_KEY,
            session_id=f"sticker-{uuid.uuid4().hex[:8]}",
            system_message="You extract structured JSON from product label images. Output JSON only.",
        ).with_model("gemini", "gemini-3.1-pro-preview")
        msg = UserMessage(text=STICKER_SCAN_PROMPT, file_contents=[ImageContent(image_base64=b64)])
        raw = await chat.send_message(msg)
    except Exception as e:
        logger.exception("sticker scan failed")
        raise HTTPException(502, f"AI scan failed: {e}")

    text = raw if isinstance(raw, str) else str(raw)
    # strip markdown fences if present
    t = text.strip()
    if t.startswith("```"):
        t = t.strip("`")
        if t.lower().startswith("json"):
            t = t[4:]
    # extract first {...}
    start = t.find("{")
    end = t.rfind("}")
    if start == -1 or end == -1:
        raise HTTPException(502, "AI returned no JSON")
    try:
        data = json.loads(t[start:end + 1])
    except Exception:
        raise HTTPException(502, "AI returned invalid JSON")
    # normalize
    data.setdefault("aspect", 1.4)
    data.setdefault("part_number", "")
    data.setdefault("lines", [])
    data.setdefault("logos", [])
    data.setdefault("codes", [])
    return data



app.include_router(api)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
