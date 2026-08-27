"""
Tests for the review request:
 - Purchase limit enforcement (POST /api/limits/part + POST /api/buy override=false -> 409 LIMIT_REACHED)
 - Batch multiple buy on server-side (override=false path)
 - Change password (POST /api/auth/change-password) — wrong current, short new, mismatch
 - Photo upload endpoint (POST /api/upload) and photos array stored on stock unit
 - GPS stored on stock unit location.gps and returned in /api/parts/{pn}
"""
import io
import os
import uuid
import requests
import pytest
from pathlib import Path
from dotenv import load_dotenv

load_dotenv("/app/backend/.env")
load_dotenv("/app/frontend/.env")

BASE_URL = os.environ.get("EXPO_PUBLIC_BACKEND_URL", "").rstrip("/")


# ---- Helpers ----
def _login(username: str, password: str):
    r = requests.post(f"{BASE_URL}/api/auth/login",
                      json={"username": username, "password": password}, timeout=30)
    return r


def _auth_headers(token: str):
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


@pytest.fixture(scope="module")
def admin_token():
    r = _login("abdul", "Salam@123")
    assert r.status_code == 200, f"admin login failed: {r.status_code} {r.text}"
    return r.json()["access_token"]


@pytest.fixture(scope="module")
def h(admin_token):
    return _auth_headers(admin_token)


# ------------------------------------------------------------
# 1) PURCHASE LIMIT ENFORCEMENT
# ------------------------------------------------------------
class TestPurchaseLimit:
    def test_limit_reached_returns_409(self, h):
        pn = f"TEST_LIMIT_{uuid.uuid4().hex[:8].upper()}"

        # first, create the part by doing an initial buy (override=true)
        r0 = requests.post(f"{BASE_URL}/api/buy", headers=h,
                           json={"part_number": pn, "condition": "Unknown", "override": True}, timeout=30)
        assert r0.status_code == 200, r0.text

        # apply per-part limit=1 enabled=true
        rlim = requests.post(f"{BASE_URL}/api/limits/part", headers=h,
                             json={"part_number": pn, "limit": 1, "enabled": True}, timeout=30)
        assert rlim.status_code == 200, rlim.text
        payload = rlim.json()
        assert payload.get("limit_enabled") is True
        assert payload.get("remaining") is not None
        # remaining after 1 buy with limit 1 -> 0
        assert payload["remaining"] <= 0, payload

        # 2nd buy WITHOUT override -> 409 LIMIT_REACHED
        r2 = requests.post(f"{BASE_URL}/api/buy", headers=h,
                           json={"part_number": pn, "condition": "Unknown", "override": False}, timeout=30)
        assert r2.status_code == 409, f"expected 409 got {r2.status_code}: {r2.text}"
        j = r2.json()
        # FastAPI wraps HTTPException.detail under "detail"
        detail = j.get("detail", j)
        assert detail.get("code") == "LIMIT_REACHED", detail
        assert "DO NOT BUY" in detail.get("message", "") or "limit" in detail.get("message", "").lower()

        # override=True should still succeed
        r3 = requests.post(f"{BASE_URL}/api/buy", headers=h,
                           json={"part_number": pn, "condition": "Unknown", "override": True}, timeout=30)
        assert r3.status_code == 200, r3.text

    def test_batch_buy_calls_use_override_false_and_are_blocked(self, h):
        """Simulate what /batch-buy.web.tsx does: sends override=false for each part."""
        pn = f"TEST_BATCH_LIMIT_{uuid.uuid4().hex[:8].upper()}"
        # create with 1 buy
        requests.post(f"{BASE_URL}/api/buy", headers=h,
                      json={"part_number": pn, "condition": "Unknown", "override": True}, timeout=30)
        # set limit=1
        requests.post(f"{BASE_URL}/api/limits/part", headers=h,
                      json={"part_number": pn, "limit": 1, "enabled": True}, timeout=30)
        # batch calls (override=false) should now all get 409
        for _ in range(3):
            rb = requests.post(f"{BASE_URL}/api/buy", headers=h,
                               json={"part_number": pn, "condition": "Unknown",
                                     "location": {"gps": "21.1702,72.8311"}, "override": False}, timeout=30)
            assert rb.status_code == 409, rb.text
        # stock still == 1
        rg = requests.get(f"{BASE_URL}/api/parts/{pn}", headers=h, timeout=30)
        assert rg.status_code == 200
        assert rg.json().get("stock_count") == 1


