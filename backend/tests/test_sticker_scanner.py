"""Tests for AI Sticker Scanner + Sticker Template CRUD (store-scoped).

Covers the review items:
  1. POST /api/scan-sticker returns 200 with keys aspect/part_number/lines/code/logo.
  2. POST /api/scan-sticker requires auth (401 without Bearer).
  3. sticker-templates: POST saves + returns id, GET lists, DELETE removes.
  4. Store isolation: template saved by store A is not visible to store B.
"""
import base64
import io
import os
import time
import uuid

import pytest
import requests

BASE_URL = os.environ.get("EXPO_PUBLIC_BACKEND_URL", "https://part-number-first.preview.emergentagent.com").rstrip("/")
API = f"{BASE_URL}/api"


# --- helpers -----------------------------------------------------------------
def _login(username: str, password: str) -> str:
    r = requests.post(f"{API}/auth/login", json={"username": username, "password": password}, timeout=15)
    assert r.status_code == 200, f"login {username} → {r.status_code} {r.text}"
    return r.json()["token"]


def _auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


def _synth_jpeg_b64() -> str:
    """Build a tiny synthetic JPEG (32x32 red square with black rectangle).
    Uses only stdlib — no PIL dependency assumed.
    Falls back to a hard-coded 1x1 JPEG if PIL missing."""
    try:
        from PIL import Image, ImageDraw
        img = Image.new("RGB", (240, 160), "white")
        d = ImageDraw.Draw(img)
        d.rectangle([10, 10, 230, 40], fill="black")
        d.rectangle([10, 60, 230, 90], outline="black", width=2)
        d.rectangle([10, 110, 90, 150], fill="black")  # fake QR block
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=70)
        return base64.b64encode(buf.getvalue()).decode()
    except Exception:
        # 1x1 white pixel JPEG
        MIN_JPEG = bytes.fromhex(
            "ffd8ffe000104a46494600010100000100010000ffdb0043000806060706050807070709090a0c140d0c0b"
            "0b0c1912130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e33"
            "3432ffc0000b080001000101011100ffc4001f0000010501010101010100000000000000000102030405060"
            "708090a0bffc400b5100002010303020403050504040000017d01020300041105122131410613516107227114"
            "328191a1082342b1c11552d1f02433627282090a161718191a25262728292a3435363738393a434445464748"
            "494a535455565758595a636465666768696a737475767778797a838485868788898a92939495969798999aa2"
            "a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae1e2e3e4e5e6e7e8e9"
            "eaf1f2f3f4f5f6f7f8f9faffda0008010100003f00fbfcffd9"
        )
        return base64.b64encode(MIN_JPEG).decode()


# --- fixtures ----------------------------------------------------------------
@pytest.fixture(scope="module")
def teststore1_token():
    return _login("teststore1", "Test@123")


@pytest.fixture(scope="module")
def super_token():
    return _login("abdul", "Salam@123")


@pytest.fixture(scope="module")
def storeB():
    """Register a fresh isolated store to check cross-store visibility."""
    uname = f"TEST_stickerB_{uuid.uuid4().hex[:6]}"
    r = requests.post(f"{API}/auth/register", json={
        "store_name": f"TEST_StickerB_{uname}",
        "name": "Sticker B Owner",
        "username": uname,
        "password": "Test@1234",
        "contact": "9999999999",
    }, timeout=15)
    assert r.status_code == 200, f"register storeB failed: {r.status_code} {r.text}"
    data = r.json()
    return {"token": data["token"], "store_id": data["user"]["store_id"], "username": uname}


# --- 1. /scan-sticker requires auth -----------------------------------------
class TestScanStickerAuth:
    def test_no_token_returns_401_or_403(self):
        r = requests.post(f"{API}/scan-sticker",
                          json={"image_base64": _synth_jpeg_b64()},
                          headers={"Content-Type": "application/json"},
                          timeout=15)
        assert r.status_code in (401, 403), f"expected 401/403, got {r.status_code} {r.text[:200]}"


# --- 2. /scan-sticker happy path (structure only) ---------------------------
class TestScanStickerShape:
    def test_returns_expected_json_shape(self, teststore1_token):
        b64 = _synth_jpeg_b64()
        t0 = time.time()
        r = requests.post(f"{API}/scan-sticker",
                          json={"image_base64": b64},
                          headers=_auth(teststore1_token),
                          timeout=30)
        elapsed = time.time() - t0
        assert r.status_code == 200, f"scan-sticker → {r.status_code} {r.text[:300]}"
        data = r.json()
        # keys
        for k in ("aspect", "part_number", "lines", "code", "logo"):
            assert k in data, f"missing key {k} in response: {data}"
        # types
        assert isinstance(data["aspect"], (int, float)), f"aspect not number: {data['aspect']}"
        assert isinstance(data["part_number"], str)
        assert isinstance(data["lines"], list)
        for ln in data["lines"]:
            assert isinstance(ln, dict) and "text" in ln, f"invalid line entry: {ln}"
        # code may be None or dict; if dict must have 'type'
        if data["code"] is not None:
            assert isinstance(data["code"], dict) and "type" in data["code"], f"code shape wrong: {data['code']}"
        # logo may be None or dict with x/y/w/h
        if data["logo"] is not None:
            assert isinstance(data["logo"], dict)
            for k in ("x", "y", "w", "h"):
                assert k in data["logo"], f"logo missing {k}: {data['logo']}"
        # perf hint (soft): report only, do not fail
        print(f"scan-sticker latency: {elapsed:.2f}s")


