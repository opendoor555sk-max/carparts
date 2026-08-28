"""Backend tests for iteration 16 review:

Covers:
1. POST /api/scan-sticker (AI vision) — shape only, not OCR accuracy
2. Company Logo Library CRUD (store-scoped) /api/logos
3. Sticker Templates CRUD (store-scoped) /api/sticker-templates
4. Multi-tenant isolation for logos + sticker-templates
5. GET /api/categories (groups length 22) and /api/companies (contains key brands)
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


def _login(username: str, password: str) -> str:
    r = requests.post(f"{API}/auth/login", json={"username": username, "password": password}, timeout=15)
    assert r.status_code == 200, f"login {username} → {r.status_code} {r.text}"
    body = r.json()
    tok = body.get("access_token") or body.get("token")
    assert tok, f"no token in login response: {body}"
    return tok


def _auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


def _synth_jpeg_b64() -> str:
    """Real-looking label image with text + a fake QR block."""
    try:
        from PIL import Image, ImageDraw, ImageFont
        img = Image.new("RGB", (480, 300), "white")
        d = ImageDraw.Draw(img)
        # Company name bold-ish
        try:
            font_big = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 26)
            font_med = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 20)
        except Exception:
            font_big = ImageFont.load_default()
            font_med = ImageFont.load_default()
        d.text((20, 15), "MARUTI SUZUKI", fill="black", font=font_big)
        d.text((20, 55), "Part No: 95400-T7110", fill="black", font=font_med)
        d.text((20, 90), "GENUINE PART", fill="black", font=font_med)
        d.text((20, 125), "MODEL: SWIFT DZIRE", fill="black", font=font_med)
        # Fake QR block bottom-right
        d.rectangle([360, 180, 460, 280], fill="black")
        d.rectangle([375, 195, 445, 265], fill="white")
        d.rectangle([390, 210, 430, 250], fill="black")
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=80)
        return base64.b64encode(buf.getvalue()).decode()
    except Exception:
        # 1x1 white pixel fallback
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
def token_a() -> str:
    return _login("teststore1", "Test@123")


@pytest.fixture(scope="module")
def store_b() -> dict:
    uname = f"TEST_isoB_{uuid.uuid4().hex[:6]}"
    r = requests.post(f"{API}/auth/register", json={
        "store_name": f"TEST_IsoB_{uname}",
        "name": "Iso B Owner",
        "username": uname,
        "password": "Test@1234",
        "contact": "9999999999",
    }, timeout=20)
    assert r.status_code == 200, f"register storeB → {r.status_code} {r.text}"
    body = r.json()
    tok = body.get("access_token") or body.get("token")
    assert tok
    return {"token": tok, "username": uname}


# --- 1. Meta endpoints -------------------------------------------------------
class TestMeta:
    def test_categories_has_22_groups(self, token_a):
        r = requests.get(f"{API}/categories", headers=_auth(token_a), timeout=15)
        assert r.status_code == 200, f"{r.status_code} {r.text[:200]}"
        data = r.json()
        assert "groups" in data, f"missing groups: {data}"
        assert isinstance(data["groups"], list)
        assert len(data["groups"]) == 22, f"expected 22 groups, got {len(data['groups'])}"
        # each group must have group name + items
        for g in data["groups"]:
            assert "group" in g and "items" in g, f"bad group entry: {g}"
            assert isinstance(g["items"], list) and len(g["items"]) > 0

    def test_companies_contains_brands(self, token_a):
        r = requests.get(f"{API}/companies", headers=_auth(token_a), timeout=15)
        assert r.status_code == 200
        data = r.json()
        assert isinstance(data, list)
        for brand in ("Maruti Suzuki", "Hyundai", "Tata"):
            assert brand in data, f"{brand} missing from companies: {data}"


# --- 2. /scan-sticker --------------------------------------------------------
class TestScanSticker:
    def test_shape_and_keys(self, token_a):
        b64 = _synth_jpeg_b64()
        t0 = time.time()
        r = requests.post(f"{API}/scan-sticker",
                          json={"image_base64": b64},
                          headers=_auth(token_a), timeout=45)
        elapsed = time.time() - t0
        assert r.status_code == 200, f"scan-sticker → {r.status_code} {r.text[:500]}"
        data = r.json()
        for k in ("aspect", "part_number", "lines", "code", "logo"):
            assert k in data, f"missing key {k}: {data}"
        assert isinstance(data["aspect"], (int, float)), f"aspect not number: {data['aspect']!r}"
        assert isinstance(data["part_number"], str)
        assert isinstance(data["lines"], list), f"lines not list: {data['lines']!r}"
        # for a text-bearing image we expect at least one line
        assert len(data["lines"]) > 0, f"lines empty for text image: {data}"
        for ln in data["lines"]:
            assert isinstance(ln, dict) and "text" in ln, f"bad line: {ln}"
        if data["code"] is not None:
            assert isinstance(data["code"], dict) and "type" in data["code"], f"code shape: {data['code']}"
        if data["logo"] is not None:
            assert isinstance(data["logo"], dict)
            for k in ("x", "y", "w", "h"):
                assert k in data["logo"], f"logo missing {k}: {data['logo']}"
        print(f"[scan-sticker] latency={elapsed:.2f}s, lines={len(data['lines'])}, pn={data['part_number']!r}")


# --- 3. Logo library CRUD ---------------------------------------------------
class TestLogosCRUD:
    def test_create_list_delete(self, token_a):
        payload = {
            "name": f"TEST_Logo_{uuid.uuid4().hex[:6]}",
            "data_url": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
        }
        r = requests.post(f"{API}/logos", json=payload, headers=_auth(token_a), timeout=15)
        assert r.status_code == 200, f"create logo → {r.status_code} {r.text}"
        created = r.json()
        for k in ("id", "name", "data_url", "store_id"):
            assert k in created, f"logo missing field {k}: {created}"
        assert created["name"] == payload["name"]
        assert created["data_url"] == payload["data_url"]
        lid = created["id"]

        r2 = requests.get(f"{API}/logos", headers=_auth(token_a), timeout=15)
        assert r2.status_code == 200
        items = r2.json()
        assert isinstance(items, list)
        ids = [i["id"] for i in items]
        assert lid in ids, f"created logo id {lid} not in list"

        r3 = requests.delete(f"{API}/logos/{lid}", headers=_auth(token_a), timeout=15)
        assert r3.status_code == 200
        assert r3.json().get("ok") is True

        r4 = requests.get(f"{API}/logos", headers=_auth(token_a), timeout=15)
        assert lid not in [i["id"] for i in r4.json()], "deleted logo still listed"


# --- 4. Sticker Template CRUD -----------------------------------------------
class TestStickerTemplatesCRUD:
    def test_create_list_delete(self, token_a):
        name = f"TEST_T1_{uuid.uuid4().hex[:6]}"
        payload = {
            "name": name,
            "bg_data_url": '{"aspect":1.6}',
            "aspect": 1.6,
            "pn_box": None,
            "part_number": "95400-T7110",
        }
        r = requests.post(f"{API}/sticker-templates", json=payload, headers=_auth(token_a), timeout=15)
        assert r.status_code == 200, f"create tpl → {r.status_code} {r.text}"
        created = r.json()
        assert "id" in created and created["id"], f"no id: {created}"
        assert created["name"] == name
        assert created["part_number"] == "95400-T7110"
        assert created["aspect"] == 1.6
        tid = created["id"]

        r2 = requests.get(f"{API}/sticker-templates", headers=_auth(token_a), timeout=15)
        assert r2.status_code == 200
        assert tid in [t["id"] for t in r2.json()]

        r3 = requests.delete(f"{API}/sticker-templates/{tid}", headers=_auth(token_a), timeout=15)
        assert r3.status_code == 200

        r4 = requests.get(f"{API}/sticker-templates", headers=_auth(token_a), timeout=15)
        assert tid not in [t["id"] for t in r4.json()], "deleted template still listed"


# --- 5. Multi-tenant isolation ----------------------------------------------
class TestMultiTenantIsolation:
    def test_logos_isolated_between_stores(self, token_a, store_b):
        name = f"TEST_isoLogo_{uuid.uuid4().hex[:6]}"
        r = requests.post(f"{API}/logos",
                          json={"name": name, "data_url": "data:image/png;base64,AA=="},
                          headers=_auth(token_a), timeout=15)
        assert r.status_code == 200
        lid = r.json()["id"]
        try:
            # store B must not see it
            rB = requests.get(f"{API}/logos", headers=_auth(store_b["token"]), timeout=15)
            assert rB.status_code == 200
            ids_b = [i["id"] for i in rB.json()]
            names_b = [i.get("name") for i in rB.json()]
            assert lid not in ids_b, f"LEAK: storeB sees storeA logo id {lid}"
            assert name not in names_b, f"LEAK: storeB sees storeA logo name {name}"
            # store A still sees it
            rA = requests.get(f"{API}/logos", headers=_auth(token_a), timeout=15)
            assert lid in [i["id"] for i in rA.json()]
        finally:
            requests.delete(f"{API}/logos/{lid}", headers=_auth(token_a), timeout=15)

    def test_sticker_templates_isolated_between_stores(self, token_a, store_b):
        name = f"TEST_isoTpl_{uuid.uuid4().hex[:6]}"
        r = requests.post(f"{API}/sticker-templates",
                          json={"name": name, "bg_data_url": '{"aspect":1.4}',
                                "aspect": 1.4, "pn_box": None, "part_number": "PN_A"},
                          headers=_auth(token_a), timeout=15)
        assert r.status_code == 200
        tid = r.json()["id"]
        try:
            rB = requests.get(f"{API}/sticker-templates", headers=_auth(store_b["token"]), timeout=15)
            assert rB.status_code == 200
            ids_b = [t["id"] for t in rB.json()]
            names_b = [t.get("name") for t in rB.json()]
            assert tid not in ids_b, f"LEAK: storeB sees storeA template id {tid}"
            assert name not in names_b, f"LEAK: storeB sees storeA template name {name}"
        finally:
            requests.delete(f"{API}/sticker-templates/{tid}", headers=_auth(token_a), timeout=15)
