"""Phase 1: Multi-tenant (multi-store) tests
- POST /api/auth/register creates isolated store + admin
- Data isolation across stores (parts/inventory/search/stats)
- Super-admin god-view via /admin/stores
- SEC-001 (staff can't self-elevate to admin), SEC-004 (no secret leak)
- Buy/Sell/Inventory within store
"""
import os
import uuid
import time
import pytest
import requests

BASE = os.environ["EXPO_BACKEND_URL"] if "EXPO_BACKEND_URL" in os.environ else \
       "https://part-number-first.preview.emergentagent.com"
BASE = BASE.rstrip("/")
API = BASE + "/api"

SUPER_USER = "abdul"
SUPER_PW = "Salam@123"


def _post(path, json=None, token=None):
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = f"Bearer {token}"
    return requests.post(API + path, json=json, headers=h, timeout=30)


def _get(path, token=None, params=None):
    h = {}
    if token:
        h["Authorization"] = f"Bearer {token}"
    return requests.get(API + path, headers=h, params=params, timeout=30)


def _delete(path, token=None):
    h = {}
    if token:
        h["Authorization"] = f"Bearer {token}"
    return requests.delete(API + path, headers=h, timeout=30)


@pytest.fixture(scope="module")
def super_token():
    r = _post("/auth/login", {"username": SUPER_USER, "password": SUPER_PW})
    assert r.status_code == 200, r.text
    d = r.json()
    assert d["user"]["role"] == "super_admin"
    return d["access_token"]


def _register(prefix):
    suffix = uuid.uuid4().hex[:8]
    payload = {
        "store_name": f"TEST_{prefix}_{suffix}",
        "name": f"TEST Owner {prefix}",
        "username": f"test_{prefix}_{suffix}",
        "password": "Test@1234",
    }
    r = _post("/auth/register", payload)
    assert r.status_code == 200, f"register failed: {r.status_code} {r.text}"
    d = r.json()
    return payload, d


@pytest.fixture(scope="module")
def store_a():
    payload, d = _register("A")
    return {"payload": payload, "token": d["access_token"], "user": d["user"]}


@pytest.fixture(scope="module")
def store_b():
    payload, d = _register("B")
    return {"payload": payload, "token": d["access_token"], "user": d["user"]}


# ----- Register -----
class TestRegister:
    def test_register_creates_admin_of_new_store(self, store_a):
        u = store_a["user"]
        assert u["role"] == "admin"
        assert u["store_id"], "store_id should be set"
        assert u["store_name"].startswith("TEST_A_")
        assert "search" in u["permissions"] and "manage_users" in u["permissions"]

    def test_register_duplicate_username_400(self, store_a):
        p = store_a["payload"]
        r = _post("/auth/register", p)
        assert r.status_code in (400, 422)

    def test_register_short_password_422(self):
        r = _post("/auth/register", {
            "store_name": "TEST_X", "name": "X", "username": f"u_{uuid.uuid4().hex[:6]}", "password": "abc"})
        assert r.status_code == 422


# ----- Login -----
class TestLogin:
    def test_owner_can_login(self, store_a):
        p = store_a["payload"]
        r = _post("/auth/login", {"username": p["username"], "password": p["password"]})
        assert r.status_code == 200
        d = r.json()
        assert d["user"]["role"] == "admin"
        assert d["user"]["store_id"] == store_a["user"]["store_id"]

    def test_super_admin_login(self, super_token):
        # verifies fixture — via /auth/me
        r = _get("/auth/me", token=super_token)
        assert r.status_code == 200
        assert r.json()["role"] == "super_admin"


# ----- Super-admin /admin/stores -----
class TestAdminStores:
    def test_super_admin_can_list_stores(self, super_token, store_a, store_b):
        r = _get("/admin/stores", token=super_token)
        assert r.status_code == 200
        stores = r.json()
        ids = {s["id"] for s in stores}
        assert store_a["user"]["store_id"] in ids
        assert store_b["user"]["store_id"] in ids
        a = next(s for s in stores if s["id"] == store_a["user"]["store_id"])
        assert "users" in a and "parts" in a and "in_stock" in a
        assert a["users"] >= 1

    def test_store_owner_cannot_list_stores(self, store_a):
        r = _get("/admin/stores", token=store_a["token"])
        assert r.status_code == 403