# ------------------------------------------------------------
# 2) CHANGE PASSWORD (admin abdul must remain Salam@123)
# ------------------------------------------------------------
class TestChangePassword:
    def test_wrong_current_returns_400(self, h):
        r = requests.post(f"{BASE_URL}/api/auth/change-password", headers=h,
                          json={"current_password": "WRONG_XYZ", "new_password": "Newpass1"}, timeout=30)
        assert r.status_code == 400, r.text
        # Gujarati message
        assert "વર્તમાન" in (r.json().get("detail", "") or "")

    def test_short_new_returns_422(self, h):
        r = requests.post(f"{BASE_URL}/api/auth/change-password", headers=h,
                          json={"current_password": "Salam@123", "new_password": "abc"}, timeout=30)
        assert r.status_code == 422, r.text

    def test_same_as_old_returns_422(self, h):
        r = requests.post(f"{BASE_URL}/api/auth/change-password", headers=h,
                          json={"current_password": "Salam@123", "new_password": "Salam@123"}, timeout=30)
        assert r.status_code == 422, r.text

    def test_valid_change_and_revert(self):
        """End-to-end: change abdul's password and change it back to Salam@123."""
        tok = _login("abdul", "Salam@123").json()["access_token"]
        h1 = _auth_headers(tok)
        temp = "Temp@9876"
        r = requests.post(f"{BASE_URL}/api/auth/change-password", headers=h1,
                          json={"current_password": "Salam@123", "new_password": temp}, timeout=30)
        assert r.status_code == 200, r.text
        # login with new password
        r2 = _login("abdul", temp)
        assert r2.status_code == 200, r2.text
        # revert
        tok2 = r2.json()["access_token"]
        h2 = _auth_headers(tok2)
        r3 = requests.post(f"{BASE_URL}/api/auth/change-password", headers=h2,
                           json={"current_password": temp, "new_password": "Salam@123"}, timeout=30)
        assert r3.status_code == 200, r3.text
        # confirm login with original
        assert _login("abdul", "Salam@123").status_code == 200


# ------------------------------------------------------------
# 3) PHOTO UPLOAD + BUY WITH PHOTOS + GPS
# ------------------------------------------------------------
class TestUploadAndBuyWithPhotos:
    def test_upload_returns_path(self, admin_token):
        # tiny jpeg (1x1)
        jpg = bytes.fromhex(
            "FFD8FFE000104A46494600010100000100010000FFDB004300080606070605080707"
            "070909080A0C140D0C0B0B0C1912130F141D1A1F1E1D1A1C1C20242E2720222C231C"
            "1C2837292C30313434341F27393D38323C2E333432FFC0000B080001000101011100"
            "FFC4001F0000010501010101010100000000000000000102030405060708090A0BFF"
            "C400B5100002010303020403050504040000017D01020300041105122131410613516107"
            "227114328191A1082342B1C11552D1F02433627282090A161718191A25262728292A34"
            "35363738393A434445464748494A535455565758595A636465666768696A737475767778"
            "797A838485868788898A92939495969798999AA2A3A4A5A6A7A8A9AAB2B3B4B5B6B7"
            "B8B9BAC2C3C4C5C6C7C8C9CAD2D3D4D5D6D7D8D9DAE1E2E3E4E5E6E7E8E9EAF1F2F3F4"
            "F5F6F7F8F9FAFFDA0008010100003F00FBD0FFD9"
        )
        files = {"file": ("test.jpg", io.BytesIO(jpg), "image/jpeg")}
        headers = {"Authorization": f"Bearer {admin_token}"}
        r = requests.post(f"{BASE_URL}/api/upload", headers=headers, files=files, timeout=60)
        assert r.status_code == 200, r.text
        body = r.json()
        assert "path" in body and body["path"]
        assert "url" in body and body["url"].startswith("/api/files/")

    def test_buy_persists_photos_and_gps(self, h, admin_token):
        pn = f"TEST_GPSPHOTO_{uuid.uuid4().hex[:8].upper()}"
        # upload a photo
        jpg = bytes.fromhex(
            "FFD8FFE000104A46494600010100000100010000FFDB004300080606070605080707"
            "070909080A0C140D0C0B0B0C1912130F141D1A1F1E1D1A1C1C20242E2720222C231C"
            "1C2837292C30313434341F27393D38323C2E333432FFC0000B080001000101011100"
            "FFC4001F0000010501010101010100000000000000000102030405060708090A0BFF"
            "C400B5100002010303020403050504040000017D01020300041105122131410613516107"
            "227114328191A1082342B1C11552D1F02433627282090A161718191A25262728292A34"
            "35363738393A434445464748494A535455565758595A636465666768696A737475767778"
            "797A838485868788898A92939495969798999AA2A3A4A5A6A7A8A9AAB2B3B4B5B6B7"
            "B8B9BAC2C3C4C5C6C7C8C9CAD2D3D4D5D6D7D8D9DAE1E2E3E4E5E6E7E8E9EAF1F2F3F4"
            "F5F6F7F8F9FAFFDA0008010100003F00FBD0FFD9"
        )
        r_up = requests.post(f"{BASE_URL}/api/upload",
                             headers={"Authorization": f"Bearer {admin_token}"},
                             files={"file": ("t.jpg", io.BytesIO(jpg), "image/jpeg")}, timeout=60)
        assert r_up.status_code == 200
        photo_path = r_up.json()["path"]

        # buy with photos + gps
        gps = "21.170200,72.831100"
        r = requests.post(f"{BASE_URL}/api/buy", headers=h, json={
            "part_number": pn, "condition": "Unknown",
            "location": {"gps": gps, "rack": "R1"},
            "photos": [photo_path],
            "override": True,
        }, timeout=30)
        assert r.status_code == 200, r.text

        # GET /api/parts/{pn} and verify unit has photos + gps
        rg = requests.get(f"{BASE_URL}/api/parts/{pn}", headers=h, timeout=30)
        assert rg.status_code == 200, rg.text
        detail = rg.json()
        units = detail.get("units") or []
        assert len(units) >= 1
        u = units[0]
        assert (u.get("location") or {}).get("gps") == gps, u
        assert photo_path in (u.get("photos") or []), u
