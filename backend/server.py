import os
import re
import uuid
import json
import logging
import asyncio
import secrets
import string
import time
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
from pymongo import ReturnDocument
from pymongo.errors import DuplicateKeyError
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
# Platform-level Owner: a single hardcoded contact number, not a role. Whichever
# user account's own `contact` field (server-side, from their DB record) matches
# this is treated as the Owner everywhere in this file — see is_owner() below.
OWNER_CONTACT = os.environ.get('OWNER_CONTACT', '+919773041676')
EMERGENT_LLM_KEY = os.environ.get('EMERGENT_LLM_KEY')
GEMINI_API_KEY = os.environ.get('GEMINI_API_KEY')
GEMINI_MODEL = os.environ.get('GEMINI_MODEL', 'gemini-3.7-flash')
GEMINI_VISION_MODEL = os.environ.get('GEMINI_VISION_MODEL', 'gemini-3.6-flash')
GEMINI_GROUNDING = os.environ.get('GEMINI_GROUNDING', 'false').lower() == 'true'
GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
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


def _parse_gps(g: Optional[str]) -> Optional[Dict[str, float]]:
    """Shared with admin_gps_locations() below -- the one place in the app
    that already parses the "lat,lng" string format stored on
    requirements.gps / stock.location.gps into {lat, lng}."""
    if not g:
        return None
    try:
        parts = [float(x.strip()) for x in str(g).replace(";", ",").split(",")[:2]]
        if len(parts) == 2 and -90 <= parts[0] <= 90 and -180 <= parts[1] <= 180:
            return {"lat": parts[0], "lng": parts[1]}
    except Exception:
        return None
    return None


# ---------------- Hierarchical location address ----------------
# "Store [name] · [Wall: Front/Back/Left/Right] · [Rack name OR Open Floor +
# Carton number] · [Shelf: Top/Middle/Bottom]" — replaces the old free-text
# `assigned_location` field with a structured object so /inventory/location-check
# can compare fields instead of fuzzy-matching strings.
VALID_WALLS = {"Front", "Back", "Left", "Right"}
VALID_SHELVES = {"Top", "Middle", "Bottom"}


def _normalize_location_fields(
    store_name: Optional[str], wall: Optional[str], rack_name: Optional[str],
    is_open_floor: Optional[bool], carton_number: Optional[Any], shelf_level: Optional[str],
) -> Optional[Dict[str, Any]]:
    store_name = (store_name or "").strip() or None
    wall_raw = (wall or "").strip()
    wall_norm = wall_raw.title() if wall_raw else None
    if wall_norm and wall_norm not in VALID_WALLS:
        wall_norm = None
    is_open_floor = bool(is_open_floor)
    rack_name_norm = None if is_open_floor else ((rack_name or "").strip() or None)
    carton_norm = None
    if is_open_floor and carton_number not in (None, ""):
        carton_norm = str(carton_number).strip() or None
    shelf_raw = (shelf_level or "").strip()
    shelf_norm = shelf_raw.title() if shelf_raw else None
    if shelf_norm and shelf_norm not in VALID_SHELVES:
        shelf_norm = None
    if is_open_floor:
        shelf_norm = None
    if not any([store_name, wall_norm, rack_name_norm, carton_norm, shelf_norm]):
        return None
    return {
        "store_name": store_name, "wall": wall_norm, "rack_name": rack_name_norm,
        "is_open_floor": is_open_floor, "carton_number": carton_norm, "shelf_level": shelf_norm,
    }


def _location_from_model(loc: Optional["AssignedLocationIn"]) -> Optional[Dict[str, Any]]:
    if loc is None:
        return None
    return _normalize_location_fields(
        loc.store_name, loc.wall, loc.rack_name, loc.is_open_floor, loc.carton_number, loc.shelf_level,
    )


def _location_key(loc: Optional[Dict[str, Any]]):
    """Hashable/sortable fingerprint of a normalized location dict, for grouping
    and equality checks (dicts themselves aren't hashable). String fields are
    case-folded so e.g. rack "a-3" and "A-3" are treated as the same address."""
    if not loc:
        return None
    def _norm(v: Any) -> Any:
        return v.strip().lower() if isinstance(v, str) else v
    return tuple(sorted((k, _norm(v)) for k, v in loc.items()))


# ---------------- Permissions ----------------
ALL_PERMISSIONS = [
    "search", "buy", "sell", "requirement", "manage_parts",
    "view_price", "manage_limits", "manage_users", "ai_approve", "view_stats",
    # Gates full part detail (name/company/category/technical info, purchase
    # history, stock units, AI research, purchase-limit status) on the
    # search-result/part-detail screen. A staff account with "search" but
    # without this can still search and see whether a part is in stock and
    # its shelf/rack location -- nothing else. Deliberately NOT in
    # STAFF_DEFAULT: opt-in only, a store admin grants it explicitly.
    "view_part_details",
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
            # Own contact, for client-side UI only (e.g. showing the Owner
            # Panel entry point) — never trusted for the actual owner-only
            # authorization check, which is always is_owner() against the
            # user's server-side DB record, done fresh on every call.
            "contact": u.get("contact", ""),
            "permissions": perms, "disabled": u.get("disabled", False),
            "verified": u.get("verified", True),
            "has_google_key": bool(u.get("google_api_key") and u.get("google_cx"))}


async def _attach_store(user: dict) -> Optional[dict]:
    if user.get("store_id"):
        store = await db.stores.find_one({"id": user["store_id"]}, {"_id": 0})
        if store:
            user["store_name"] = store.get("name", "")
            user["store_gst"] = store.get("gst", "")
            user["store_phone"] = store.get("phone", "")
            user["store_address"] = store.get("address", "")
            user["store_logo"] = store.get("logo_path", "")
            user["store_bank"] = store.get("bank", "")
            return store
    return None


def is_owner(user: dict) -> bool:
    """True only for the specific account whose OWN stored `contact` field —
    from the authenticated user's DB record, never a client-supplied value —
    equals OWNER_CONTACT. Independent of role/permissions entirely; re-derived
    fresh from `user` (itself always freshly loaded by get_current_user) on
    every call, never cached or trusted from a token claim."""
    contact = user.get("contact")
    return bool(contact) and contact == OWNER_CONTACT


def _store_locked_error() -> HTTPException:
    return HTTPException(403, detail={
        "code": "store_locked",
        "message": f"This store has been locked by the platform owner. Contact {OWNER_CONTACT} for help.",
        "contact": OWNER_CONTACT,
    })


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
    store = await _attach_store(user)
    # Central choke point: every authenticated request resolves current_user
    # (and its store) here, so this is the one place needed to reject a
    # locked store's users on every subsequent call, not just at login.
    if store and store.get("status") == "locked" and not is_owner(user):
        raise _store_locked_error()
    return user


def require(permission: str):
    async def dep(user=Depends(get_current_user)):
        role = user.get("role")
        perms = ALL_PERMISSIONS if role in ("admin", "super_admin") else user.get("permissions", [])
        if permission not in perms:
            raise HTTPException(403, f"Permission denied: {permission}")
        return user
    return dep


def has_permission(user: dict, permission: str) -> bool:
    """Soft check (never 403s) for endpoints that stay reachable regardless
    of a permission but reduce what they return without it -- e.g. a part
    listing that still resolves for a "search"-only caller, just with
    sensitive fields stripped. Mirrors require()'s own role/permission
    logic exactly."""
    role = user.get("role")
    perms = ALL_PERMISSIONS if role in ("admin", "super_admin") else user.get("permissions", [])
    return permission in perms


async def require_admin(user=Depends(get_current_user)):
    if user.get("role") not in ("admin", "super_admin"):
        raise HTTPException(403, "ફક્ત Admin આ કરી શકે")
    return user


async def require_super_admin(user=Depends(get_current_user)):
    if user.get("role") != "super_admin":
        raise HTTPException(403, "ફક્ત Admin આ કરી શકે")
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
            raise HTTPException(400, "Admin: પહેલા store પસંદ કરો (store_id)")
        return store_id_param
    return user.get("store_id")


def sq(user: dict, extra: Optional[dict] = None, store_id_param: Optional[str] = None) -> dict:
    q = dict(extra or {})
    sid = resolve_store(user, store_id_param)
    if sid is not None:
        q["store_id"] = sid
    return q


# ---------------- Common Part Catalog (global, shared identity) ----------------
# The IDENTITY of a part (part number, name, company, category, compatibility) is shared
# GLOBALLY across all stores to build an ever-growing cross-referenced database.
# Stock / purchases / sales / limits stay PRIVATE per store (in db.parts / db.stock).
CATALOG_FIELDS = ["name", "company", "category", "compatible_vehicles",
                  "variant", "year", "old_number", "new_number"]


def _nonempty(v) -> bool:
    if v is None:
        return False
    if isinstance(v, str):
        s = v.strip()
        return s != "" and s.lower() != "all"
    if isinstance(v, list):
        return len(v) > 0
    return True


async def upsert_catalog(part_number: str, src: dict, store_id: Optional[str] = None) -> None:
    """Enrich the global catalog with a part's identity. Only fills fields that are
    currently empty/missing globally, so one store never clobbers another's good data."""
    pn = (part_number or "").strip()
    if not pn:
        return
    existing = await db.catalog.find_one({"part_number": pn})
    set_fields: Dict[str, Any] = {}
    for f in CATALOG_FIELDS:
        val = src.get(f)
        if _nonempty(val) and (not existing or not _nonempty(existing.get(f))):
            set_fields[f] = val.strip() if isinstance(val, str) else val
    if existing:
        if set_fields:
            set_fields["updated_at"] = now_iso()
            await db.catalog.update_one({"part_number": pn}, {"$set": set_fields})
    else:
        doc = {"part_number": pn, "created_at": now_iso(), "updated_at": now_iso(),
               "first_store": store_id, **set_fields}
        try:
            await db.catalog.insert_one(doc)
        except Exception:
            if set_fields:
                await db.catalog.update_one({"part_number": pn}, {"$set": set_fields})


# ---------------- Models ----------------
class LoginIn(BaseModel):
    username: str
    password: str


class StoreRequestIn(BaseModel):
    name: str
    mobile: str


class VerifyOtpIn(BaseModel):
    otp: str


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


class AssignedLocationIn(BaseModel):
    """Structured shelf/rack address — distinct from `location` (GPS-only).
    Renders as: "Store [name] · [Wall] · [Rack name OR Open Floor + Carton
    number] · [Shelf]"."""
    store_name: Optional[str] = None
    wall: Optional[str] = None  # Front / Back / Left / Right
    rack_name: Optional[str] = None
    is_open_floor: bool = False
    carton_number: Optional[str] = None
    shelf_level: Optional[str] = None  # Top / Middle / Bottom — Rack only


class BuyIn(BaseModel):
    part_number: str
    company: Optional[str] = "All"
    name: Optional[str] = ""
    category: Optional[str] = ""
    compatible_vehicles: Optional[List[str]] = []
    variant: Optional[str] = ""
    condition: str = "Unknown"
    location: Optional[Dict[str, str]] = {}
    # Manual hierarchical shelf/rack address — distinct from `location` above, which is GPS-only.
    assigned_location: Optional[AssignedLocationIn] = None
    price: Optional[float] = None
    photos: Optional[List[str]] = []
    barcode: Optional[str] = ""
    override: bool = False


class SellIn(BaseModel):
    part_number: str
    unit_id: Optional[str] = None
    price: Optional[float] = None
    buyer: Optional[str] = ""
    # Linking a customer turns this sale into a credit entry on their ledger
    # (they owe `price`) instead of an assumed-paid-in-full cash sale — see
    # the Customer Ledger section below. `buyer` above stays a free-text note
    # independent of this (e.g. "picked up by driver") and is never required
    # to match the linked customer's name.
    customer_id: Optional[str] = None


class CustomerReturnIn(BaseModel):
    part_number: str
    unit_id: Optional[str] = None
    customer_id: Optional[str] = None
    condition: str  # "good" (reusable -> back into sellable stock) | "damaged" (not reusable)
    note: Optional[str] = ""


class DamagedStockIn(BaseModel):
    part_number: str
    unit_id: Optional[str] = None
    note: Optional[str] = ""


class VendorReturnIn(BaseModel):
    part_number: str
    unit_id: Optional[str] = None
    vendor_id: Optional[str] = None
    reason: Optional[str] = ""


class CustomerIn(BaseModel):
    name: str
    phone: str
    address: Optional[str] = ""


class CustomerUpdate(BaseModel):
    name: Optional[str] = None
    phone: Optional[str] = None
    address: Optional[str] = None


class CustomerPaymentIn(BaseModel):
    amount: float
    note: Optional[str] = ""


class VendorIn(BaseModel):
    name: str
    phone: str
    address: Optional[str] = ""
    notes: Optional[str] = ""


class VendorUpdate(BaseModel):
    name: Optional[str] = None
    phone: Optional[str] = None
    address: Optional[str] = None
    notes: Optional[str] = None


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


