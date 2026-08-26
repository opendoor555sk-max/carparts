import os
import pytest
import requests
from pathlib import Path
from dotenv import load_dotenv

load_dotenv(Path(__file__).parent.parent / ".env")
# Also load frontend env for public URL
load_dotenv("/app/frontend/.env")

BASE_URL = os.environ.get("EXPO_PUBLIC_BACKEND_URL", "").rstrip("/")


@pytest.fixture(scope="session")
def base_url():
    assert BASE_URL, "EXPO_PUBLIC_BACKEND_URL not configured"
    return BASE_URL


@pytest.fixture(scope="session")
def admin_token(base_url):
    r = requests.post(f"{base_url}/api/auth/login",
                      json={"username": "abdul", "password": "Salam@123"}, timeout=30)
    assert r.status_code == 200, f"admin login failed: {r.status_code} {r.text}"
    return r.json()["access_token"]


@pytest.fixture(scope="session")
def admin_headers(admin_token):
    return {"Authorization": f"Bearer {admin_token}", "Content-Type": "application/json"}


@pytest.fixture(scope="session")
def staff_creds():
    return {"username": "teststaff1", "password": "Staff@123", "name": "Test Staff"}


@pytest.fixture(scope="session")
def staff_token(base_url, admin_headers, staff_creds):
    # ensure user (idempotent). Try create, ignore if exists.
    payload = {**staff_creds, "role": "staff",
               "permissions": ["search", "buy", "sell", "requirement", "buying_trip", "manage_parts"]}
    requests.post(f"{base_url}/api/admin/users", headers=admin_headers, json=payload, timeout=30)
    # login
    r = requests.post(f"{base_url}/api/auth/login",
                      json={"username": staff_creds["username"], "password": staff_creds["password"]}, timeout=30)
    if r.status_code != 200:
        pytest.skip(f"staff login failed: {r.text}")
    return r.json()["access_token"]


@pytest.fixture(scope="session")
def staff_headers(staff_token):
    return {"Authorization": f"Bearer {staff_token}", "Content-Type": "application/json"}
