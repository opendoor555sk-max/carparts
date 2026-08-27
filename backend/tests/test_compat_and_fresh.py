"""Iter 8 — Auto-log compatibility on Buy + Fresh data + Core sanity."""
import uuid
import requests


# ---- Fresh data (empty parts + inventory), 73 categories ----
class TestFreshData:
    def test_categories_still_73(self, base_url, admin_headers):
        r = requests.get(f"{base_url}/api/categories", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        assert r.json()["total"] == 73

    def test_parts_and_inventory_can_be_empty(self, base_url, admin_headers):
        # NOTE: this suite creates parts, so run FIRST or note pre-existing entries.
        # We only assert the endpoint is 200 and shape is list.
        r = requests.get(f"{base_url}/api/parts", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        assert isinstance(r.json(), list)
        r2 = requests.get(f"{base_url}/api/inventory", headers=admin_headers, timeout=30)
        assert r2.status_code == 200
        assert isinstance(r2.json(), list)


# ---- Auto-log compatibility on Buy (creates new part) ----
class TestBuyAutoLogsCompatibility:
    def test_buy_new_part_saves_compat(self, base_url, admin_headers):
        pn = f"TEST-CMP-{uuid.uuid4().hex[:6].upper()}"
        payload = {
            "part_number": pn,
            "company": "Hyundai+Kia",
            "name": "BCM",
            "category": "Electrical/Body Control",
            "compatible_vehicles": ["Hyundai Creta", "Kia Seltos"],
            "variant": "HTC Diesel",
            "condition": "Working",
            "location": {"rack": "R1", "shelf": "S2"},
        }
        r = requests.post(f"{base_url}/api/buy", headers=admin_headers, json=payload, timeout=30)
        assert r.status_code == 200, r.text
        assert r.json()["ok"] is True
        # Verify part master persisted the compatibility fields
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        assert p["company"] == "Hyundai+Kia"
        assert p["compatible_vehicles"] == ["Hyundai Creta", "Kia Seltos"]
        assert p["variant"] == "HTC Diesel"
        assert p["name"] == "BCM"
        assert p["category"] == "Electrical/Body Control"
        assert p["stock_count"] == 1

    def test_buy_existing_part_does_not_overwrite_verified_compat(self, base_url, admin_headers):
        """Create a part with vehicles ['Hyundai Creta'] (via first buy),
        then buy again with DIFFERENT vehicles -> original preserved."""
        pn = f"TEST-CMPX-{uuid.uuid4().hex[:6].upper()}"
        # First buy establishes compat
        r1 = requests.post(f"{base_url}/api/buy", headers=admin_headers, json={
            "part_number": pn,
            "company": "Hyundai+Kia",
            "compatible_vehicles": ["Hyundai Creta"],
            "variant": "SX",
            "condition": "Working",
        }, timeout=30)
        assert r1.status_code == 200
        # Second buy tries to overwrite
        r2 = requests.post(f"{base_url}/api/buy", headers=admin_headers, json={
            "part_number": pn,
            "company": "Toyota",
            "compatible_vehicles": ["Toyota Innova"],
            "variant": "GX",
            "condition": "Working",
        }, timeout=30)
        assert r2.status_code == 200
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        # Original values preserved (only-empty-fields-filled rule)
        assert p["compatible_vehicles"] == ["Hyundai Creta"], p
        assert p["variant"] == "SX", p
        assert p["company"] == "Hyundai+Kia", p
        assert p["stock_count"] == 2

    def test_buy_empty_compat_still_ok(self, base_url, admin_headers):
        pn = f"TEST-CMPE-{uuid.uuid4().hex[:6].upper()}"
        r = requests.post(f"{base_url}/api/buy", headers=admin_headers,
                          json={"part_number": pn, "condition": "Working"}, timeout=30)
        assert r.status_code == 200
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        assert p["stock_count"] == 1
        assert p["compatible_vehicles"] == []


# ---- Core sanity ----
class TestCoreStillWorks:
    def test_sell_decrements(self, base_url, admin_headers):
        pn = f"TEST-SEL-{uuid.uuid4().hex[:6].upper()}"
        requests.post(f"{base_url}/api/buy", headers=admin_headers,
                      json={"part_number": pn, "condition": "Working"}, timeout=30)
        r = requests.post(f"{base_url}/api/sell", headers=admin_headers,
                          json={"part_number": pn}, timeout=30)
        assert r.status_code == 200
        assert r.json()["remaining_stock"] == 0
