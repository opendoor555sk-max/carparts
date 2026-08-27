"""BYO-Key Google Custom Search + friendly errors + cache/DB-first tests."""
import os
import uuid
import pytest
import requests

BASE_URL = os.environ.get('EXPO_PUBLIC_BACKEND_URL', 'https://part-number-first.preview.emergentagent.com').rstrip('/')
API = f"{BASE_URL}/api"


@pytest.fixture(scope="module")
def admin_token():
    r = requests.post(f"{API}/auth/login", json={"username": "abdul", "password": "Salam@123"}, timeout=30)
    assert r.status_code == 200, r.text
    return r.json()["access_token"]


@pytest.fixture(scope="module")
def H(admin_token):
    return {"Authorization": f"Bearer {admin_token}"}


# ---------- Settings save + secure retrieval ----------
class TestSettings:
    def test_save_and_reflect(self, H):
        r = requests.post(f"{API}/auth/settings", json={"google_api_key": "AIzaTESTKEY", "google_cx": "testcx"}, headers=H, timeout=30)
        assert r.status_code == 200, r.text
        body = r.json()
        assert body["ok"] is True
        assert body["has_google_key"] is True
        assert body["google_cx"] == "testcx"

    def test_me_has_google_key(self, H):
        r = requests.get(f"{API}/auth/me", headers=H, timeout=30)
        assert r.status_code == 200
        assert r.json()["has_google_key"] is True

    def test_settings_raw_key_not_returned(self, H):
        r = requests.get(f"{API}/auth/settings", headers=H, timeout=30)
        assert r.status_code == 200
        b = r.json()
        assert b["has_google_key"] is True
        assert b["google_cx"] == "testcx"
        # raw key MUST NOT be exposed
        assert "google_api_key" not in b
        assert "AIzaTESTKEY" not in str(b)


# ---------- Friendly NO_KEY error ----------
class TestNoKey:
    def test_no_key_error(self, H):
        # clear first
        r0 = requests.post(f"{API}/auth/settings", json={"google_api_key": "", "google_cx": ""}, headers=H, timeout=30)
        assert r0.status_code == 200
        assert r0.json()["has_google_key"] is False

        r = requests.post(f"{API}/search/web", json={"part_number": "TESTPN1"}, headers=H, timeout=30)
        assert r.status_code == 400
        j = r.json()
        d = j.get("detail", {})
        assert isinstance(d, dict), f"expected structured detail, got: {j}"
        assert d.get("code") == "NO_KEY"
        assert d.get("message"), "friendly message missing"


# ---------- Friendly bad-key error (must not 500) ----------
class TestBadKey:
    def test_bad_key_error_graceful(self, H):
        rs = requests.post(f"{API}/auth/settings", json={"google_api_key": "BADKEY", "google_cx": "badcx"}, headers=H, timeout=30)
        assert rs.status_code == 200

        r = requests.post(f"{API}/search/web", json={"part_number": "TESTPN1BAD"}, headers=H, timeout=60)
        # Must NOT be an unhandled 500. Accept 4xx or 502 with structured detail.
        assert r.status_code in (400, 401, 403, 429, 502), f"expected friendly error, got {r.status_code}: {r.text}"
        try:
            j = r.json()
        except Exception:
            pytest.fail(f"non-JSON body: {r.text[:400]}")
        d = j.get("detail")
        # detail should be a dict OR a string — but no raw stack trace
        text = str(d)
        assert "Traceback" not in text
        if isinstance(d, dict):
            assert d.get("code") in ("QUOTA", "SEARCH_ERR", "NO_KEY")
            assert d.get("message")