class LowStockIn(BaseModel):
    part_number: str
    threshold: Optional[int] = None
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

    # Common Part Catalog: global shared identity keyed by part_number.
    try:
        await db.catalog.create_index("part_number", unique=True)
    except Exception as e:
        logger.warning(f"catalog index create warning: {e}")
    # Backfill catalog from any existing per-store parts (idempotent enrich-only).
    try:
        async for p in db.parts.find({}, {"_id": 0}):
            await upsert_catalog(p.get("part_number", ""), p, p.get("store_id"))
    except Exception as e:
        logger.warning(f"catalog backfill warning: {e}")

    # Super Admin (app developer / god-view) — also the account this
    # deployment's Owner mechanism is tied to (OWNER_CONTACT; see is_owner()).
    #
    # SECURITY FIX: this used to match by username alone (ADMIN_USERNAME,
    # default "abdul") — `db.users.find_one({"username": sa_username})` —
    # then unconditionally set role="super_admin" (+ contact=OWNER_CONTACT)
    # on whatever document that found. usernames are ordinary, tenant-chosen
    # values with no reservation; if any store's own admin had ALREADY
    # created a staff/admin account named "abdul" (this app's own documented
    # demo username, not an obviously reserved one) before this block's next
    # run, that exact tenant account got silently promoted — instantly
    # breaking its store scoping (a super_admin's resolve_store() is
    # cross-store by design when no store_id is given), i.e. "a staff user
    # can see data belonging to other stores too". Fixed by matching on a
    # dedicated `platform_seed` marker instead, set only here and never
    # reachable from any user-facing endpoint, so a tenant account can never
    # collide with it regardless of what it's named.
    seed = await db.users.find_one({"platform_seed": True})
    if not seed:
        sa_username = os.environ.get("ADMIN_USERNAME", "abdul").lower()
        base_username = sa_username
        n = 2
        while await db.users.find_one({"username": sa_username}):
            # ADMIN_USERNAME is already taken by a tenant account (or, on a
            # fresh DB, this can't happen) — fall back rather than touching
            # someone else's account.
            sa_username = f"{base_username}_platform{'' if n == 2 else n}"
            n += 1
        await db.users.insert_one({
            "id": new_id(),
            "name": os.environ.get("ADMIN_NAME", "Abdul Salam"),
            "username": sa_username,
            "password_hash": hash_pw(os.environ.get("ADMIN_PASSWORD", "Salam@123")),
            "password_enc": encrypt_pw(os.environ.get("ADMIN_PASSWORD", "Salam@123")),
            "role": "super_admin", "store_id": None, "permissions": ALL_PERMISSIONS,
            "contact": OWNER_CONTACT, "disabled": False, "created_at": now_iso(),
            "platform_seed": True, "verified": True,
        })
        logger.info(f"Seeded platform super admin user (username={sa_username!r})")
    else:
        upd = {"role": "super_admin", "contact": OWNER_CONTACT}
        if not seed.get("password_enc"):
            upd["password_enc"] = encrypt_pw(os.environ.get("ADMIN_PASSWORD", "Salam@123"))
        await db.users.update_one({"id": seed["id"]}, {"$set": upd})

    # One-time repair for any account the OLD username-matching lookup above
    # already mispromoted in the past: role=="super_admin" is never correct
    # for an account that also has a real store_id (the platform account
    # always has store_id=None) unless it's the platform_seed account
    # itself. Can't know what the account's role was before the leak, so
    # this deliberately does not guess "admin" back — "staff" is the
    # never-over-privileged default — and strips OWNER_CONTACT if it was
    # wrongly granted. Logged loudly so a human can manually re-grant
    # whatever the account actually needed.
    async for u in db.users.find({"role": "super_admin", "store_id": {"$ne": None}, "platform_seed": {"$ne": True}}):
        logger.warning(
            f"Reverting user {u.get('username')!r} (id={u['id']}) from super_admin back to staff — "
            "it has a store_id, so it was never the platform account; almost certainly a tenant "
            "account previously mismatched by the old username-based seed lookup. If it needed "
            "admin rights in its own store, re-grant that by hand."
        )
        fix: Dict[str, Any] = {"$set": {"role": "staff"}}
        if u.get("contact") == OWNER_CONTACT:
            fix["$unset"] = {"contact": ""}
        await db.users.update_one({"id": u["id"]}, fix)

    # Store status backfill: only sets it where missing (never overwrites an
    # existing value), same "idempotent enrich-only" shape as the catalog
    # backfill above. New stores get "active" set directly at creation
    # (_create_store_and_admin) — this only covers stores that existed
    # before this field did.
    try:
        r = await db.stores.update_many({"status": {"$exists": False}}, {"$set": {"status": "active"}})
        if r.modified_count:
            logger.info(f"Backfilled status=active on {r.modified_count} store(s)")
    except Exception as e:
        logger.warning(f"store status backfill warning: {e}")

    # created_by backfill: existing accounts predate this field entirely, and
    # there's no reliable way to reconstruct who actually added them, so this
    # deliberately sets null ("Unknown" in the UI) rather than guessing.
    try:
        r = await db.users.update_many({"created_by": {"$exists": False}}, {"$set": {"created_by": None}})
        if r.modified_count:
            logger.info(f"Backfilled created_by=None on {r.modified_count} user(s)")
    except Exception as e:
        logger.warning(f"created_by backfill warning: {e}")

    # verified backfill: any account predating this field (every role, not
    # just staff) grandfathers in as verified=True -- new staff created from
    # now on start explicitly verified=False in create_user() instead, so
    # this never touches them (they already have the field set).
    try:
        r = await db.users.update_many({"verified": {"$exists": False}}, {"$set": {"verified": True}})
        if r.modified_count:
            logger.info(f"Backfilled verified=True on {r.modified_count} user(s)")
    except Exception as e:
        logger.warning(f"verified backfill warning: {e}")

    try:
        await db.store_requests.create_index("id", unique=True)
    except Exception as e:
        logger.warning(f"store_requests index create warning: {e}")

    # Backs both /admin/search-logs (store_id + created_at) and
    # /owner/search-logs (created_at alone, across every store).
    try:
        await db.search_logs.create_index([("store_id", 1), ("created_at", -1)])
        await db.search_logs.create_index("created_at")
    except Exception as e:
        logger.warning(f"search_logs index create warning: {e}")

    try:
        await run_in_threadpool(init_storage)
    except Exception as e:
        logger.warning(f"Object storage init failed (non-fatal): {e}")

    # Unique index backing the one-shot demo lock (see /demo-check) — the
    # uniqueness on "id" is what makes its insert-to-acquire pattern atomic
    # and race-safe. (The /buy purchase-limit mutex deliberately does NOT
    # depend on this index — see the module comment above
    # _acquire_part_buy_lock for why it was moved off of it.)
    try:
        await db.locks.create_index("id", unique=True)
    except Exception as e:
        logger.warning(f"locks index create warning: {e}")


@app.on_event("shutdown")
async def shutdown():
    client.close()


# ---------------- Auth routes ----------------
@api.get("/")
async def root():
    return {"app": "Auto Parts Store", "status": "ok"}


DEMO_LOCK_ID = "single_demo_lock"


@api.post("/demo-check")
async def demo_check():
    """One-shot flag: the very first caller gets {"allowed": true} and flips
    the flag; every call after that gets {"allowed": false}. No auth.

    Atomic via find_one_and_update(upsert=True) racing against a unique index
    on "id" (created at startup) — only one concurrent caller can ever win the
    insert, so this is safe even under concurrent requests.
    """
    try:
        await db.locks.find_one_and_update(
            {"id": DEMO_LOCK_ID, "used": {"$ne": True}},
            {"$set": {"used": True}},
            upsert=True,
            return_document=ReturnDocument.AFTER,
        )
        return {"allowed": True}
    except DuplicateKeyError:
        return {"allowed": False}


# ---------------- Store creation: owner-approved via in-app OTP ----------------
# Replaces the old direct /auth/register (create a store immediately, no
# gate) entirely — anyone reaching the app can no longer spin up a store on
# their own; every store now needs the platform Owner to actually hand over
# an OTP first. store_requests is the pending-approval queue that sits in
# front of the same account-creation logic /auth/register used to run
# inline (see _create_store_and_admin below).
OTP_TTL_MINUTES = 10


def _username_from_mobile(mobile: str) -> str:
    digits = re.sub(r"[^0-9]", "", mobile or "")
    return digits or uuid.uuid4().hex[:10]


async def _unique_username(base: str) -> str:
    username = base
    n = 2
    while await db.users.find_one({"username": username}):
        username = f"{base}{n}"
        n += 1
    return username


def _gen_temp_password() -> str:
    # 8 random uppercase/digit chars -- short enough to read off a screen
    # and retype, long enough to not be trivially guessable as a one-off
    # initial credential (the admin is expected to change it afterward).
    alphabet = string.ascii_uppercase + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(8))


def _gen_otp() -> str:
    return f"{secrets.randbelow(1_000_000):06d}"


async def _create_store_and_admin(store_name: str, mobile: str) -> tuple:
    """The exact account-creation logic /auth/register used to run inline,
    now shared with verify_store_request_otp() below. Since the OTP flow
    only ever collects a name + mobile (no chosen username/password), the
    username is derived from the mobile number and a random temporary
    password is generated — returned in plaintext ONLY to the immediate
    caller (verify_store_request_otp's response, shown once), never stored
    anywhere in plaintext or logged."""
    mobile = mobile.strip()
    username = await _unique_username(_username_from_mobile(mobile))
    temp_password = _gen_temp_password()
    store_id = new_id()
    store = {"id": store_id, "name": store_name.strip(), "owner_username": username,
             "contact": mobile, "created_at": now_iso(), "status": "active"}
    await db.stores.insert_one(dict(store))
    user = {
        "id": new_id(), "name": store_name.strip(), "username": username,
        "password_hash": hash_pw(temp_password), "password_enc": encrypt_pw(temp_password),
        "role": "admin", "store_id": store_id, "permissions": ALL_PERMISSIONS,
        "contact": mobile, "disabled": False, "created_at": now_iso(),
        # The store's own first admin has no human creator.
        "created_by": None,
        "verified": True,
    }
    await db.users.insert_one(dict(user))
    await db.settings.insert_one({"key": "purchase_limit", "store_id": store_id,
                                  "global_enabled": False, "global_default": None})
    user["store_name"] = store["name"]
    return store, user, temp_password


@api.post("/store-requests")
async def create_store_request(body: StoreRequestIn):
    name = body.name.strip()
    mobile = body.mobile.strip()
    if not name:
        raise HTTPException(422, "Store name is required")
    if not mobile:
        raise HTTPException(422, "Mobile number is required")
    # Dedup: one live (not yet verified/expired) request per mobile at a
    # time — resend the same one instead of piling up duplicates.
    existing = await db.store_requests.find_one(
        {"mobile": mobile, "status": {"$in": ["pending", "otp_generated"]}}, {"_id": 0, "otp_hash": 0},
    )
    if existing:
        return existing
    doc = {
        "id": new_id(), "name": name, "mobile": mobile, "status": "pending",
        "otp_hash": None, "otp_expires_at": None, "created_at": now_iso(), "verified_at": None,
    }
    await db.store_requests.insert_one(dict(doc))
    doc.pop("_id", None)
    doc.pop("otp_hash", None)
    return doc


@api.post("/store-requests/{request_id}/verify-otp")
async def verify_store_request_otp(request_id: str, body: VerifyOtpIn):
    req = await db.store_requests.find_one({"id": request_id})
    # Same generic error whether the request doesn't exist, has no OTP yet,
    # is already verified, has expired, or the code is simply wrong — never
    # reveal which case it is.
    generic_error = HTTPException(400, "Invalid or expired code")
    if not req or req.get("status") == "verified" or not req.get("otp_hash"):
        raise generic_error
    expires_at = req.get("otp_expires_at")
    try:
        expired = not expires_at or datetime.fromisoformat(expires_at) < datetime.now(timezone.utc)
    except ValueError:
        expired = True
    if expired:
        await db.store_requests.update_one({"id": request_id}, {"$set": {"status": "expired"}})
        raise generic_error
    if not verify_pw((body.otp or "").strip(), req["otp_hash"]):
        raise generic_error
    store, admin_user, temp_password = await _create_store_and_admin(req["name"], req["mobile"])
    await db.store_requests.update_one(
        {"id": request_id}, {"$set": {"status": "verified", "verified_at": now_iso()}},
    )
    return {
        "access_token": make_token(admin_user), "token_type": "bearer",
        "user": public_user(admin_user), "temp_password": temp_password,
    }


@api.post("/auth/login")
async def login(body: LoginIn):
    user = await db.users.find_one({"username": body.username.lower().strip()})
    if not user or not verify_pw(body.password, user["password_hash"]):
        raise HTTPException(401, "ખોટું username અથવા password")
    if user.get("disabled"):
        raise HTTPException(403, "User disabled")
    # Staff-only verification gate (separate from the store_requests owner-
    # approval flow): a staff account a store admin just created starts
    # verified=False and can't log in until that admin approves it. Admins
    # (and everyone predating this field, via the startup backfill) are
    # always verified=True, so this never blocks them.
    if user.get("role") == "staff" and not user.get("verified", True):
        raise HTTPException(403, detail={
            "code": "staff_pending_approval",
            "message": "Your account is pending approval from your store admin",
        })
    store = await _attach_store(user)
    # Checked right after credentials verify, before a token is issued — a
    # locked store's users (owner excepted) can't log in at all, matching
    # the same check get_current_user() applies to every call afterward.
    if store and store.get("status") == "locked" and not is_owner(user):
        raise _store_locked_error()
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


# ---------------- Owner (platform-level, single hardcoded contact) ----------------
# Entirely separate from the per-store "admin" role and from super_admin's
# existing cross-store read access above — this is additive only, nothing
# here changes what those roles can already do. Gated by is_owner() alone,
# never by role/permissions, so it works regardless of which account (or
# which store, if any) OWNER_CONTACT happens to be seeded onto.
class OwnerDeleteStoreIn(BaseModel):
    confirm_name: str


# Every collection that carries a per-store store_id field (audited against
# every db.<x>.insert_one/insert_many call site in this file). db.catalog is
# deliberately excluded — it's the GLOBAL shared part-identity table, not
# store data. db.locks is excluded too — internal mutex bookkeeping, not
# business data. Deleting a store's db.files rows removes their metadata
# only; the underlying uploaded objects in blob storage are not touched.
OWNER_STORE_SCOPED_COLLECTIONS = [
    "parts", "stock", "transactions", "requirements", "customers", "customer_ledger",
    "vendors", "invoices", "settings", "users", "ai_research", "files", "logos",
    "sticker_templates", "verifications", "stock_adjustments",
]


async def require_owner(user=Depends(get_current_user)):
    # Same generic 403 a permission-denied call gets anywhere else in this
    # app — never a distinct message/code that would confirm to a non-owner
    # that owner-only routes exist at all.
    if not is_owner(user):
        raise HTTPException(403, "Not authorized")
    return user