# --- 3. sticker-templates CRUD ----------------------------------------------
class TestStickerTemplateCRUD:
    def test_create_list_delete(self, teststore1_token):
        payload = {
            "name": f"TEST_tpl_{uuid.uuid4().hex[:6]}",
            "bg_data_url": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
            "aspect": 1.5,
            "pn_box": {"x": 10, "y": 10, "w": 40, "h": 20},
            "part_number": "TEST_PN_001",
        }
        r = requests.post(f"{API}/sticker-templates", json=payload, headers=_auth(teststore1_token), timeout=15)
        assert r.status_code == 200, f"create tpl → {r.status_code} {r.text}"
        created = r.json()
        assert "id" in created and created["id"]
        assert created["name"] == payload["name"]
        assert created["part_number"] == "TEST_PN_001"
        assert created["aspect"] == 1.5
        tid = created["id"]

        # list — must contain the created id
        r2 = requests.get(f"{API}/sticker-templates", headers=_auth(teststore1_token), timeout=15)
        assert r2.status_code == 200
        ids = [t["id"] for t in r2.json()]
        assert tid in ids, f"created id {tid} missing from list {ids[:5]}..."

        # delete
        r3 = requests.delete(f"{API}/sticker-templates/{tid}", headers=_auth(teststore1_token), timeout=15)
        assert r3.status_code == 200

        # verify gone
        r4 = requests.get(f"{API}/sticker-templates", headers=_auth(teststore1_token), timeout=15)
        assert tid not in [t["id"] for t in r4.json()]


# --- 4. Store isolation ------------------------------------------------------
class TestStickerTemplateStoreIsolation:
    def test_storeA_template_not_visible_to_storeB(self, teststore1_token, storeB):
        # A creates
        name_a = f"TEST_isoA_{uuid.uuid4().hex[:6]}"
        r = requests.post(f"{API}/sticker-templates",
                          json={"name": name_a, "bg_data_url": "data:image/png;base64,AA==", "aspect": 1.4, "part_number": "PN_A"},
                          headers=_auth(teststore1_token), timeout=15)
        assert r.status_code == 200
        tid_a = r.json()["id"]

        try:
            # B lists — MUST NOT see A's template
            rB = requests.get(f"{API}/sticker-templates", headers=_auth(storeB["token"]), timeout=15)
            assert rB.status_code == 200
            ids_b = [t["id"] for t in rB.json()]
            assert tid_a not in ids_b, f"LEAK: storeB sees storeA template! got ids {ids_b}"
            names_b = [t.get("name") for t in rB.json()]
            assert name_a not in names_b, f"LEAK: storeB sees storeA name {name_a}"

            # A still sees own
            rA = requests.get(f"{API}/sticker-templates", headers=_auth(teststore1_token), timeout=15)
            assert tid_a in [t["id"] for t in rA.json()]

            # B cannot delete A's template
            rDel = requests.delete(f"{API}/sticker-templates/{tid_a}", headers=_auth(storeB["token"]), timeout=15)
            # delete returns 200 {ok:true} regardless — verify A's still there
            rA2 = requests.get(f"{API}/sticker-templates", headers=_auth(teststore1_token), timeout=15)
            assert tid_a in [t["id"] for t in rA2.json()], "storeB was able to delete storeA's template!"
        finally:
            # cleanup A's template
            requests.delete(f"{API}/sticker-templates/{tid_a}", headers=_auth(teststore1_token), timeout=15)

    def test_super_admin_can_scope_to_store(self, super_token, teststore1_token):
        # Create template in teststore1
        r = requests.post(f"{API}/sticker-templates",
                          json={"name": "TEST_super_scope", "bg_data_url": "data:image/png;base64,AA==",
                                "aspect": 1.4, "part_number": "PN_S"},
                          headers=_auth(teststore1_token), timeout=15)
        assert r.status_code == 200
        tid = r.json()["id"]
        try:
            # super admin (no store filter) sees all — should include this
            rAll = requests.get(f"{API}/sticker-templates", headers=_auth(super_token), timeout=15)
            assert rAll.status_code == 200
            assert tid in [t["id"] for t in rAll.json()]
        finally:
            requests.delete(f"{API}/sticker-templates/{tid}", headers=_auth(teststore1_token), timeout=15)