# ---------- Cache / DB-first ----------
class TestDbFirstCache:
    @pytest.fixture(scope="class")
    def verified_pn(self, H):
        pn = f"TESTPN-CACHE-{uuid.uuid4().hex[:8].upper()}"
        # Create part directly then mark verified via ai/research approve path.
        # Simplest: POST /api/parts then PATCH via ai approval isn't available directly;
        # use /api/ai/research then approve. But research needs LLM.
        # Simpler still: POST /api/parts and PATCH is not enough (needs verification_status). Use ai/research then approve.
        # Instead, create part with POST /api/parts and then directly update verification via approve endpoint requires research doc.
        # Fastest path: POST /api/parts (creates Unverified), then run ai/research (LLM may fail) — skip that.
        # Use a Mongo trick? No. Approve endpoint updates part_updates including verification_status:'Verified' — but needs a research doc.
        # Use POST /api/parts + PATCH part to set fields, then hit a manual verify... none exposed.
        # Try ai/research: it may succeed OR fail. If succeed -> approve.
        # If fails, we skip.
        rc = requests.post(f"{API}/parts", json={
            "part_number": pn,
            "company": "Hyundai+Kia",
            "name": "TestCacheBCM",
            "category": "Body Control",
            "compatible_vehicles": ["Hyundai Creta"],
            "variant": "SX",
            "source": "TestSeed",
        }, headers=H, timeout=30)
        if rc.status_code != 200:
            pytest.skip(f"part create failed: {rc.text}")
        # Now trigger ai/research (will hit DB-FIRST path? No, part is Unverified — will go to AI). Skip: instead try approving via a research document created by ai research call. If AI fails, skip cache test.
        rr = requests.post(f"{API}/ai/research", json={"part_number": pn, "company": "Hyundai+Kia"}, headers=H, timeout=120)
        if rr.status_code != 200:
            pytest.skip(f"ai/research failed (LLM unavailable): {rr.status_code} {rr.text[:200]}")
        rid = rr.json().get("id")
        ap = requests.post(f"{API}/ai/research/{rid}/approve",
                           json={"compatible_vehicles": ["Hyundai Creta"], "company": "Hyundai+Kia", "variant": "SX", "name": "TestCacheBCM", "category": "Body Control"},
                           headers=H, timeout=60)
        assert ap.status_code == 200, ap.text
        return pn

    def test_cache_hits_without_google_key(self, H, verified_pn):
        # ensure NO key set
        rs = requests.post(f"{API}/auth/settings", json={"google_api_key": "", "google_cx": ""}, headers=H, timeout=30)
        assert rs.status_code == 200
        assert rs.json()["has_google_key"] is False

        r = requests.post(f"{API}/search/web", json={"part_number": verified_pn}, headers=H, timeout=30)
        assert r.status_code == 200, r.text
        j = r.json()
        assert j.get("cached") is True
        assert "Hyundai Creta" in (j.get("models") or [])


# ---------- Master DB tagging on /buy ----------
class TestBuyTagging:
    def test_buy_records_created_by_and_at(self, H):
        pn = f"TESTPN-TAG-{uuid.uuid4().hex[:8].upper()}"
        r = requests.post(f"{API}/buy", json={
            "part_number": pn, "company": "Maruti", "name": "TagTestPart",
            "category": "Sensors", "condition": "Working",
        }, headers=H, timeout=30)
        assert r.status_code == 200, r.text

        g = requests.get(f"{API}/parts/{pn}", headers=H, timeout=30)
        assert g.status_code == 200
        j = g.json()
        assert j.get("created_by") == "abdul"
        assert j.get("created_at"), "created_at missing"


# ---------- Regression ----------
class TestRegression:
    def test_categories_total_73(self, H):
        r = requests.get(f"{API}/categories", headers=H, timeout=30)
        assert r.status_code == 200
        assert r.json().get("total") == 73

    def test_login_works(self):
        r = requests.post(f"{API}/auth/login", json={"username": "abdul", "password": "Salam@123"}, timeout=30)
        assert r.status_code == 200
        assert "access_token" in r.json()

    def test_buy_increases_and_sell_decreases(self, H):
        pn = f"TESTPN-REG-{uuid.uuid4().hex[:8].upper()}"
        b1 = requests.post(f"{API}/buy", json={"part_number": pn, "condition": "Working"}, headers=H, timeout=30)
        assert b1.status_code == 200
        g1 = requests.get(f"{API}/parts/{pn}", headers=H, timeout=30).json()
        assert g1["stock_count"] == 1

        b2 = requests.post(f"{API}/buy", json={"part_number": pn, "condition": "Working"}, headers=H, timeout=30)
        assert b2.status_code == 200
        g2 = requests.get(f"{API}/parts/{pn}", headers=H, timeout=30).json()
        assert g2["stock_count"] == 2

        s1 = requests.post(f"{API}/sell", json={"part_number": pn}, headers=H, timeout=30)
        assert s1.status_code == 200
        g3 = requests.get(f"{API}/parts/{pn}", headers=H, timeout=30).json()
        assert g3["stock_count"] == 1


# ---------- Cleanup: ensure admin ends with NO google key set ----------
def test_zzz_cleanup_clear_google_key(H):
    r = requests.post(f"{API}/auth/settings", json={"google_api_key": "", "google_cx": ""}, headers=H, timeout=30)
    assert r.status_code == 200
    assert r.json()["has_google_key"] is False
