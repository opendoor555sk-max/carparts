"""Kabadi Market Hisab — comprehensive backend API tests."""
import time
import uuid
import requests


# ------------- Auth -------------
class TestAuth:
    def test_admin_login(self, base_url):
        r = requests.post(f"{base_url}/api/auth/login",
                          json={"username": "abdul", "password": "Salam@123"}, timeout=30)
        assert r.status_code == 200
        data = r.json()
        assert "access_token" in data and data["user"]["role"] == "admin"
        # admin should have all 11 permissions
        assert len(data["user"]["permissions"]) >= 11

    def test_wrong_password(self, base_url):
        r = requests.post(f"{base_url}/api/auth/login",
                          json={"username": "abdul", "password": "wrong"}, timeout=30)
        assert r.status_code == 401

    def test_me_endpoint(self, base_url, admin_headers):
        r = requests.get(f"{base_url}/api/auth/me", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        assert r.json()["username"] == "abdul"

    def test_no_token_unauthorized(self, base_url):
        r = requests.get(f"{base_url}/api/auth/me", timeout=30)
        assert r.status_code == 401


# ------------- Meta -------------
class TestMeta:
    def test_categories_73_items_5_groups(self, base_url, admin_headers):
        r = requests.get(f"{base_url}/api/categories", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        d = r.json()
        assert d["total"] == 73
        assert len(d["groups"]) == 5

    def test_companies(self, base_url, admin_headers):
        r = requests.get(f"{base_url}/api/companies", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        assert "Hyundai+Kia" in r.json()

    def test_conditions(self, base_url, admin_headers):
        r = requests.get(f"{base_url}/api/conditions", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        assert "Working" in r.json()


# ------------- Search + status transitions + no stock change -------------
class TestSearchAndStatus:
    def test_new_part_search_no_stock_change(self, base_url, admin_headers):
        pn = f"TEST_NEW_{uuid.uuid4().hex[:8].upper()}"
        r = requests.get(f"{base_url}/api/search", params={"q": pn}, headers=admin_headers, timeout=30)
        assert r.status_code == 200
        d = r.json()
        assert d["status"] == "NEW PART"
        assert d["stock_count"] == 0
        # part must NOT be auto-created by search
        r2 = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30)
        assert r2.status_code == 404

    def test_search_to_buy_transitions_to_in_stock(self, base_url, admin_headers):
        pn = f"TEST_BUY_{uuid.uuid4().hex[:8].upper()}"
        s1 = requests.get(f"{base_url}/api/search", params={"q": pn}, headers=admin_headers, timeout=30).json()
        assert s1["status"] == "NEW PART"
        b = requests.post(f"{base_url}/api/buy", headers=admin_headers,
                          json={"part_number": pn, "condition": "Working",
                                "location": {"text": "Rack A1"}}, timeout=30)
        assert b.status_code == 200
        s2 = requests.get(f"{base_url}/api/search", params={"q": pn}, headers=admin_headers, timeout=30).json()
        assert s2["status"] == "IN STOCK"
        assert s2["stock_count"] == 1
        # verify auto-created part is Unverified
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        assert p["verification_status"] == "Unverified"


# ------------- Buy -------------
class TestBuy:
    def test_buy_increments_stock(self, base_url, admin_headers):
        pn = f"TEST_BUYINC_{uuid.uuid4().hex[:8].upper()}"
        for i in range(2):
            r = requests.post(f"{base_url}/api/buy", headers=admin_headers,
                              json={"part_number": pn, "condition": "Working"}, timeout=30)
            assert r.status_code == 200
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        assert p["stock_count"] == 2


# ------------- Purchase limit -------------
class TestLimits:
    def test_part_limit_stop_and_override(self, base_url, admin_headers):
        pn = f"TEST_LIM_{uuid.uuid4().hex[:8].upper()}"
        # buy 1
        requests.post(f"{base_url}/api/buy", headers=admin_headers,
                      json={"part_number": pn, "condition": "Working"}, timeout=30)
        # set limit=1
        r = requests.post(f"{base_url}/api/limits/part", headers=admin_headers,
                          json={"part_number": pn, "limit": 1, "enabled": True}, timeout=30)
        assert r.status_code == 200
        assert r.json()["status"] == "STOP"
        # next buy should 409
        r2 = requests.post(f"{base_url}/api/buy", headers=admin_headers,
                           json={"part_number": pn, "condition": "Working"}, timeout=30)
        assert r2.status_code == 409
        assert r2.json()["detail"]["code"] == "LIMIT_REACHED"
        # override
        r3 = requests.post(f"{base_url}/api/buy", headers=admin_headers,
                           json={"part_number": pn, "condition": "Working", "override": True}, timeout=30)
        assert r3.status_code == 200

    def test_global_limit_admin_only(self, base_url, admin_headers, staff_headers):
        r = requests.post(f"{base_url}/api/limits/global", headers=admin_headers,
                          json={"global_enabled": False, "global_default": 5}, timeout=30)
        assert r.status_code == 200
        r2 = requests.post(f"{base_url}/api/limits/global", headers=staff_headers,
                          json={"global_enabled": False, "global_default": 3}, timeout=30)
        assert r2.status_code == 403


# ------------- Sell -------------
class TestSell:
    def test_sell_decrements_and_no_stock_error(self, base_url, admin_headers):
        pn = f"TEST_SELL_{uuid.uuid4().hex[:8].upper()}"
        requests.post(f"{base_url}/api/buy", headers=admin_headers,
                      json={"part_number": pn, "condition": "Working"}, timeout=30)
        r = requests.post(f"{base_url}/api/sell", headers=admin_headers,
                         json={"part_number": pn}, timeout=30)
        assert r.status_code == 200
        assert r.json()["remaining_stock"] == 0
        # sell again -> 409 NO_STOCK
        r2 = requests.post(f"{base_url}/api/sell", headers=admin_headers,
                          json={"part_number": pn}, timeout=30)
        assert r2.status_code == 409
        assert r2.json()["detail"]["code"] == "NO_STOCK"


# ------------- Requirements (no stock change) -------------
class TestRequirements:
    def test_requirement_flow(self, base_url, admin_headers):
        pn = f"TEST_REQ_{uuid.uuid4().hex[:8].upper()}"
        r = requests.post(f"{base_url}/api/requirements", headers=admin_headers,
                         json={"part_number": pn, "priority": "High", "quantity": 2}, timeout=30)
        assert r.status_code == 200
        req_id = r.json()["id"]
        assert r.json()["status"] == "Pending"
        # search should show REQUIREMENT status (no stock)
        s = requests.get(f"{base_url}/api/search", params={"q": pn}, headers=admin_headers, timeout=30).json()
        assert s["status"] == "REQUIREMENT"
        assert s["stock_count"] == 0
        # update to Purchased
        r2 = requests.patch(f"{base_url}/api/requirements/{req_id}", headers=admin_headers,
                           json={"status": "Purchased"}, timeout=30)
        assert r2.status_code == 200
        assert r2.json()["status"] == "Purchased"


# ------------- Buying trip -------------
class TestBuyingTrip:
    def test_buying_trip_ok_to_buy(self, base_url, admin_headers):
        pn = f"TEST_TRIP_{uuid.uuid4().hex[:8].upper()}"
        r = requests.post(f"{base_url}/api/buying-trip/scan", headers=admin_headers,
                         json={"part_number": pn}, timeout=30)
        assert r.status_code == 200
        assert r.json()["buy_status"] == "OK TO BUY"

    def test_buying_trip_required(self, base_url, admin_headers):
        pn = f"TEST_TRIP2_{uuid.uuid4().hex[:8].upper()}"
        requests.post(f"{base_url}/api/requirements", headers=admin_headers,
                     json={"part_number": pn, "quantity": 1}, timeout=30)
        r = requests.post(f"{base_url}/api/buying-trip/scan", headers=admin_headers,
                         json={"part_number": pn}, timeout=30)
        assert r.json()["buy_status"] == "BUY — REQUIRED"

    def test_buying_trip_already_in_stock(self, base_url, admin_headers):
        pn = f"TEST_TRIP3_{uuid.uuid4().hex[:8].upper()}"
        requests.post(f"{base_url}/api/buy", headers=admin_headers,
                     json={"part_number": pn, "condition": "Working"}, timeout=30)
        r = requests.post(f"{base_url}/api/buying-trip/scan", headers=admin_headers,
                         json={"part_number": pn}, timeout=30)
        assert r.json()["buy_status"] == "ALREADY IN STOCK"

    def test_buying_trip_do_not_buy(self, base_url, admin_headers):
        pn = f"TEST_TRIP4_{uuid.uuid4().hex[:8].upper()}"
        requests.post(f"{base_url}/api/buy", headers=admin_headers,
                     json={"part_number": pn, "condition": "Working"}, timeout=30)
        requests.post(f"{base_url}/api/limits/part", headers=admin_headers,
                     json={"part_number": pn, "limit": 1, "enabled": True}, timeout=30)
        r = requests.post(f"{base_url}/api/buying-trip/scan", headers=admin_headers,
                         json={"part_number": pn}, timeout=30)
        assert r.json()["buy_status"] == "DO NOT BUY"


# ------------- AI Research (Gemini) -------------
class TestAI:
    def test_ai_research_and_approve(self, base_url, admin_headers):
        pn = f"AI_{uuid.uuid4().hex[:6].upper()}"
        r = requests.post(f"{base_url}/api/ai/research", headers=admin_headers,
                         json={"part_number": pn, "company": "Hyundai+Kia"}, timeout=120)
        assert r.status_code == 200, f"AI research failed: {r.status_code} {r.text}"
        d = r.json()
        assert "confidence" in d and isinstance(d["confidence"], int)
        assert d["approval_status"] == "Pending"
        assert "sources" in d
        rid = d["id"]
        # approve merges into part master as Verified
        a = requests.post(f"{base_url}/api/ai/research/{rid}/approve", headers=admin_headers, timeout=30)
        assert a.status_code == 200
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        assert p["verification_status"] == "Verified"


# ------------- Inventory + stats + demand -------------
class TestInventoryStats:
    def test_inventory_lists_only_non_sold(self, base_url, admin_headers):
        pn = f"TEST_INV_{uuid.uuid4().hex[:8].upper()}"
        requests.post(f"{base_url}/api/buy", headers=admin_headers,
                     json={"part_number": pn, "condition": "Working",
                           "location": {"text": "Rack B"}}, timeout=30)
        r = requests.get(f"{base_url}/api/inventory", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        assert any(u["part_number"] == pn for u in r.json())

    def test_stats_admin_only(self, base_url, admin_headers, staff_headers):
        r = requests.get(f"{base_url}/api/stats", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        assert "total_parts" in r.json()
        r2 = requests.get(f"{base_url}/api/stats", headers=staff_headers, timeout=30)
        assert r2.status_code == 403

    def test_demand_high_after_multiple_searches(self, base_url, admin_headers):
        pn = f"TEST_DEM_{uuid.uuid4().hex[:8].upper()}"
        # search twice, no stock
        requests.get(f"{base_url}/api/search", params={"q": pn}, headers=admin_headers, timeout=30)
        requests.get(f"{base_url}/api/search", params={"q": pn}, headers=admin_headers, timeout=30)
        r = requests.get(f"{base_url}/api/demand", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        assert any(x["part_number"] == pn for x in r.json())


# ------------- Permissions -------------
class TestPermissions:
    def test_staff_cannot_access_admin_endpoints(self, base_url, staff_headers):
        # manage_limits
        r1 = requests.post(f"{base_url}/api/limits/global", headers=staff_headers,
                          json={"global_enabled": False, "global_default": 0}, timeout=30)
        assert r1.status_code == 403
        # manage_users
        r2 = requests.get(f"{base_url}/api/admin/users", headers=staff_headers, timeout=30)
        assert r2.status_code == 403
        # view_stats
        r3 = requests.get(f"{base_url}/api/stats", headers=staff_headers, timeout=30)
        assert r3.status_code == 403

    def test_staff_can_search_and_buy(self, base_url, staff_headers):
        pn = f"TEST_STAFF_{uuid.uuid4().hex[:8].upper()}"
        r = requests.get(f"{base_url}/api/search", params={"q": pn}, headers=staff_headers, timeout=30)
        assert r.status_code == 200
        r2 = requests.post(f"{base_url}/api/buy", headers=staff_headers,
                          json={"part_number": pn, "condition": "Working"}, timeout=30)
        assert r2.status_code == 200