@api.get("/owner/stores")
async def owner_list_stores(user=Depends(require_owner)):
    stores = await db.stores.find({}, {"_id": 0}).sort("created_at", -1).to_list(1000)
    out = []
    for s in stores:
        sid = s["id"]
        admin = await db.users.find_one({"store_id": sid, "role": "admin"}, {"_id": 0, "contact": 1})
        out.append({
            "id": sid,
            "name": s.get("name", ""),
            "admin_contact": (admin or {}).get("contact", ""),
            "user_count": await db.users.count_documents({"store_id": sid, "deleted_at": {"$exists": False}}),
            "part_count": await db.parts.count_documents({"store_id": sid}),
            "status": s.get("status", "active"),
            "created_at": s.get("created_at"),
            "locked_at": s.get("locked_at"),
            "locked_by": s.get("locked_by"),
        })
    return out


@api.post("/owner/stores/{store_id}/lock")
async def owner_lock_store(store_id: str, user=Depends(require_owner)):
    store = await db.stores.find_one({"id": store_id}, {"_id": 0})
    if not store:
        raise HTTPException(404, "Store not found")
    await db.stores.update_one({"id": store_id}, {"$set": {
        "status": "locked", "locked_at": now_iso(), "locked_by": user.get("contact") or OWNER_CONTACT,
    }})
    return {"ok": True, "id": store_id, "status": "locked"}


@api.post("/owner/stores/{store_id}/unlock")
async def owner_unlock_store(store_id: str, user=Depends(require_owner)):
    store = await db.stores.find_one({"id": store_id}, {"_id": 0})
    if not store:
        raise HTTPException(404, "Store not found")
    await db.stores.update_one(
        {"id": store_id},
        {"$set": {"status": "active"}, "$unset": {"locked_at": "", "locked_by": ""}},
    )
    return {"ok": True, "id": store_id, "status": "active"}


@api.delete("/owner/stores/{store_id}")
async def owner_delete_store(store_id: str, body: OwnerDeleteStoreIn, user=Depends(require_owner)):
    store = await db.stores.find_one({"id": store_id}, {"_id": 0})
    if not store:
        raise HTTPException(404, "Store not found")
    if (body.confirm_name or "").strip() != store.get("name", ""):
        raise HTTPException(400, "Confirmation name does not match this store — nothing was deleted")
    for coll in OWNER_STORE_SCOPED_COLLECTIONS:
        await db[coll].delete_many({"store_id": store_id})
    await db.stores.delete_one({"id": store_id})
    return {"ok": True, "deleted_store_id": store_id}


@api.get("/owner/store-requests")
async def owner_list_store_requests(user=Depends(require_owner)):
    reqs = await db.store_requests.find({}, {"_id": 0, "otp_hash": 0}).sort("created_at", -1).to_list(1000)
    # Stable sort on top of the created_at-desc order above: pending/
    # otp_generated first, each group staying newest-first internally.
    reqs.sort(key=lambda r: 0 if r.get("status") in ("pending", "otp_generated") else 1)
    return reqs


@api.post("/owner/store-requests/{request_id}/generate-otp")
async def owner_generate_otp(request_id: str, user=Depends(require_owner)):
    req = await db.store_requests.find_one({"id": request_id}, {"_id": 0})
    if not req:
        raise HTTPException(404, "Request not found")
    if req.get("status") == "verified":
        raise HTTPException(400, "This request is already verified")
    otp = _gen_otp()
    expires_at = (datetime.now(timezone.utc) + timedelta(minutes=OTP_TTL_MINUTES)).isoformat()
    await db.store_requests.update_one(
        {"id": request_id},
        # Regenerating replaces whatever OTP was issued before it — only the
        # latest one this returns is ever valid.
        {"$set": {"otp_hash": hash_pw(otp), "otp_expires_at": expires_at, "status": "otp_generated"}},
    )
    # The one and only place this is ever exposed in plaintext — directly in
    # this response, to the owner's own screen. Never logged, never stored
    # (only otp_hash is persisted), never sent anywhere by the server itself.
    return {"ok": True, "otp": otp, "expires_at": expires_at}


# ---------------- Owner: staff/admin accounts across every store ----------------
# Individual-account actions, distinct from the store-wide lock/delete above
# (locking a store already blocks everyone in it at once; this is for acting
# on one specific person without touching the rest of their store).
@api.get("/owner/users")
async def owner_list_users(store_id: Optional[str] = None, user=Depends(require_owner)):
    proj = {"_id": 0, "password_hash": 0, "password_enc": 0, "google_api_key": 0, "google_cx": 0}
    query = {"deleted_at": {"$exists": False}}
    # Optional per-store scoping -- lets a caller (the store-detail screen,
    # reached by tapping one store from owner-panel/stores) see only that
    # store's own admin+staff instead of every store's accounts flattened
    # together, which is confusing once there's more than a handful of
    # stores. Omitted entirely (not "" or "all") keeps the old unscoped
    # behavior for owner-panel's own flat "Staff" tab.
    if store_id:
        query["store_id"] = store_id
    users = await db.users.find(query, proj).sort("created_at", -1).to_list(5000)
    store_names = {s["id"]: s.get("name", "") for s in await db.stores.find({}, {"_id": 0, "id": 1, "name": 1}).to_list(1000)}
    out = []
    for u in users:
        sid = u.get("store_id")
        out.append({
            "id": u["id"], "name": u.get("name", ""), "username": u.get("username", ""),
            "role": u.get("role"), "store_id": sid,
            "store_name": store_names.get(sid, "") if sid else "—",
            "disabled": u.get("disabled", False), "created_at": u.get("created_at"),
            "created_by": u.get("created_by"),
        })
    return out


@api.post("/owner/users/{user_id}/deactivate")
async def owner_deactivate_user(user_id: str, user=Depends(require_owner)):
    if user_id == user["id"]:
        raise HTTPException(400, "You can't deactivate your own account")
    target = await db.users.find_one({"id": user_id})
    if not target:
        raise HTTPException(404, "User not found")
    await db.users.update_one({"id": user_id}, {"$set": {"disabled": True}})
    return {"ok": True, "id": user_id, "disabled": True}


@api.post("/owner/users/{user_id}/reactivate")
async def owner_reactivate_user(user_id: str, user=Depends(require_owner)):
    target = await db.users.find_one({"id": user_id})
    if not target:
        raise HTTPException(404, "User not found")
    await db.users.update_one({"id": user_id}, {"$set": {"disabled": False}})
    return {"ok": True, "id": user_id, "disabled": False}


# ---------------- Company-device location (disclosed) ----------------
# A staff account only exists on a company-issued device (see /admin/users
# above -- self-signup never creates a "staff" role at all), so this is
# asset/device tracking of a company phone, not covert tracking of a
# person's own phone. It piggybacks on the location permission the app
# already requires for every screen (LocationGate.tsx) -- no separate
# prompt -- and only ever stores each user's ONE latest point (an upsert,
# never a history/trail), which is enough to answer "where is this store's
# phone right now" without building a movement log.
@api.post("/device/ping-location")
async def device_ping_location(body: dict, user=Depends(get_current_user)):
    lat = body.get("lat")
    lng = body.get("lng")
    if lat is None or lng is None:
        raise HTTPException(400, "lat/lng required")
    await db.device_locations.update_one(
        {"user_id": user["id"]},
        {"$set": {
            "user_id": user["id"], "name": user.get("name", ""), "username": user.get("username", ""),
            "store_id": user.get("store_id"), "lat": float(lat), "lng": float(lng), "at": now_iso(),
        }},
        upsert=True,
    )
    return {"ok": True}


@api.get("/owner/device-locations")
async def owner_device_locations(store_id: Optional[str] = None, user=Depends(require_owner)):
    """Latest known point for every company phone that has pinged in --
    one row per user (never a trail), newest first."""
    query = {}
    if store_id:
        query["store_id"] = store_id
    points = await db.device_locations.find(query, {"_id": 0}).sort("at", -1).to_list(2000)
    store_names = {s["id"]: s.get("name", "") for s in await db.stores.find({}, {"_id": 0, "id": 1, "name": 1}).to_list(1000)}
    for p in points:
        p["store_name"] = store_names.get(p.get("store_id"), "") if p.get("store_id") else "—"
    return points


@api.get("/owner/search-logs")
async def owner_search_logs(page: int = 1, page_size: int = 50, user=Depends(require_owner)):
    """Cross-store version of /admin/search-logs below -- every store's
    search activity, for the platform Owner."""
    page = max(1, page)
    page_size = max(1, min(page_size, 200))
    store_names = {s["id"]: s.get("name", "") for s in await db.stores.find({}, {"_id": 0, "id": 1, "name": 1}).to_list(1000)}
    total = await db.search_logs.count_documents({})
    rows = await db.search_logs.find({}, {"_id": 0}).sort("created_at", -1) \
        .skip((page - 1) * page_size).limit(page_size).to_list(page_size)
    for r in rows:
        r["store_name"] = store_names.get(r.get("store_id"), "—")
        r["gps_coord"] = _parse_gps(r.get("gps"))
    return {"items": rows, "total": total, "page": page, "page_size": page_size}


