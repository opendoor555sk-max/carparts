"""Gemini API key provider swap regression suite.

Verifies:
  1. POST /api/ai/research returns 200 with honest labeling for a fresh PN.
  2. DB-FIRST branch after approve returns Verified/100/from_database=true.
  3. AI branch NEVER self-marks Verified.
  4. Core endpoints (categories=73, buy inc, sell dec, stats) unaffected.
"""
import uuid
import requests


class TestGeminiAIResearch:
    """AI research endpoint honest-labeling contract"""

    _pn = f"95400-{uuid.uuid4().hex[:5].upper()}"
    _rid = None

    def test_1_ai_research_fresh_pn(self, base_url, admin_headers):
        """Feature 1: fresh PN -> Requires Verification / Pending / from_database=false"""
        r = requests.post(
            f"{base_url}/api/ai/research",
            headers=admin_headers,
            json={"part_number": self.__class__._pn, "company": "Hyundai+Kia"},
            timeout=90,
        )
        assert r.status_code == 200, f"Expected 200, got {r.status_code}: {r.text}"
        d = r.json()

        # Honest labeling (critical)
        assert d.get("verification") == "Requires Verification", (
            f"AI must NEVER self-mark Verified. got={d.get('verification')}")
        assert d.get("approval_status") == "Pending"
        assert d.get("from_database") is False

        # Shape assertions
        assert "grounded" in d and isinstance(d["grounded"], bool)
        assert isinstance(d.get("confidence"), int)

        result = d.get("result", {})
        assert isinstance(result, dict), "result must be an object"
        # status may be SUCCESS or NOT_FOUND per honest-labeling contract
        assert result.get("status") in ("SUCCESS", "NOT_FOUND"), \
            f"unexpected status: {result.get('status')}"
        # compatible_vehicles is derived from compatible_models when missing (server line 879)
        assert "compatible_vehicles" in result and isinstance(result["compatible_vehicles"], list)
        assert "cross_reference" in result and isinstance(result["cross_reference"], list)

        self.__class__._rid = d["id"]

    def test_2_approve_with_edits(self, base_url, admin_headers):
        """Feature 2a: approve merges admin edits into part master as Verified."""
        assert self.__class__._rid, "prior test must have created a research doc"
        payload = {
            "name": "Test BCM",
            "company": "Hyundai+Kia",
            "compatible_vehicles": ["Hyundai Creta"],
            "variant": "BS6",
            "year": "2020",
            "category": "Electrical/Body Control",
        }
        r = requests.post(
            f"{base_url}/api/ai/research/{self.__class__._rid}/approve",
            headers=admin_headers, json=payload, timeout=30,
        )
        assert r.status_code == 200, f"approve failed: {r.status_code} {r.text}"
        assert r.json()["ok"] is True

        # Verify part master got the edits
        p = requests.get(
            f"{base_url}/api/parts/{self.__class__._pn}",
            headers=admin_headers, timeout=30,
        ).json()
        assert p["verification_status"] == "Verified"
        assert p["compatible_vehicles"] == ["Hyundai Creta"]
        assert p["name"] == "Test BCM"

    def test_3_db_first_after_approve(self, base_url, admin_headers):
        """Feature 2b: subsequent research returns instantly from DB with 100% Verified."""
        import time
        t0 = time.time()
        r = requests.post(
            f"{base_url}/api/ai/research",
            headers=admin_headers,
            json={"part_number": self.__class__._pn, "company": "Hyundai+Kia"},
            timeout=15,  # tight timeout: must NOT hit Gemini
        )
        elapsed = time.time() - t0
        assert r.status_code == 200
        d = r.json()

        assert d.get("from_database") is True
        assert d.get("verification") == "Verified"
        assert d.get("confidence") == 100
        assert d.get("approval_status") == "Approved"

        result = d.get("result", {})
        assert result.get("status") == "SUCCESS"
        assert result.get("compatible_vehicles") == ["Hyundai Creta"]

        # Must be fast (no external AI call)
        assert elapsed < 10, f"DB-first branch too slow ({elapsed:.1f}s) — likely called Gemini"


# ------------- Core endpoints regression -------------
class TestCoreUnaffected:
    def test_categories_73(self, base_url, admin_headers):
        r = requests.get(f"{base_url}/api/categories", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        assert r.json()["total"] == 73

    def test_buy_increases_stock(self, base_url, admin_headers):
        pn = f"TEST_SWAP_BUY_{uuid.uuid4().hex[:6].upper()}"
        r = requests.post(f"{base_url}/api/buy", headers=admin_headers,
                          json={"part_number": pn, "condition": "Working"}, timeout=30)
        assert r.status_code == 200
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        assert p["stock_count"] == 1

    def test_sell_decreases_stock(self, base_url, admin_headers):
        pn = f"TEST_SWAP_SELL_{uuid.uuid4().hex[:6].upper()}"
        requests.post(f"{base_url}/api/buy", headers=admin_headers,
                      json={"part_number": pn, "condition": "Working"}, timeout=30)
        r = requests.post(f"{base_url}/api/sell", headers=admin_headers,
                          json={"part_number": pn}, timeout=30)
        assert r.status_code == 200
        assert r.json()["remaining_stock"] == 0

    def test_stats_ok(self, base_url, admin_headers):
        r = requests.get(f"{base_url}/api/stats", headers=admin_headers, timeout=30)
        assert r.status_code == 200
        assert "total_parts" in r.json()