# ----- Data isolation -----
class TestIsolation:
    @pytest.fixture(scope="class")
    def part_a(self, store_a):
        pn = f"TESTPN_{uuid.uuid4().hex[:8].upper()}"
        r = _post("/buy", {"part_number": pn, "company": "Hyundai+Kia",
                            "name": "TEST Part", "condition": "Working"},
                  token=store_a["token"])
        assert r.status_code == 200, r.text
        return pn

    def test_store_b_inventory_does_not_show_a(self, store_b, part_a):
        r = _get("/inventory", token=store_b["token"])
        assert r.status_code == 200
        pns = [u["part_number"] for u in r.json()]
        assert part_a not in pns

    def test_store_b_parts_does_not_show_a(self, store_b, part_a):
        r = _get("/parts", token=store_b["token"])
        assert r.status_code == 200
        pns = [p["part_number"] for p in r.json()]
        assert part_a not in pns

    def test_store_b_search_says_new(self, store_b, part_a):
        r = _get("/search", token=store_b["token"], params={"q": part_a})
        assert r.status_code == 200
        # b never bought/registered it → NEW PART
        assert r.json()["status"] == "NEW PART"
        assert r.json()["stock_count"] == 0

    def test_store_a_search_says_in_stock(self, store_a, part_a):
        r = _get("/search", token=store_a["token"], params={"q": part_a})
        assert r.status_code == 200
        assert r.json()["status"] == "IN STOCK"
        assert r.json()["stock_count"] >= 1

    def test_stats_scoped(self, store_a, store_b, part_a):
        ra = _get("/stats", token=store_a["token"]).json()
        rb = _get("/stats", token=store_b["token"]).json()
        assert ra["in_stock_units"] >= 1
        # b shouldn't count store a's unit
        assert rb["in_stock_units"] == 0 or rb["in_stock_units"] < ra["in_stock_units"]

    def test_store_b_cannot_override_store_id_param(self, store_b, store_a, part_a):
        # Attempt to leak by passing ?store_id=A → resolve_store must ignore it for non-super
        r = _get("/inventory", token=store_b["token"],
                 params={"store_id": store_a["user"]["store_id"]})
        assert r.status_code == 200
        pns = [u["part_number"] for u in r.json()]
        assert part_a not in pns, "store_id param leak: store B saw store A's stock"


# ----- Sell -----
class TestBuySell:
    def test_buy_then_sell_decreases_stock(self, store_a):
        pn = f"TESTBS_{uuid.uuid4().hex[:8].upper()}"
        r = _post("/buy", {"part_number": pn, "condition": "Working"},
                  token=store_a["token"])
        assert r.status_code == 200
        r2 = _get("/search", token=store_a["token"], params={"q": pn}).json()
        assert r2["stock_count"] == 1
        s = _post("/sell", {"part_number": pn, "price": 100},
                  token=store_a["token"])
        assert s.status_code == 200
        assert s.json()["remaining_stock"] == 0
        r3 = _get("/search", token=store_a["token"], params={"q": pn}).json()
        assert r3["status"] != "IN STOCK"


# ----- SEC-004: /admin/users must not leak secrets -----
class TestSec004:
    def test_admin_users_hides_secrets(self, store_a):
        r = _get("/admin/users", token=store_a["token"])
        assert r.status_code == 200
        for u in r.json():
            assert "password_enc" not in u, "SEC-004: password_enc leaked"
            assert "password_hash" not in u
            assert "google_api_key" not in u, "SEC-004: google_api_key leaked"
            assert "google_cx" not in u, "SEC-004: google_cx leaked"


# ----- SEC-001: store admin creating admin-role user should get staff -----
class TestSec001:
    def test_store_admin_cannot_create_admin(self, store_a):
        un = f"test_staff_{uuid.uuid4().hex[:6]}"
        r = _post("/admin/users",
                  {"name": "TEST Staff", "username": un, "password": "Test@1234",
                   "role": "admin"},
                  token=store_a["token"])
        assert r.status_code == 200, r.text
        d = r.json()
        assert d["role"] == "staff", f"SEC-001: role should be forced to staff, got {d['role']}"
        assert d["store_id"] == store_a["user"]["store_id"]

    def test_cross_store_user_edit_blocked(self, store_a, store_b):
        # Create a staff in store B, then try to edit from store A
        un = f"test_edit_{uuid.uuid4().hex[:6]}"
        rb = _post("/admin/users",
                   {"name": "B Staff", "username": un, "password": "Test@1234"},
                   token=store_b["token"])
        assert rb.status_code == 200
        target_id = rb.json()["id"]
        # Store A tries to patch store B's staff
        r = requests.patch(API + f"/admin/users/{target_id}",
                           headers={"Authorization": f"Bearer {store_a['token']}",
                                    "Content-Type": "application/json"},
                           json={"name": "hijacked"}, timeout=30)
        assert r.status_code == 403


# ----- Super-admin can view all stores' users -----
class TestSuperAdminListUsers:
    def test_super_admin_sees_all_users(self, super_token, store_a, store_b):
        r = _get("/admin/users", token=super_token)
        assert r.status_code == 200
        unames = {u["username"] for u in r.json()}
        assert store_a["payload"]["username"].lower() in unames
        assert store_b["payload"]["username"].lower() in unames