@api.get("/admin/gps-locations")
async def admin_gps_locations(user=Depends(require_super_admin)):
    """God-view of every GPS point captured across all stores — from requirements
    (inquiries) and stock unit purchase locations. Used by the Super Admin map screen."""
    parse_gps = _parse_gps

    store_names: Dict[str, str] = {}
    for s in await db.stores.find({}, {"_id": 0, "id": 1, "name": 1}).to_list(1000):
        store_names[s["id"]] = s.get("name", "Store")

    out = []
    # Requirements / inquiries
    reqs = await db.requirements.find({"gps": {"$nin": ["", None]}}, {"_id": 0}).sort("created_at", -1).to_list(1000)
    for r in reqs:
        coord = parse_gps(r.get("gps"))
        if not coord:
            continue
        out.append({
            "type": "Requirement", "part_number": r.get("part_number", ""),
            "store_id": r.get("store_id"), "store_name": store_names.get(r.get("store_id"), "—"),
            "by": r.get("by", ""), "at": r.get("created_at", ""),
            "gps": r.get("gps"), **coord,
        })
    # Stock units with a purchase GPS
    units = await db.stock.find({"location.gps": {"$nin": ["", None]}}, {"_id": 0}).sort("created_at", -1).to_list(2000)
    for u in units:
        coord = parse_gps((u.get("location") or {}).get("gps"))
        if not coord:
            continue
        out.append({
            "type": "Purchase", "part_number": u.get("part_number", ""),
            "store_id": u.get("store_id"), "store_name": store_names.get(u.get("store_id"), "—"),
            "by": u.get("added_by", "") or u.get("created_by", ""), "at": u.get("created_at", ""),
            "gps": (u.get("location") or {}).get("gps"), **coord,
        })
    out.sort(key=lambda x: x.get("at", ""), reverse=True)
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
    # SEC-001: only admin/super_admin may create admin-role users (within their own
    # store, since sid above is already locked to it for a non-super-admin caller);
    # plain staff can only make staff.
    role = "staff"
    if body.role == "admin" and user.get("role") in ("admin", "super_admin"):
        role = "admin"
    doc = {
        "id": new_id(), "name": body.name, "username": body.username.lower().strip(),
        "password_hash": hash_pw(body.password),
        "password_enc": encrypt_pw(body.password),
        "role": role, "store_id": sid,
        "permissions": body.permissions if body.permissions is not None else STAFF_DEFAULT,
        "disabled": False, "created_at": now_iso(),
        # Who added this account — from the authenticated caller's own DB
        # record, never anything the client could pass in the request body.
        "created_by": {"id": user["id"], "name": user.get("name", ""), "contact": user.get("contact", "")},
        # Staff accounts start unverified and can't log in until their store
        # admin approves them (see /admin/users/{id}/verify below and the
        # login() check) -- admin-role accounts are always implicitly
        # verified, there's no one else in the store to approve them.
        "verified": role != "staff",
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
    # SEC-001: plain staff cannot modify an admin/super_admin account other than self.
    # admin/super_admin themselves can — the store-isolation check above already
    # guarantees the target is in their own store unless the caller is super_admin.
    if target["role"] in ("admin", "super_admin") and user.get("role") not in ("admin", "super_admin") and target["id"] != user["id"]:
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


# Store-admin staff approval -- separate from the platform Owner's
# store_requests approval flow above: this is a store's own admin approving
# their own staff, scoped to that one store, using the same "manage_users"
# permission gate and store-isolation check as the rest of /admin/users/*.
@api.post("/admin/users/{user_id}/verify")
async def verify_user(user_id: str, user=Depends(require("manage_users"))):
    target = await db.users.find_one({"id": user_id})
    if not target:
        raise HTTPException(404, "User not found")
    if user.get("role") != "super_admin" and target.get("store_id") != user.get("store_id"):
        raise HTTPException(403, "બીજા store નો user verify ન કરાય")
    if target["role"] != "staff":
        raise HTTPException(400, "Only staff accounts need verification")
    await db.users.update_one({"id": user_id}, {"$set": {"verified": True}})
    return {"ok": True, "id": user_id, "verified": True}


@api.post("/admin/users/{user_id}/unverify")
async def unverify_user(user_id: str, user=Depends(require("manage_users"))):
    target = await db.users.find_one({"id": user_id})
    if not target:
        raise HTTPException(404, "User not found")
    if user.get("role") != "super_admin" and target.get("store_id") != user.get("store_id"):
        raise HTTPException(403, "બીજા store નો user unverify ન કરાય")
    if target["role"] != "staff":
        raise HTTPException(400, "Only staff accounts can be unverified")
    await db.users.update_one({"id": user_id}, {"$set": {"verified": False}})
    return {"ok": True, "id": user_id, "verified": False}


@api.get("/admin/search-logs")
async def admin_search_logs(page: int = 1, page_size: int = 50, store_id: Optional[str] = None,
                            user=Depends(require("manage_users"))):
    """That store's own staff/admin search activity -- store-scoped exactly
    like the rest of /admin/users/* (locked to the caller's own store for a
    normal admin; a super_admin may target any store via store_id, or see
    all stores' logs when it's omitted, same as list_users())."""
    page = max(1, page)
    page_size = max(1, min(page_size, 200))
    query = sq(user, None, store_id)
    total = await db.search_logs.count_documents(query)
    rows = await db.search_logs.find(query, {"_id": 0}).sort("created_at", -1) \
        .skip((page - 1) * page_size).limit(page_size).to_list(page_size)
    for r in rows:
        r["gps_coord"] = _parse_gps(r.get("gps"))
    return {"items": rows, "total": total, "page": page, "page_size": page_size}


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
# A given real-world part reaches the backend written two different ways:
#   - Typed by hand (e.g. in the Limits screen, or a manual search box) —
#     preserved as-typed, hyphens and all, e.g. "954A0-CCAF0".
#   - Mined from an actual barcode/QR scan (frontend's extractPartNumber) —
#     stripped down to just the alphanumeric OEM code, no separators at all,
#     e.g. "954A0CCAF0".
# Both spellings are the SAME part, but a plain Mongo equality match treats
# them as two unrelated documents — so a limit set by typing the hyphenated
# form never matches stock recorded from a scan of the same part, and the
# limit silently never triggers. _canon_pn()/_pn_regex() make part-number
# lookups punctuation-insensitive so either spelling finds the other.
def _canon_pn(s: Optional[str]) -> str:
    return re.sub(r"[^0-9A-Z]", "", (s or "").strip().upper())


def _pn_regex(part_number: Optional[str]):
    """A regex matching `part_number` and any punctuation/spacing variant of
    it with the same alphanumeric characters in the same order (e.g. matches
    "954A0-CCAF0", "954A0 CCAF0" and "954A0CCAF0" alike). Use as the value of
    a `part_number` filter in place of an exact-string match."""
    canon = _canon_pn(part_number)
    if not canon:
        return re.compile(r"(?!)")  # matches nothing
    pattern = r"[^0-9A-Za-z]*".join(re.escape(ch) for ch in canon)
    return re.compile(f"^{pattern}$", re.IGNORECASE)


async def _find_canonical_part(base: Dict[str, Any], pn: str) -> Optional[dict]:
    """Resolve `pn` (any punctuation/casing variant) to ONE db.parts document,
    self-healing pre-existing duplicates rather than assuming _pn_regex only
    ever matches one.

    _pn_regex() above stops a *new* buy/limit-set from spawning a fresh
    duplicate once it exists — it does nothing about parts that had ALREADY
    fragmented into more than one document before that matching existed (the
    exact state left behind by real historical usage: a "Limit"-source ghost
    doc from an admin typing "954A0-CCAF0" into the Limits screen, and a
    separate "Buy"-source doc from real scans recording "954A0CCAF0", created
    independently before anything consolidated them). A bare
    `find_one({"part_number": _pn_regex(pn)})` at every call site has NO
    ordering guarantee across two such matches — it can silently return
    whichever document happens to sort first, which may well be the one with
    limit_enabled=False, even though the sibling document for the SAME real
    part has a limit properly configured. The purchase then sails through
    with no error and no signal anything is wrong, regardless of how correct
    the punctuation-matching itself is. Reproduced end-to-end against the
    live compute_limit()/buy() code: with a pre-existing "Buy" doc (no limit)
    inserted before a pre-existing "Limit" doc (limit_enabled=True) for the
    same canonical part number, buy() picked the limit-less doc and let a
    3rd unit through against a configured limit of 2.

    Fix: fetch every matching document, merge any limit/low-stock config and
    descriptive fields found on ANY of them onto the earliest-created one,
    persist that merge, and delete the now-redundant duplicates — so the
    ambiguity is resolved once and every later lookup is unambiguous.

    `base` is not guaranteed to pin one store (a super_admin's read-only,
    cross-store aggregate view calls compute_limit()/compute_low_stock() with
    store_id=None, so `base` can be {}) — merging/deleting must never cross a
    store_id boundary, so duplicates are consolidated separately WITHIN each
    store_id found among the matches, never against a different store's doc.
    """
    matches = await db.parts.find({**base, "part_number": _pn_regex(pn)}, {"_id": 0}).sort("created_at", 1).to_list(200)
    if len(matches) <= 1:
        return matches[0] if matches else None
    by_store: Dict[Any, List[dict]] = {}
    for d in matches:
        by_store.setdefault(d.get("store_id"), []).append(d)
    canonicals = []
    for group in by_store.values():
        canonical, dupes = group[0], group[1:]
        if dupes:
            merged: Dict[str, Any] = {}
            for extra in dupes:
                if extra.get("limit_enabled") and not canonical.get("limit_enabled"):
                    merged["purchase_limit"] = extra.get("purchase_limit")
                    merged["limit_enabled"] = True
                if extra.get("low_stock_enabled") and not canonical.get("low_stock_enabled"):
                    merged["low_stock_threshold"] = extra.get("low_stock_threshold")
                    merged["low_stock_enabled"] = True
                for field in ("name", "category", "compatible_vehicles", "variant"):
                    if extra.get(field) and not canonical.get(field):
                        merged[field] = extra[field]
                if extra.get("company") and extra["company"] != "All" and canonical.get("company", "All") == "All":
                    merged["company"] = extra["company"]
            if merged:
                canonical.update(merged)
                await db.parts.update_one({"store_id": canonical.get("store_id"), "part_number": canonical["part_number"]},
                                          {"$set": merged})
            for extra in dupes:
                await db.parts.delete_one({"store_id": extra.get("store_id"), "part_number": extra["part_number"]})
        canonicals.append(canonical)
    # matches was sorted by created_at ascending, and each group[0] kept that
    # order, so the earliest-created canonical overall is simply the first.
    return canonicals[0]


# ---------------- Purchase-limit concurrency lock ----------------
# ba366de and ce7f981 fixed two *identity* bugs (a limit set under one
# spelling of a part number not matching stock recorded under another). Both
# assumed /buy's check-then-insert — read existing_stock via compute_limit(),
# decide, THEN insert the new stock.insert_one() — was itself safe. It isn't:
# nothing serializes two /buy calls for the same part, so two requests can
# both read "4 of 5 used, OK to proceed" before either write commits, and
# both insert — landing at 6 units against a limit of 5 with no error to
# either caller. This reproduces reliably under real network/DB latency
# (verified by re-running compute_limit() with an injected delay between its
# read and /buy's insert: 12 concurrent buys against a limit of 5 all
# succeeded, 12 stock docs created, zero blocked) — it just doesn't show up
# in a sequential test, which is why it read as "sometimes blocks, sometimes
# doesn't" rather than a deterministic failure like the two prior bugs.
#
# Fix: a short-lived mutex per (store, canonical part number), so only one
# /buy critical section for a given part runs at a time.
#
# bb81986 first implemented this as a separate db.locks collection racing a
# brand-new unique index on "id", created at startup the same way the
# pre-existing /demo-check one-shot lock is. That shipped correctly here and
# passed a full concurrent-request test locally, but the bug persisted in
# production — and startup()'s index creation is wrapped in a bare
# try/except that only logs a warning on failure (matching the pattern the
# pre-existing demo-check index already used). If that CREATE INDEX ever
# silently fails on the live database for any deployment-specific reason
# (permissions, a transient hiccup at boot, cluster-specific quirks) —
# impossible to confirm from here without direct access to the live Render
# service's logs/database — db.locks.insert_one() with a duplicate "id"
# simply never raises DuplicateKeyError, and _acquire_buy_lock becomes a
# silent no-op: every caller "acquires" the lock immediately, and the
# original race is wide open again with no error anywhere to point at it.
#
# Rebuilt to remove that dependency entirely: the lock now lives on the part
# DOCUMENT ITSELF (a buy_lock_until field), guarded by the (store_id,
# part_number) unique index on db.parts — which has existed since long
# before this feature and has been load-bearing for basic data integrity
# the whole time, so its presence in production is far better established
# than a brand-new index shipped alongside the very fix that depends on it.
# Claiming/releasing the lock are plain (non-upsert) single-document
# updates matched by that already-unique key, which MongoDB guarantees are
# atomic independent of ANY secondary index — so correctness no longer rests
# on a second CREATE INDEX succeeding at boot at all.
BUY_LOCK_TTL_SECONDS = 30.0
BUY_LOCK_WAIT_SECONDS = 5.0


async def _acquire_part_buy_lock(store_id: Optional[str], part_number: str) -> bool:
    """Claim the mutex on an EXISTING part document — callers must resolve/
    create the part first (see _ensure_part_for_buy) so this is always a
    plain update against a document that's already there, never an upsert
    (which would reintroduce exactly the index-dependent race this design
    avoids)."""
    deadline = time.monotonic() + BUY_LOCK_WAIT_SECONDS
    while True:
        now = datetime.now(timezone.utc)
        claimed = await db.parts.find_one_and_update(
            {
                "store_id": store_id, "part_number": part_number,
                "$or": [
                    {"buy_lock_until": {"$exists": False}},
                    {"buy_lock_until": None},
                    {"buy_lock_until": {"$lt": now}},
                ],
            },
            {"$set": {"buy_lock_until": now + timedelta(seconds=BUY_LOCK_TTL_SECONDS)}},
        )
        if claimed is not None:
            return True
        if time.monotonic() >= deadline:
            return False
        await asyncio.sleep(0.05)


async def _release_part_buy_lock(store_id: Optional[str], part_number: str) -> None:
    await db.parts.update_one({"store_id": store_id, "part_number": part_number},
                              {"$set": {"buy_lock_until": None}})


async def _ensure_part_for_buy(sid: Optional[str], pn: str, body: "BuyIn", user: dict) -> dict:
    """Resolve `pn` (any punctuation/casing variant) to an existing part doc
    via _find_canonical_part, or create a fresh one. Safe against two
    concurrent /buy calls for the SAME brand-new part racing to create it:
    the loser's insert_one raises DuplicateKeyError against the existing
    (store_id, part_number) unique index, which is caught and treated as
    "the other request just created it" rather than a 500."""
    part = await _find_canonical_part({"store_id": sid}, pn)
    if part:
        return part
    cat = await db.catalog.find_one({"part_number": pn}, {"_id": 0}) or {}
    comp = body.company if _nonempty(body.company) else cat.get("company")
    doc = {
        "id": new_id(), "store_id": sid, "part_number": pn, "company": comp or "All",
        "name": body.name or cat.get("name", "") or "",
        "category": body.category or cat.get("category", "") or "",
        "compatible_vehicles": body.compatible_vehicles or cat.get("compatible_vehicles", []) or [],
        "variant": body.variant or cat.get("variant", "") or "",
        "year": cat.get("year", "") or "", "old_number": cat.get("old_number", "") or "",
        "new_number": cat.get("new_number", "") or "", "barcode": body.barcode or "",
        "sticker_color": "", "technical_info": "", "photos": [], "source": "Buy",
        "verification_status": "Unverified", "created_at": now_iso(),
        "created_by": user["username"], "purchase_limit": None, "limit_enabled": False,
    }
    try:
        await db.parts.insert_one(dict(doc))
    except DuplicateKeyError:
        existing = await db.parts.find_one({"store_id": sid, "part_number": pn}, {"_id": 0})
        if existing:
            return existing
        raise
    await upsert_catalog(pn, doc, sid)
    return doc


async def compute_limit(store_id: Optional[str], part_number: str) -> dict:
    # part_number is never case-normalized at write time elsewhere, and Mongo
    # matches are case-sensitive — normalize here so a limit set under one
    # casing still applies to a purchase recorded under a different one.
    part_number = (part_number or "").strip().upper()
    base = {"store_id": store_id} if store_id is not None else {}
    rx = _pn_regex(part_number)
    part = await _find_canonical_part(base, part_number)
    existing_stock = await db.stock.count_documents({**base, "part_number": rx, "sold": {"$ne": True}})
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


async def compute_low_stock(store_id: Optional[str], part_number: str) -> dict:
    # Same typed-vs-scanned formatting mismatch as compute_limit() above —
    # match punctuation-insensitively so either spelling finds the other.
    part_number = (part_number or "").strip().upper()
    base = {"store_id": store_id} if store_id is not None else {}
    rx = _pn_regex(part_number)
    part = await _find_canonical_part(base, part_number)
    stock_count = await db.stock.count_documents({**base, "part_number": rx, "sold": {"$ne": True}})
    enabled = bool(part and part.get("low_stock_enabled") and part.get("low_stock_threshold") is not None)
    threshold = part.get("low_stock_threshold") if part else None
    low = bool(enabled and threshold is not None and stock_count <= threshold)
    return {
        "part_number": part_number, "stock_count": stock_count,
        "low_stock_enabled": enabled, "low_stock_threshold": threshold, "low": low,
    }


# ---------------- Search ----------------
async def part_status(store_id: Optional[str], part_number: str) -> dict:
    base = {"store_id": store_id} if store_id is not None else {}
    part = await db.parts.find_one({**base, "part_number": part_number}, {"_id": 0})
    stock_count = await db.stock.count_documents({**base, "part_number": part_number, "sold": {"$ne": True}})
    known = await db.known_parts.find_one({**base, "part_number": part_number}, {"_id": 0})
    requirement = await db.requirements.find_one(
        {**base, "part_number": part_number, "status": {"$in": ["Pending", "Purchased"]}}, {"_id": 0})
    catalog = await db.catalog.find_one({"part_number": part_number}, {"_id": 0})
    if stock_count > 0:
        status = "IN STOCK"
    elif requirement:
        status = "REQUIREMENT"
    elif part or known:
        status = "KNOWN PART"
    elif catalog:
        status = "IN CATALOG"
    else:
        status = "NEW PART"
    return {
        "status": status, "part_number": part_number, "part": part,
        "stock_count": stock_count, "known": known, "requirement": requirement,
        "catalog": catalog,
    }


# db.search_history (above/below, via /search's own update_one call) is a
# per-part-per-store AGGREGATE counter (count/last_searched/last_status) used
# by /demand and /search-history -- it has no per-event who/when/where, so it
# can't serve the admin/owner "who searched what, when, from where" log. This
# is a genuinely new, separate collection, not a duplicate of it.
async def _log_search(user: dict, store_id: Optional[str], part_number_searched: str, gps: Optional[str] = None) -> None:
    try:
        await db.search_logs.insert_one({
            "id": new_id(), "user_id": user["id"], "user_name": user.get("name", ""),
            "role": user.get("role"), "store_id": store_id,
            "part_number_searched": part_number_searched,
            # Raw "lat,lng" string, same format/field already used on
            # requirements.gps and stock.location.gps -- parsed on read via
            # _parse_gps(), same as admin_gps_locations() does for those.
            # None whenever the caller's screen never captured GPS at all
            # (no new location-permission prompt is added anywhere for this).
            "gps": gps or None,
            "created_at": now_iso(),
        })
    except Exception as e:
        logger.warning(f"search_logs insert warning: {e}")


@api.get("/search")
async def search(q: str, store_id: Optional[str] = None, gps: Optional[str] = None, user=Depends(require("search"))):
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
    await _log_search(user, sid, pn, gps)
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
    # This screen (the Catalog tab's category browse) never captures GPS,
    # so gps is always omitted here -- q is the closest thing to a "part
    # number searched" when given; otherwise this logs as a category/company
    # browse rather than a specific part lookup.
    await _log_search(
        user, resolve_store(user, store_id),
        q.strip() if q else f"(browse: {category or company or 'All'})",
    )
    if not has_permission(user, "view_part_details"):
        # This is the only caller of /parts in the app (the Catalog tab's
        # category browse list) -- a staff account without view_part_details
        # only ever learns a part number exists here, never its name,
        # company, exact stock count, or verification status.
        return [{"id": p["id"], "part_number": p["part_number"], "exists": p["stock_count"] > 0} for p in parts]
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
    await upsert_catalog(pn, doc, sid)
    return doc


@api.get("/parts/{part_number}")
async def get_part(part_number: str, store_id: Optional[str] = None, user=Depends(get_current_user)):
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
    await upsert_catalog(part_number.strip(), updates, resolve_store(user, store_id))
    return await db.parts.find_one(sq(user, {"part_number": part_number}, store_id), {"_id": 0})


@api.get("/catalog/{part_number}")
async def get_catalog(part_number: str, user=Depends(get_current_user)):
    """Global shared part identity (enrichment) — same across every store."""
    pn = part_number.strip()
    doc = await db.catalog.find_one({"part_number": pn}, {"_id": 0})
    if not doc:
        return {"found": False, "part_number": pn}
    return {"found": True, **doc}


# ---------------- Buy (increases stock) ----------------
@api.post("/buy")
async def buy(body: BuyIn, store_id: Optional[str] = None, user=Depends(require("buy"))):
    # Uppercased so this always lines up with the identity compute_limit()/
    # /limits/part use — part_number matches are case-sensitive in Mongo, and a
    # limit set under one casing must still apply to a buy under another.
    pn = body.part_number.strip().upper()
    if not pn:
        raise HTTPException(400, "Part number required")
    sid = resolve_store(user, store_id, require_write=True)
    # Match punctuation-insensitively (see _pn_regex) so a buy scanned as
    # "954A0CCAF0" finds a part document already created under a
    # differently-formatted spelling of the SAME part number — e.g.
    # "954A0-CCAF0", typed by hand when an admin proactively set a limit on
    # it via /limits/part before any purchase existed. Without this, the buy
    # would spawn an untracked duplicate part with no limit attached, and
    # the configured limit would silently never apply to it. Also self-heals
    # the case where multiple spelling-variant documents already exist (see
    # _find_canonical_part's docstring), and is safe against two concurrent
    # /buy calls for the SAME brand-new part racing to create it (see
    # _ensure_part_for_buy's docstring) — this all runs BEFORE the lock below
    # because the lock lives on the part document itself, which must exist
    # first.
    part = await _ensure_part_for_buy(sid, pn, body, user)
    # Keep recording stock under the identity that already existed (and may
    # already carry a limit/threshold) rather than the raw scanned spelling,
    # so every buy of this part accumulates against one canonical
    # part_number instead of fragmenting across spellings.
    pn = part["part_number"]
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
    await upsert_catalog(pn, {**part, **fill}, sid)

    # Serialize the check-then-insert critical section per (store, canonical
    # part) — see the module comment above _acquire_part_buy_lock. Without
    # this, two concurrent /buy calls for the same part can both read "under
    # limit" from compute_limit() before either write commits, and both
    # insert.
    if not await _acquire_part_buy_lock(sid, pn):
        raise HTTPException(503, "Server busy processing this part — try again")
    try:
        limit = await compute_limit(sid, pn)
        if limit["limit_enabled"] and limit["remaining"] is not None and limit["remaining"] <= 0 and not body.override:
            raise HTTPException(409, detail={"code": "LIMIT_REACHED", "message": "DO NOT BUY — purchase limit reached",
                                             "limit": limit})
        unit = {
            "id": new_id(), "store_id": sid, "part_number": pn, "condition": body.condition,
            "location": body.location or {}, "assigned_location": _location_from_model(body.assigned_location),
            "photos": body.photos or [],
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
    finally:
        await _release_part_buy_lock(sid, pn)


# ---------------- GST Invoicing ----------------
# Fixed flat rate — no slab lookup, per the shop's requirements.
GST_RATE = 0.18


def _financial_year_label(dt: datetime) -> str:
    """Indian financial year: Apr 1 - Mar 31, formatted like "2026-27"."""
    start_year = dt.year if dt.month >= 4 else dt.year - 1
    return f"{start_year}-{str(start_year + 1)[-2:]}"


async def _next_invoice_number(store_id: Optional[str], fy_label: str) -> str:
    # Numbering is derived fresh from how many invoices already exist for this
    # store+financial-year, rather than a stored counter — same "never let a
    # cached total drift from what actually justifies it" approach used by
    # compute_customer_balance() below. A tiny race between two concurrent
    # sells could in theory produce a duplicate number; acceptable here since
    # invoice_number is a display label, not a uniqueness key.
    count = await db.invoices.count_documents({"store_id": store_id, "fy_label": fy_label})
    return f"INV/{fy_label}/{count + 1:04d}"


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
    customer = None
    if body.customer_id:
        # Validate up front, before touching stock, so a bad customer_id fails
        # loudly instead of selling the unit and then silently skipping the
        # ledger entry it was supposed to get.
        customer = await db.customers.find_one({"store_id": sid, "id": body.customer_id}, {"_id": 0})
        if not customer:
            raise HTTPException(404, "Customer not found")
        if not body.price:
            raise HTTPException(400, "Price required to record a sale on credit")
    await db.stock.update_one({"id": unit["id"]}, {"$set": {"sold": True, "sold_at": now_iso(), "sold_by": user["username"]}})
    txn = {"id": new_id(), "store_id": sid, "type": "sell", "part_number": pn, "unit_id": unit["id"],
           "price": body.price, "buyer": body.buyer or "", "customer_id": body.customer_id,
           "by": user["username"], "at": now_iso()}
    await db.transactions.insert_one(dict(txn))
    remaining = await db.stock.count_documents({"store_id": sid, "part_number": pn, "sold": {"$ne": True}})
    result = {"ok": True, "remaining_stock": remaining}
    if customer:
        entry = {
            "id": new_id(), "store_id": sid, "customer_id": body.customer_id, "type": "sale",
            "amount": body.price, "note": f"Credit sale — {pn}",
            "part_number": pn, "unit_id": unit["id"], "transaction_id": txn["id"],
            "by": user["username"], "at": now_iso(),
        }
        await db.customer_ledger.insert_one(dict(entry))
        result["credit_balance"] = await compute_customer_balance(sid, body.customer_id)

    # Every successful sale gets an invoice — even when price is missing (a
    # part sold with no price recorded), so the invoice trail always matches
    # the transaction trail. Financial fields are left null rather than
    # silently treated as zero, same "unknown, don't fake a number" approach
    # used for missing cost prices in the profit report.
    at = now_iso()
    fy_label = _financial_year_label(datetime.now(timezone.utc))
    invoice_number = await _next_invoice_number(sid, fy_label)
    part = await db.parts.find_one({"store_id": sid, "part_number": pn}, {"_id": 0, "name": 1})
    store = await db.stores.find_one({"id": sid}, {"_id": 0, "name": 1, "gst": 1})
    price = body.price
    gst_amount = round(price * GST_RATE, 2) if price is not None else None
    total = round(price + gst_amount, 2) if price is not None else None
    invoice = {
        "id": new_id(), "invoice_number": invoice_number, "fy_label": fy_label,
        "store_id": sid, "store_name": (store or {}).get("name", ""), "store_gst": (store or {}).get("gst", ""),
        "unit_id": unit["id"], "transaction_id": txn["id"],
        "part_number": pn, "description": (part or {}).get("name", "") or "",
        "customer_id": body.customer_id, "customer_name": (customer or {}).get("name", ""),
        "price": price, "gst_rate": GST_RATE, "gst_amount": gst_amount, "total": total,
        "by": user["username"], "at": at,
    }
    await db.invoices.insert_one(dict(invoice))
    invoice.pop("_id", None)
    result["invoice"] = invoice
    return result


@api.get("/invoices/{invoice_id}")
async def get_invoice(invoice_id: str, store_id: Optional[str] = None, user=Depends(require("sell"))):
    inv = await db.invoices.find_one(sq(user, {"id": invoice_id}, store_id), {"_id": 0})
    if not inv:
        raise HTTPException(404, "Invoice not found")
    return inv


# ---------------- Customer Ledger (Grahak Khata) ----------------
# A customer's running balance is never stored as a mutable field — like
# compute_limit()/compute_low_stock() above, it's computed fresh from the
# ledger entries every time, so it can never drift out of sync with the
# entries that actually justify it. type="sale" entries increase what the
# customer owes; type="payment" entries reduce it.
async def compute_customer_balance(store_id: Optional[str], customer_id: str) -> float:
    entries = await db.customer_ledger.find({"store_id": store_id, "customer_id": customer_id}, {"_id": 0}).to_list(10000)
    balance = 0.0
    for e in entries:
        amt = e.get("amount") or 0
        balance += amt if e.get("type") == "sale" else -amt
    return round(balance, 2)


@api.post("/customers")
async def create_customer(body: CustomerIn, store_id: Optional[str] = None, user=Depends(require("sell"))):
    name = body.name.strip()
    phone = body.phone.strip()
    if not name or not phone:
        raise HTTPException(400, "Name and phone are required")
    sid = resolve_store(user, store_id, require_write=True)
    # One ledger per phone number per store — otherwise the same customer
    # typed in twice (or looked up by an operator who didn't search first)
    # fragments their credit history across two untracked records, the exact
    # failure mode the purchase-limit part_number fixes earlier addressed.
    if await db.customers.find_one({"store_id": sid, "phone": phone}):
        raise HTTPException(400, "A customer with this phone number already exists")
    doc = {
        "id": new_id(), "store_id": sid, "name": name, "phone": phone, "address": body.address or "",
        "created_at": now_iso(), "created_by": user["username"],
    }
    await db.customers.insert_one(dict(doc))
    doc.pop("_id", None)
    return doc


@api.get("/customers")
async def list_customers(q: Optional[str] = None, phone: Optional[str] = None,
                         store_id: Optional[str] = None, user=Depends(get_current_user)):
    query = sq(user, None, store_id)
    if phone:
        # Exact match — used by the barcode/card-scan lookup, which decodes a
        # known phone number and needs one unambiguous result, not a fuzzy list.
        query["phone"] = phone.strip()
    elif q:
        rx = {"$regex": re.escape(q.strip()[:64]), "$options": "i"}
        query["$or"] = [{"name": rx}, {"phone": rx}]
    customers = await db.customers.find(query, {"_id": 0}).sort("name", 1).to_list(500)
    for c in customers:
        c["balance"] = await compute_customer_balance(c.get("store_id"), c["id"])
    return customers


@api.get("/customers/{customer_id}")
async def get_customer(customer_id: str, store_id: Optional[str] = None, user=Depends(get_current_user)):
    customer = await db.customers.find_one(sq(user, {"id": customer_id}, store_id), {"_id": 0})
    if not customer:
        raise HTTPException(404, "Customer not found")
    customer["balance"] = await compute_customer_balance(customer.get("store_id"), customer_id)
    return customer


@api.patch("/customers/{customer_id}")
async def update_customer(customer_id: str, body: CustomerUpdate, store_id: Optional[str] = None,
                          user=Depends(require("sell"))):
    updates = {k: v.strip() if isinstance(v, str) else v for k, v in body.dict().items() if v is not None}
    if not updates:
        return await get_customer(customer_id, store_id, user)
    sid = resolve_store(user, store_id, require_write=True)
    if updates.get("phone"):
        dupe = await db.customers.find_one({"store_id": sid, "phone": updates["phone"], "id": {"$ne": customer_id}})
        if dupe:
            raise HTTPException(400, "A customer with this phone number already exists")
    r = await db.customers.update_one({"store_id": sid, "id": customer_id}, {"$set": updates})
    if r.matched_count == 0:
        raise HTTPException(404, "Customer not found")
    return await get_customer(customer_id, sid, user)


async def _customer_ledger_data(customer_id: str, sid: Optional[str]) -> Dict[str, Any]:
    entries = await db.customer_ledger.find({"store_id": sid, "customer_id": customer_id}, {"_id": 0}) \
        .sort("at", -1).to_list(2000)
    balance = 0.0
    for e in reversed(entries):
        amt = e.get("amount") or 0
        balance += amt if e.get("type") == "sale" else -amt
        e["running_balance"] = round(balance, 2)
    return {"balance": round(balance, 2), "entries": entries}


@api.get("/customers/{customer_id}/ledger")
async def get_customer_ledger(customer_id: str, store_id: Optional[str] = None, user=Depends(get_current_user)):
    customer = await db.customers.find_one(sq(user, {"id": customer_id}, store_id), {"_id": 0})
    if not customer:
        raise HTTPException(404, "Customer not found")
    data = await _customer_ledger_data(customer_id, customer.get("store_id"))
    return {"customer": customer, **data}


@api.get("/customers/{customer_id}/ledger/excel")
async def get_customer_ledger_excel(customer_id: str, store_id: Optional[str] = None, user=Depends(get_current_user)):
    customer = await db.customers.find_one(sq(user, {"id": customer_id}, store_id), {"_id": 0})
    if not customer:
        raise HTTPException(404, "Customer not found")
    data = await _customer_ledger_data(customer_id, customer.get("store_id"))
    rows = [["Date", "Type", "Part Number", "Note", "Amount", "Running Balance", "By"]]
    for e in data["entries"]:
        rows.append([e.get("at", ""), e.get("type", ""), e.get("part_number", "") or "",
                    e.get("note", "") or "", e.get("amount") or 0, e["running_balance"], e.get("by", "")])
    summary = [["Customer", customer["name"]], ["Phone", customer["phone"]], ["Balance", data["balance"]]]
    fname = f"ledger_{customer['name'].replace(' ', '_')}.xlsx"
    return _xlsx_response(fname, {"Customer": summary, "Transactions": rows})


@api.post("/customers/{customer_id}/payments")
async def record_customer_payment(customer_id: str, body: CustomerPaymentIn, store_id: Optional[str] = None,
                                  user=Depends(require("sell"))):
    if body.amount is None or body.amount <= 0:
        raise HTTPException(400, "Payment amount must be greater than 0")
    sid = resolve_store(user, store_id, require_write=True)
    customer = await db.customers.find_one({"store_id": sid, "id": customer_id})
    if not customer:
        raise HTTPException(404, "Customer not found")
    entry = {
        "id": new_id(), "store_id": sid, "customer_id": customer_id, "type": "payment",
        "amount": body.amount, "note": body.note or "", "by": user["username"], "at": now_iso(),
    }
    await db.customer_ledger.insert_one(dict(entry))
    entry.pop("_id", None)
    return {"ok": True, "entry": entry, "balance": await compute_customer_balance(sid, customer_id)}


# ---------------- Vendor Directory (Supplier Records) ----------------
# A plain directory, not a ledger — no balance/credit tracking like Customers
# above (vendors are who parts are bought FROM; nothing here records what's
# owed to them). Same CRUD shape and phone-based dedup as Customers though,
# since both are "a person/business record with a phone number" at heart.
@api.post("/vendors")
async def create_vendor(body: VendorIn, store_id: Optional[str] = None, user=Depends(require("buy"))):
    name = body.name.strip()
    phone = body.phone.strip()
    if not name or not phone:
        raise HTTPException(400, "Name and phone are required")
    sid = resolve_store(user, store_id, require_write=True)
    # One record per phone number per store — same reasoning as Customers:
    # avoid the same real vendor fragmenting into two untracked records.
    if await db.vendors.find_one({"store_id": sid, "phone": phone}):
        raise HTTPException(400, "A vendor with this phone number already exists")
    doc = {
        "id": new_id(), "store_id": sid, "name": name, "phone": phone,
        "address": body.address or "", "notes": body.notes or "",
        "created_at": now_iso(), "created_by": user["username"],
    }
    await db.vendors.insert_one(dict(doc))
    doc.pop("_id", None)
    return doc


@api.get("/vendors")
async def list_vendors(q: Optional[str] = None, phone: Optional[str] = None,
                       store_id: Optional[str] = None, user=Depends(get_current_user)):
    query = sq(user, None, store_id)
    if phone:
        query["phone"] = phone.strip()
    elif q:
        rx = {"$regex": re.escape(q.strip()[:64]), "$options": "i"}
        query["$or"] = [{"name": rx}, {"phone": rx}]
    return await db.vendors.find(query, {"_id": 0}).sort("name", 1).to_list(500)


@api.get("/vendors/{vendor_id}")
async def get_vendor(vendor_id: str, store_id: Optional[str] = None, user=Depends(get_current_user)):
    vendor = await db.vendors.find_one(sq(user, {"id": vendor_id}, store_id), {"_id": 0})
    if not vendor:
        raise HTTPException(404, "Vendor not found")
    return vendor


@api.patch("/vendors/{vendor_id}")
async def update_vendor(vendor_id: str, body: VendorUpdate, store_id: Optional[str] = None,
                        user=Depends(require("buy"))):
    updates = {k: v.strip() if isinstance(v, str) else v for k, v in body.dict().items() if v is not None}
    if not updates:
        return await get_vendor(vendor_id, store_id, user)
    sid = resolve_store(user, store_id, require_write=True)
    if updates.get("phone"):
        dupe = await db.vendors.find_one({"store_id": sid, "phone": updates["phone"], "id": {"$ne": vendor_id}})
        if dupe:
            raise HTTPException(400, "A vendor with this phone number already exists")
    r = await db.vendors.update_one({"store_id": sid, "id": vendor_id}, {"$set": updates})
    if r.matched_count == 0:
        raise HTTPException(404, "Vendor not found")
    return await get_vendor(vendor_id, sid, user)


@api.delete("/vendors/{vendor_id}")
async def delete_vendor(vendor_id: str, user=Depends(require_admin)):
    vendor = await db.vendors.find_one({"id": vendor_id})
    if not vendor:
        raise HTTPException(404, "Vendor not found")
    if user.get("role") != "super_admin" and vendor.get("store_id") != user.get("store_id"):
        raise HTTPException(403, "બીજા store નું vendor delete ન કરાય")
    await db.vendors.delete_one({"id": vendor_id})
    return {"ok": True}


# ---------------- Inventory ----------------
async def _inventory_list(user: dict, condition: Optional[str], q: Optional[str], store_id: Optional[str],
                          company: Optional[str], category: Optional[str],
                          date_from: Optional[str], date_to: Optional[str]) -> List[Dict[str, Any]]:
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


@api.get("/inventory")
async def inventory(condition: Optional[str] = None, q: Optional[str] = None, store_id: Optional[str] = None,
                    company: Optional[str] = None, category: Optional[str] = None,
                    date_from: Optional[str] = None, date_to: Optional[str] = None,
                    user=Depends(get_current_user)):
    return await _inventory_list(user, condition, q, store_id, company, category, date_from, date_to)


@api.get("/inventory/excel")
async def inventory_excel(condition: Optional[str] = None, q: Optional[str] = None, store_id: Optional[str] = None,
                          company: Optional[str] = None, category: Optional[str] = None,
                          date_from: Optional[str] = None, date_to: Optional[str] = None,
                          user=Depends(get_current_user)):
    units = await _inventory_list(user, condition, q, store_id, company, category, date_from, date_to)
    rows = [["Part Number", "Name", "Company", "Category", "Condition", "Location", "Added By", "Added At"]]
    for u in units:
        loc = u.get("location") or {}
        loc_str = " -> ".join(str(loc[k]) for k in ("rack", "shelf", "box", "position") if loc.get(k))
        rows.append([u["part_number"], u.get("part_name", ""), u.get("company", ""), u.get("category", ""),
                    u.get("condition", ""), loc_str, u.get("added_by", ""), u.get("created_at", "")])
    return _xlsx_response("inventory.xlsx", {"Stock": rows})


@api.get("/inventory/low-stock")
async def low_stock_parts(store_id: Optional[str] = None, user=Depends(get_current_user)):
    """Parts whose current stock_count is at or below their configured
    low_stock_threshold. Mirrors compute_limit's per-part-then-live-count
    approach, just applied across all of a store's thresholded parts at once.
    """
    query = sq(user, {"low_stock_enabled": True}, store_id)
    parts = await db.parts.find(query, {"_id": 0}).to_list(1000)
    out = []
    for p in parts:
        threshold = p.get("low_stock_threshold")
        if threshold is None:
            continue
        stock_count = await db.stock.count_documents(
            {"store_id": p.get("store_id"), "part_number": p["part_number"], "sold": {"$ne": True}})
        if stock_count <= threshold:
            out.append({
                "part_number": p["part_number"], "name": p.get("name", ""),
                "company": p.get("company", "") or "All", "category": p.get("category", "") or "Uncategorized",
                "stock_count": stock_count, "low_stock_threshold": threshold,
            })
    out.sort(key=lambda x: x["stock_count"])
    return out


@api.get("/inventory/location-check")
async def inventory_location_check(
    part_number: str,
    current_store_name: Optional[str] = None,
    current_wall: Optional[str] = None,
    current_rack_name: Optional[str] = None,
    current_is_open_floor: Optional[bool] = None,
    current_carton_number: Optional[str] = None,
    current_shelf_level: Optional[str] = None,
    store_id: Optional[str] = None, user=Depends(get_current_user),
):
    """Look up a part's assigned (shelf/rack) location and flag a mismatch against
    the structured `current_*` location — the location the caller is physically
    scanning/checking from right now (passed in by the client; nothing here can
    observe that on its own). If active units of this part disagree on
    assigned_location, that's reported too, since it's itself a data-integrity
    problem worth surfacing.
    """
    pn = part_number.strip()
    if not pn:
        raise HTTPException(400, "Part number required")
    query = sq(user, {"part_number": pn, "sold": {"$ne": True}}, store_id)
    units = await db.stock.find(query, {"_id": 0, "assigned_location": 1}).to_list(2000)
    by_key: Dict[Any, Dict[str, Any]] = {}
    for u in units:
        loc = u.get("assigned_location")
        if loc:
            by_key[_location_key(loc)] = loc
    distinct = list(by_key.values())
    assigned_location = distinct[0] if len(distinct) == 1 else None
    cur = _normalize_location_fields(
        current_store_name, current_wall, current_rack_name,
        current_is_open_floor, current_carton_number, current_shelf_level,
    )
    mismatch = bool(cur and assigned_location and _location_key(cur) != _location_key(assigned_location))
    return {
        "part_number": pn,
        "assigned_location": assigned_location,
        "current_location": cur,
        "location_mismatch": mismatch,
        "units_total": len(units),
        "units_with_location": sum(1 for u in units if u.get("assigned_location")),
        "inconsistent_locations": distinct if len(distinct) > 1 else None,
    }


@api.get("/inventory/unlinked-stock")
async def inventory_unlinked_stock(store_id: Optional[str] = None, user=Depends(require_admin)):
    """Active stock units with no traceable purchase record: neither a `buy`
    transaction referencing the unit (the normal /buy path) nor the `adjusted`
    flag set (the tracked /stock/adjust admin path). A unit failing both must
    have been inserted outside the app's normal flow (direct DB write, import,
    migration) — flag it for proper entry.
    """
    query = sq(user, {"sold": {"$ne": True}, "adjusted": {"$ne": True}}, store_id)
    units = await db.stock.find(query, {"_id": 0}).sort("created_at", -1).to_list(5000)
    if not units:
        return []
    unit_ids = [u["id"] for u in units]
    txn_query = sq(user, {"type": "buy", "unit_id": {"$in": unit_ids}}, store_id)
    linked_ids = {t["unit_id"] async for t in db.transactions.find(txn_query, {"_id": 0, "unit_id": 1})}
    return [u for u in units if u["id"] not in linked_ids]


# ---------------- Stock adjust / delete (Admin only) ----------------
class StockAdjustIn(BaseModel):
    part_number: str
    delta: int = 0
    condition: Optional[str] = None
    location: Optional[Dict[str, Any]] = None
    assigned_location: Optional[AssignedLocationIn] = None
    override: bool = False


@api.post("/stock/adjust")
async def stock_adjust(body: StockAdjustIn, store_id: Optional[str] = None, user=Depends(require_admin)):
    pn = body.part_number.strip()
    sid = resolve_store(user, store_id, require_write=True)
    part = await db.parts.find_one({"store_id": sid, "part_number": pn})
    if not part:
        raise HTTPException(404, "Part મળ્યો નથી")
    delta = int(body.delta)
    added, removed = 0, 0
    limit_reached = False
    latest_limit: Optional[dict] = None
    if delta > 0:
        # A "+1" here (Part Detail / Inventory quick-adjust steppers) reads to
        # a shop owner exactly like buying another unit — it was previously
        # inserting units straight into db.stock with NO purchase-limit check
        # at all, a second, unguarded door onto the same shelf /buy locks
        # down. That let total stock end up over a configured limit with no
        # warning whenever this path (rather than /buy) was used to add
        # units — reported as "purchase limit allows buying more than
        # intended" even though /buy itself enforces it correctly. Fixed by
        # running the exact same per-unit compute_limit() check under the
        # same per-part mutex /buy uses, so a concurrent /buy and
        # /stock/adjust on the same part can't both slip through either.
        # Stops as soon as the limit is hit and reports what actually got
        # added, rather than raising mid-way through an already-partially-
        # applied delta>1 call (the UI only ever sends delta=1, but the field
        # itself doesn't promise that).
        for _ in range(delta):
            if not await _acquire_part_buy_lock(sid, pn):
                raise HTTPException(503, "Server busy processing this part — try again")
            try:
                latest_limit = await compute_limit(sid, pn)
                if (latest_limit["limit_enabled"] and latest_limit["remaining"] is not None
                        and latest_limit["remaining"] <= 0 and not body.override):
                    limit_reached = True
                    break
                unit = {"id": new_id(), "store_id": sid, "part_number": pn, "condition": body.condition or "Unknown",
                        "location": body.location or {}, "assigned_location": _location_from_model(body.assigned_location),
                        "photos": [], "barcode": "",
                        "sold": False, "created_at": now_iso(), "added_by": user["username"], "adjusted": True,
                        "overridden": bool(body.override and latest_limit.get("status") == "STOP")}
                await db.stock.insert_one(dict(unit))
                added += 1
            finally:
                await _release_part_buy_lock(sid, pn)
        if added:
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
    return {"ok": True, "added": added, "removed": removed, "remaining_stock": remaining,
            "limit_reached": limit_reached, "limit": latest_limit}


class StockUnitEditIn(BaseModel):
    assigned_location: Optional[AssignedLocationIn] = None


@api.patch("/stock/unit/{unit_id}")
async def edit_unit(unit_id: str, body: StockUnitEditIn, user=Depends(require("buy"))):
    # Setting/updating a unit's assigned_location is a normal part of the buy ->
    # store-arrangement workflow, so anyone who can buy can arrange it — not just
    # admins. This endpoint only ever touches assigned_location; deleting a unit
    # (DELETE /stock/unit/{id}) remains admin-only.
    unit = await db.stock.find_one({"id": unit_id})
    if not unit:
        raise HTTPException(404, "Unit મળ્યું નથી")
    if user.get("role") != "super_admin" and unit.get("store_id") != user.get("store_id"):
        raise HTTPException(403, "તમારા store નું unit જ edit કરી શકાય")
    new_loc = _location_from_model(body.assigned_location)
    await db.stock.update_one({"id": unit_id}, {"$set": {"assigned_location": new_loc}})
    updated = await db.stock.find_one({"id": unit_id}, {"_id": 0})
    return {"ok": True, "unit": updated}


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


# ---------------- Damaged / Return Tracking ----------------
# db.stock_adjustments is the audit trail for all three record types below
# (customer_return / damaged_stock / vendor_return) — one place to list past
# records, regardless of what (if anything) it did to db.stock. The actual
# stock-state change reuses the SAME mechanism /stock/adjust's delta<0 branch
# already established for "removed without a sale": sold=True plus a
# removed_reason discriminator. Every existing "is this unit available"
# query in the app already filters on plain `sold != True` (see compute_limit,
# compute_low_stock, /inventory, /inventory/low-stock, /stats, etc.) — so a
# unit marked damaged this way is automatically excluded from stock counts,
# low-stock counts and purchase-limit counts everywhere, with no separate
# "damaged" filter needed at any of those call sites.
async def _find_unit(sid: Optional[str], pn: str, unit_id: Optional[str], sold: Optional[bool]) -> Optional[dict]:
    query: Dict[str, Any] = {"store_id": sid, "part_number": pn}
    if sold is not None:
        query["sold"] = sold if sold else {"$ne": True}
    if unit_id:
        query["id"] = unit_id
    return await db.stock.find_one(query)


@api.post("/returns/customer")
async def customer_return(body: CustomerReturnIn, store_id: Optional[str] = None, user=Depends(require("sell"))):
    pn = body.part_number.strip().upper()
    if not pn:
        raise HTTPException(400, "Part number required")
    condition = (body.condition or "").strip().lower()
    if condition not in ("good", "damaged"):
        raise HTTPException(400, "condition must be 'good' or 'damaged'")
    sid = resolve_store(user, store_id, require_write=True)
    if body.customer_id and not await db.customers.find_one({"store_id": sid, "id": body.customer_id}, {"_id": 0}):
        raise HTTPException(404, "Customer not found")
    # A previously-sold unit of this part, if one can be traced (linking to
    # the original sale is optional per the spec, not required).
    unit = await _find_unit(sid, pn, body.unit_id, sold=True)
    if unit:
        if condition == "good":
            await db.stock.update_one({"id": unit["id"]},
                                      {"$set": {"sold": False},
                                       "$unset": {"sold_at": "", "sold_by": "", "removed_reason": ""}})
        else:
            await db.stock.update_one({"id": unit["id"]}, {"$set": {"removed_reason": "customer_return_damaged"}})
    elif condition == "good":
        # No traceable prior sale — still honor "goes back into sellable
        # stock" by adding a fresh available unit, the same way /buy creates
        # one when the part has no existing stock document yet.
        cat = await db.catalog.find_one({"part_number": pn}, {"_id": 0}) or {}
        part = await db.parts.find_one({"store_id": sid, "part_number": pn}, {"_id": 0})
        if not part:
            part = {
                "id": new_id(), "store_id": sid, "part_number": pn, "company": cat.get("company", "All") or "All",
                "name": cat.get("name", "") or "", "category": cat.get("category", "") or "",
                "compatible_vehicles": cat.get("compatible_vehicles", []) or [], "variant": cat.get("variant", "") or "",
                "year": "", "old_number": "", "new_number": "", "barcode": "", "sticker_color": "",
                "technical_info": "", "photos": [], "source": "CustomerReturn", "verification_status": "Unverified",
                "created_at": now_iso(), "created_by": user["username"], "purchase_limit": None, "limit_enabled": False,
            }
            await db.parts.insert_one(dict(part))
            await upsert_catalog(pn, part, sid)
        unit = {
            "id": new_id(), "store_id": sid, "part_number": pn, "condition": "Working",
            "location": {}, "assigned_location": None, "photos": [], "barcode": "",
            "sold": False, "created_at": now_iso(), "added_by": user["username"], "source": "CustomerReturn",
        }
        await db.stock.insert_one(dict(unit))
    unit_id = unit["id"] if unit else None
    record = {
        "id": new_id(), "store_id": sid, "type": "customer_return", "part_number": pn,
        "unit_id": unit_id, "customer_id": body.customer_id, "condition": condition,
        "note": (body.note or "").strip(), "by": user["username"], "at": now_iso(),
    }
    await db.stock_adjustments.insert_one(dict(record))
    await db.transactions.insert_one({"id": new_id(), "store_id": sid, "type": "customer_return",
                                      "part_number": pn, "unit_id": unit_id, "by": user["username"], "at": now_iso()})
    record.pop("_id", None)
    return {"ok": True, "record": record}


@api.post("/returns/damaged")
async def mark_damaged(body: DamagedStockIn, store_id: Optional[str] = None, user=Depends(require("buy"))):
    pn = body.part_number.strip().upper()
    if not pn:
        raise HTTPException(400, "Part number required")
    sid = resolve_store(user, store_id, require_write=True)
    unit = await _find_unit(sid, pn, body.unit_id, sold=False)
    if not unit:
        raise HTTPException(409, detail={"code": "NO_STOCK", "message": "No in-stock unit found for this part"})
    await db.stock.update_one({"id": unit["id"]}, {"$set": {
        "sold": True, "sold_at": now_iso(), "sold_by": user["username"], "removed_reason": "damaged",
    }})
    record = {
        "id": new_id(), "store_id": sid, "type": "damaged_stock", "part_number": pn,
        "unit_id": unit["id"], "condition": "damaged", "note": (body.note or "").strip(),
        "by": user["username"], "at": now_iso(),
    }
    await db.stock_adjustments.insert_one(dict(record))
    await db.transactions.insert_one({"id": new_id(), "store_id": sid, "type": "damaged_stock",
                                      "part_number": pn, "unit_id": unit["id"], "by": user["username"], "at": now_iso()})
    remaining = await db.stock.count_documents({"store_id": sid, "part_number": pn, "sold": {"$ne": True}})
    record.pop("_id", None)
    return {"ok": True, "record": record, "remaining_stock": remaining}


@api.post("/returns/vendor")
async def vendor_return(body: VendorReturnIn, store_id: Optional[str] = None, user=Depends(require("buy"))):
    pn = body.part_number.strip().upper()
    if not pn:
        raise HTTPException(400, "Part number required")
    sid = resolve_store(user, store_id, require_write=True)
    unit = await _find_unit(sid, pn, body.unit_id, sold=False)
    if not unit:
        raise HTTPException(409, detail={"code": "NO_STOCK", "message": "No in-stock unit found for this part"})
    if body.vendor_id and not await db.vendors.find_one({"store_id": sid, "id": body.vendor_id}, {"_id": 0}):
        raise HTTPException(404, "Vendor not found")
    # Leaves the shop entirely — same as an admin's /stock/unit/{id} delete,
    # not a "sold" state, so it doesn't stay around in any damaged list either.
    await db.stock.delete_one({"id": unit["id"]})
    record = {
        "id": new_id(), "store_id": sid, "type": "vendor_return", "part_number": pn,
        "unit_id": unit["id"], "vendor_id": body.vendor_id, "note": (body.reason or "").strip(),
        "by": user["username"], "at": now_iso(),
    }
    await db.stock_adjustments.insert_one(dict(record))
    await db.transactions.insert_one({"id": new_id(), "store_id": sid, "type": "vendor_return", "part_number": pn,
                                      "unit_id": unit["id"], "vendor_id": body.vendor_id,
                                      "by": user["username"], "at": now_iso()})
    remaining = await db.stock.count_documents({"store_id": sid, "part_number": pn, "sold": {"$ne": True}})
    record.pop("_id", None)
    return {"ok": True, "record": record, "remaining_stock": remaining}


@api.get("/returns")
async def list_returns(type: Optional[str] = None, store_id: Optional[str] = None, user=Depends(get_current_user)):
    query = sq(user, None, store_id)
    if type:
        query["type"] = type
    return await db.stock_adjustments.find(query, {"_id": 0}).sort("at", -1).to_list(500)


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
    pn = body.part_number.strip().upper()
    if not pn:
        raise HTTPException(400, "Part number required")
    sid = resolve_store(user, store_id, require_write=True)
    # If a part already exists under a punctuation variant of this same part
    # number (e.g. an earlier /buy recorded it as scanned, without the hyphen
    # the admin is typing here), attach the limit to THAT document instead of
    # upserting a new one — otherwise the limit lands on an untracked
    # duplicate that no purchase will ever match, and silently never applies.
    # _find_canonical_part also merges/dedupes if more than one variant
    # already exists (see its docstring) instead of picking one arbitrarily.
    existing = await _find_canonical_part({"store_id": sid}, pn)
    if existing:
        pn = existing["part_number"]
    # Previously required a matching part to already exist (created only by a
    # first /buy) and raised 404 otherwise — so setting a limit on a part before
    # ever buying it silently never took effect. Upsert instead: a limit set
    # proactively now actually persists and applies to the very first purchase.
    now = now_iso()
    await db.parts.update_one(
        {"store_id": sid, "part_number": pn},
        {
            "$set": {"purchase_limit": body.limit, "limit_enabled": body.enabled},
            "$setOnInsert": {
                "id": new_id(), "store_id": sid, "part_number": pn, "company": "All",
                "name": "", "category": "", "compatible_vehicles": [], "variant": "",
                "year": "", "old_number": "", "new_number": "", "barcode": "",
                "sticker_color": "", "technical_info": "", "photos": [], "source": "Limit",
                "verification_status": "Unverified", "created_at": now, "created_by": user["username"],
            },
        },
        upsert=True,
    )
    return await compute_limit(sid, pn)


@api.get("/limits/{part_number}")
async def get_part_limit(part_number: str, store_id: Optional[str] = None, user=Depends(get_current_user)):
    return await compute_limit(resolve_store(user, store_id), part_number)


@api.post("/limits/low-stock")
async def set_low_stock_threshold(body: LowStockIn, store_id: Optional[str] = None,
                                  user=Depends(require("manage_limits"))):
    pn = body.part_number.strip().upper()
    if not pn:
        raise HTTPException(400, "Part number required")
    sid = resolve_store(user, store_id, require_write=True)
    # Same punctuation-variant reuse as /limits/part above — don't spawn an
    # untracked duplicate part document if one already exists under a
    # differently-formatted (but identical) part number, and self-heal if
    # more than one already does (see _find_canonical_part's docstring).
    existing = await _find_canonical_part({"store_id": sid}, pn)
    if existing:
        pn = existing["part_number"]
    # Same upsert pattern as /limits/part: don't require the part to already
    # exist (created only by a first /buy) — a threshold set proactively
    # should still apply from the very first purchase onward.
    now = now_iso()
    await db.parts.update_one(
        {"store_id": sid, "part_number": pn},
        {
            "$set": {"low_stock_threshold": body.threshold, "low_stock_enabled": body.enabled},
            "$setOnInsert": {
                "id": new_id(), "store_id": sid, "part_number": pn, "company": "All",
                "name": "", "category": "", "compatible_vehicles": [], "variant": "",
                "year": "", "old_number": "", "new_number": "", "barcode": "",
                "sticker_color": "", "technical_info": "", "photos": [], "source": "LowStock",
                "verification_status": "Unverified", "created_at": now, "created_by": user["username"],
            },
        },
        upsert=True,
    )
    return await compute_low_stock(sid, pn)


@api.get("/limits/low-stock/{part_number}")
async def get_low_stock_threshold(part_number: str, store_id: Optional[str] = None,
                                  user=Depends(get_current_user)):
    return await compute_low_stock(resolve_store(user, store_id), part_number)


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


def _gemini_image_part(b64: str) -> dict:
    """Wrap a raw base64 image payload as a Gemini `inlineData` content part,
    guessing the MIME type from the (unpadded) base64 data header."""
    head = b64[:12]
    if head.startswith("iVBORw0KGgo"):
        mime = "image/png"
    elif head.startswith("/9j/"):
        mime = "image/jpeg"
    elif head.startswith("UklGR"):
        mime = "image/webp"
    elif head.startswith("R0lGOD"):
        mime = "image/gif"
    else:
        mime = "image/jpeg"
    return {"inlineData": {"mimeType": mime, "data": b64}}


def _gemini_generate(parts: List[dict], *, system_text: Optional[str] = None,
                      model: Optional[str] = None, timeout: int = 90,
                      json_response: bool = True) -> str:
    """Call the Gemini API directly over plain HTTP (no SDK) and return the
    model's text output. Raises Exception with a friendly message on failure."""
    if not GEMINI_API_KEY:
        raise Exception("Gemini API key not configured")
    url = f"{GEMINI_API_BASE}/{model or GEMINI_MODEL}:generateContent"
    payload: Dict[str, Any] = {"contents": [{"parts": parts}]}
    if system_text:
        payload["systemInstruction"] = {"parts": [{"text": system_text}]}
    if json_response:
        payload["generationConfig"] = {"responseMimeType": "application/json"}
    resp = requests.post(url, params={"key": GEMINI_API_KEY}, json=payload, timeout=timeout)
    if resp.status_code != 200:
        try:
            err = resp.json().get("error", {}).get("message") or resp.text
        except Exception:
            err = resp.text
        raise Exception(f"Gemini API error ({resp.status_code}): {str(err)[:300]}")
    data = resp.json()
    candidates = data.get("candidates") or []
    if not candidates:
        reason = (data.get("promptFeedback") or {}).get("blockReason")
        raise Exception(f"Gemini returned no output{f' (blocked: {reason})' if reason else ''}")
    out_parts = ((candidates[0].get("content") or {}).get("parts")) or []
    text = "".join(p.get("text", "") for p in out_parts if "text" in p)
    if not text:
        raise Exception("Gemini returned an empty response")
    return text


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

    if not GEMINI_API_KEY:
        raise Exception("No AI provider available")
    text = await run_in_threadpool(
        _gemini_generate, [{"text": prompt}],
        system_text=GEMINI_SYSTEM, model=GEMINI_MODEL, timeout=90,
    )
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

    if not GEMINI_API_KEY:
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


# ---------------- Profit / Margin Report (Admin) ----------------
async def _compute_profit_report(sid: Optional[str], date_from: Optional[str],
                                 date_to: Optional[str], part_number: Optional[str]) -> Dict[str, Any]:
    """Per-unit profit = that unit's sell price minus the price it was bought
    at, joined by unit_id (the same db.stock document's id — set on both its
    buy and sell transaction, since both are recorded against `unit["id"]` at
    the time). Not part_number-average cost: two units of the same part can
    have been bought at different prices (a very real case for used auto
    parts), so joining by the specific physical unit is the only way to get
    an actually-correct number per sale, not an approximation.

    Older purchases recorded with no price (BuyIn.price is optional, and was
    only made mandatory-in-practice much later) have nothing to subtract —
    those sales are counted in revenue/units_sold but excluded from cost/
    profit and separately tallied as unknown_cost_units, never silently
    treated as zero cost (which would inflate profit) or dropped entirely
    (which would hide that a real sale happened).

    Factored out of the /reports/profit endpoint so the Excel export
    (/reports/profit/excel below) computes the exact same numbers instead of
    a second, independently-maintained copy of this logic drifting out of
    sync with it — see the total_profit comment below for why that
    duplication risk matters here specifically.
    """
    base: Dict[str, Any] = {"store_id": sid} if sid is not None else {}
    q: Dict[str, Any] = {**base, "type": "sell"}
    if part_number:
        q["part_number"] = part_number.strip()
    if date_from or date_to:
        rng: Dict[str, Any] = {}
        if date_from:
            rng["$gte"] = date_from
        if date_to:
            rng["$lte"] = date_to + "T23:59:59"
        q["at"] = rng
    sells = await db.transactions.find(q, {"_id": 0}).sort("at", -1).to_list(5000)

    by_part: Dict[str, Dict[str, Any]] = {}
    units_sold = 0
    units_unknown_cost = 0
    total_revenue = 0.0
    total_cost = 0.0
    # Summed independently from total_revenue - total_cost on purpose: those
    # two totals cover different sets of units (revenue includes unknown-cost
    # sales, cost doesn't), so subtracting them would silently treat an
    # unknown cost as zero and inflate profit — exactly what this endpoint
    # exists to avoid. total_profit only ever adds a unit's own
    # revenue-minus-cost, and only when both sides of that are known.
    total_profit = 0.0

    for s in sells:
        pn = s.get("part_number") or "?"
        units_sold += 1
        revenue = s.get("price")
        buy_txn = None
        if s.get("unit_id"):
            buy_txn = await db.transactions.find_one(
                {**base, "type": "buy", "unit_id": s["unit_id"]}, {"_id": 0, "price": 1})
        cost = buy_txn.get("price") if buy_txn else None
        known_cost = cost is not None and revenue is not None

        row = by_part.setdefault(pn, {
            "part_number": pn, "units_sold": 0, "revenue": 0.0, "cost": 0.0,
            "profit": 0.0, "unknown_cost_units": 0,
        })
        row["units_sold"] += 1
        if revenue is not None:
            row["revenue"] += revenue
            total_revenue += revenue
        if known_cost:
            row["cost"] += cost
            row["profit"] += revenue - cost
            total_cost += cost
            total_profit += revenue - cost
        else:
            row["unknown_cost_units"] += 1
            units_unknown_cost += 1

    # Attach part name for display, same enrichment list_transactions does.
    for pn, row in by_part.items():
        p = await db.parts.find_one({**base, "part_number": pn}, {"_id": 0, "name": 1})
        row["part_name"] = (p or {}).get("name", "")
        row["revenue"] = round(row["revenue"], 2)
        row["cost"] = round(row["cost"], 2)
        row["profit"] = round(row["profit"], 2)

    parts_list = sorted(by_part.values(), key=lambda r: r["profit"], reverse=True)
    return {
        "date_from": date_from, "date_to": date_to,
        "summary": {
            "units_sold": units_sold,
            "total_revenue": round(total_revenue, 2),
            "total_cost": round(total_cost, 2),
            "total_profit": round(total_profit, 2),
            "units_with_unknown_cost": units_unknown_cost,
        },
        "by_part": parts_list,
    }


@api.get("/reports/profit")
async def profit_report(store_id: Optional[str] = None, date_from: Optional[str] = None,
                        date_to: Optional[str] = None, part_number: Optional[str] = None,
                        user=Depends(require_admin)):
    sid = resolve_store(user, store_id)
    return await _compute_profit_report(sid, date_from, date_to, part_number)


@api.get("/reports/profit/excel")
async def profit_report_excel(store_id: Optional[str] = None, date_from: Optional[str] = None,
                              date_to: Optional[str] = None, part_number: Optional[str] = None,
                              user=Depends(require_admin)):
    sid = resolve_store(user, store_id)
    report = await _compute_profit_report(sid, date_from, date_to, part_number)
    s = report["summary"]
    summary_rows = [
        ["Metric", "Value"],
        ["Units sold", s["units_sold"]],
        ["Total revenue", s["total_revenue"]],
        ["Total cost", s["total_cost"]],
        ["Total profit", s["total_profit"]],
        ["Units with unknown cost", s["units_with_unknown_cost"]],
        ["Date from", date_from or "(all time)"],
        ["Date to", date_to or "(all time)"],
    ]
    part_rows = [["Part Number", "Name", "Units Sold", "Revenue", "Cost", "Profit", "Unknown Cost Units"]]
    for r in report["by_part"]:
        part_rows.append([r["part_number"], r.get("part_name", ""), r["units_sold"],
                          r["revenue"], r["cost"], r["profit"], r["unknown_cost_units"]])
    return _xlsx_response("profit_report.xlsx", {"Summary": summary_rows, "By Part": part_rows})


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


def _xlsx_response(filename: str, sheets: Dict[str, List[List[Any]]]) -> Response:
    """Build an .xlsx file from {sheet_name: [[header...], [row...], ...]} and
    return it as a downloadable response. openpyxl is already a backend
    dependency (this helper generalizes what /backup/excel did inline below)
    — no frontend spreadsheet library needed at all: the client just
    downloads/shares these bytes the same way it already does for
    /backup/excel, so every "Export to Excel" button in the app reuses this
    one code path.
    """
    from openpyxl import Workbook
    import io

    wb = Workbook()
    wb.remove(wb.active)
    for sheet_name, rows in sheets.items():
        ws = wb.create_sheet(sheet_name[:31])  # Excel sheet-name length limit
        for row in rows:
            ws.append(row)
    buf = io.BytesIO()
    wb.save(buf)
    buf.seek(0)
    return Response(
        content=buf.read(),
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": f"attachment; filename={filename}"},
    )


@api.get("/backup/excel")
async def backup_excel(user=Depends(require_admin)):
    sid = resolve_store(user, None, require_write=True)
    cols = {
        "Parts": ("parts", ["part_number", "name", "company", "category", "variant", "verification_status"]),
        "Stock": ("stock", ["part_number", "condition", "sold", "added_by", "created_at"]),
        "Transactions": ("transactions", ["type", "part_number", "price", "by", "at"]),
    }
    sheets: Dict[str, List[List[Any]]] = {}
    for sheet_name, (col, fields) in cols.items():
        docs = await db[col].find({"store_id": sid}, {"_id": 0}).to_list(100000)
        sheets[sheet_name] = [fields] + [[str(d.get(c, "")) for c in fields] for d in docs]
    return _xlsx_response("store_backup.xlsx", sheets)


# ---------------- AI Sticker Scanner (Gemini vision) ----------------
class StickerScanReq(BaseModel):
    image_base64: str  # raw base64 (no data: prefix)


STICKER_SCAN_PROMPT = """Read this product label/sticker (it may be rotated/upside-down — read it UPRIGHT). Return ONLY strict JSON (no markdown):
{
  "aspect": <sticker width/height ratio, e.g. 1.6>,
  "part_number": "<the single main part number, exact characters>",
  "lines": [ {"text": "<one text line>", "bold": <true|false>} ],
  "code": {"type": "qr"|"barcode"|"datamatrix"},
  "logo": {"x": <0-100>, "y": <0-100>, "w": <0-100>, "h": <0-100>}
}
Rules:
- "lines": EVERY visible text line in top-to-bottom reading order, exact text (include the HYUNDAI KIA MOTORS / brand line as text too). No coordinates.
- "code": the type of the main 2D/1D code on the label. Use "qr" if unsure.
- "logo": bounding box (% of the UPRIGHT sticker) that tightly covers the manufacturer BRAND logo(s) at the top (e.g. the Hyundai + Kia logos together). null if there is no logo.
- JSON only, no extra text."""


@api.post("/scan-sticker")
async def scan_sticker(req: StickerScanReq, user=Depends(get_current_user)):
    if not GEMINI_API_KEY:
        raise HTTPException(500, "AI key not configured")
    b64 = req.image_base64
    if "," in b64 and b64.strip().startswith("data:"):
        b64 = b64.split(",", 1)[1]
    try:
        text = await run_in_threadpool(
            _gemini_generate,
            [{"text": STICKER_SCAN_PROMPT}, _gemini_image_part(b64)],
            system_text="You extract structured JSON from product label images. Output JSON only.",
            model=GEMINI_VISION_MODEL, timeout=90,
        )
    except Exception as e:
        logger.exception("sticker scan failed")
        raise HTTPException(502, f"AI scan failed: {e}")

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
    data.setdefault("aspect", 1.6)
    data.setdefault("part_number", "")
    data.setdefault("lines", [])
    data.setdefault("code", None)
    data.setdefault("logo", None)
    return data


# ---------------- Saved Sticker Templates (store-scoped) ----------------
class StickerTemplateReq(BaseModel):
    name: str
    bg_data_url: str
    aspect: float = 1.4
    pn_box: Optional[dict] = None
    part_number: str = ""
    company: Optional[str] = ""


@api.post("/sticker-templates")
async def save_sticker_template(req: StickerTemplateReq, user=Depends(get_current_user)):
    sid = resolve_store(user, None)
    pn = (req.part_number or "").strip()
    now = datetime.now(timezone.utc).isoformat()
    # Auto-register the part number so it appears in the common catalog (and the
    # store's own parts library when the saver owns a store).
    if pn:
        await upsert_catalog(pn, {"company": (req.company or "").strip(), "name": ""}, sid)
        if sid and not await db.parts.find_one({"store_id": sid, "part_number": pn}):
            cat = await db.catalog.find_one({"part_number": pn}, {"_id": 0}) or {}
            await db.parts.insert_one({
                "id": new_id(), "store_id": sid, "part_number": pn,
                "name": cat.get("name", ""), "company": (req.company or cat.get("company") or "All"),
                "category": cat.get("category", ""), "compatible_vehicles": cat.get("compatible_vehicles", []),
                "variant": cat.get("variant", ""), "year": cat.get("year", ""),
                "verification_status": "Unverified", "source": "Sticker",
                "created_at": now, "created_by": user["username"],
                "purchase_limit": None, "limit_enabled": False,
            })
    # One sticker per part number (per store): update existing instead of duplicating.
    existing = await db.sticker_templates.find_one({"store_id": sid, "part_number": pn}) if pn else None
    if existing:
        await db.sticker_templates.update_one(
            {"id": existing["id"]},
            {"$set": {"name": req.name or "Sticker", "bg_data_url": req.bg_data_url,
                      "aspect": req.aspect, "pn_box": req.pn_box, "updated_at": now}},
        )
        return await db.sticker_templates.find_one({"id": existing["id"]}, {"_id": 0})
    doc = {
        "id": uuid.uuid4().hex,
        "store_id": sid,
        "name": req.name or "Sticker",
        "bg_data_url": req.bg_data_url,
        "aspect": req.aspect,
        "pn_box": req.pn_box,
        "part_number": pn,
        "created_at": now,
    }
    await db.sticker_templates.insert_one(doc)
    doc.pop("_id", None)
    return doc


@api.get("/sticker-templates")
async def list_sticker_templates(store_id: Optional[str] = None, user=Depends(get_current_user)):
    return await db.sticker_templates.find(sq(user, None, store_id), {"_id": 0}).sort("created_at", -1).to_list(200)


@api.delete("/sticker-templates/{tid}")
async def delete_sticker_template(tid: str, user=Depends(get_current_user)):
    await db.sticker_templates.delete_one(sq(user, {"id": tid}))
    return {"ok": True}


# ---------------- Company-wise Sticker Formats (store-scoped, one per company) ----------------
class CompanyFormatReq(BaseModel):
    company: str
    template: dict


@api.post("/company-formats")
async def save_company_format(req: CompanyFormatReq, user=Depends(get_current_user)):
    sid = resolve_store(user, None)
    comp = (req.company or "").strip()
    if not comp:
        raise HTTPException(400, "Company required")
    now = datetime.now(timezone.utc).isoformat()
    await db.company_formats.update_one(
        {"store_id": sid, "company": comp},
        {"$set": {"template": req.template, "updated_at": now},
         "$setOnInsert": {"id": uuid.uuid4().hex, "store_id": sid, "company": comp, "created_at": now}},
        upsert=True,
    )
    doc = await db.company_formats.find_one({"store_id": sid, "company": comp}, {"_id": 0})
    return doc


@api.get("/company-formats")
async def list_company_formats(store_id: Optional[str] = None, user=Depends(get_current_user)):
    return await db.company_formats.find(sq(user, None, store_id), {"_id": 0}).sort("company", 1).to_list(100)


@api.delete("/company-formats/{company}")
async def delete_company_format(company: str, user=Depends(get_current_user)):
    await db.company_formats.delete_one(sq(user, {"company": company}))
    return {"ok": True}


# ---------------- Company Logo Library (store-scoped) ----------------
class LogoReq(BaseModel):
    name: str
    data_url: str


@api.post("/logos")
async def save_logo(req: LogoReq, user=Depends(get_current_user)):
    sid = resolve_store(user, None)
    doc = {"id": uuid.uuid4().hex, "store_id": sid, "name": req.name or "Logo",
           "data_url": req.data_url, "created_at": datetime.now(timezone.utc).isoformat()}
    await db.logos.insert_one(doc)
    doc.pop("_id", None)
    return doc


@api.get("/logos")
async def list_logos(store_id: Optional[str] = None, user=Depends(get_current_user)):
    return await db.logos.find(sq(user, None, store_id), {"_id": 0}).sort("created_at", -1).to_list(100)


@api.delete("/logos/{lid}")
async def delete_logo(lid: str, user=Depends(get_current_user)):
    await db.logos.delete_one(sq(user, {"id": lid}))
    return {"ok": True}




app.include_router(api)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
