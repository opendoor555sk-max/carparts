"""Tests for DB-first AI research engine + honest AI labeling (never auto-Verified).

Verifies the three behaviors A/B/C described in the review request.
"""
import uuid
import requests


class TestDbFirstEngine:
    """Behavior A -> B -> C flow on a fresh part number."""

    def test_a_unknown_goes_to_ai_requires_verification(self, base_url, admin_headers):
        # Fresh part number that cannot possibly be in the verified library
        pn = f"DBF-{uuid.uuid4().hex[:8].upper()}"
        r = requests.post(
            f"{base_url}/api/ai/research",
            headers=admin_headers,
            json={"part_number": pn, "company": "Hyundai+Kia"},
            timeout=180,
        )
        assert r.status_code == 200, f"unexpected: {r.status_code} {r.text}"
        d = r.json()
        # Structural checks (Behavior A)
        assert d["from_database"] is False, d
        assert d["verification"] == "Requires Verification", d
        assert d["approval_status"] == "Pending", d
        assert "result" in d and isinstance(d["result"], dict)
        res = d["result"]
        # AI response shape (accept either compatible_models or compatible_vehicles fallback)
        assert "status" in res
        assert "cross_reference" in res
        assert "confidence" in res
        # compatible_models expected; server also back-fills compatible_vehicles
        assert ("compatible_models" in res) or ("compatible_vehicles" in res)
        # Stash for chained tests
        TestDbFirstEngine.pn = pn
        TestDbFirstEngine.rid = d["id"]

    def test_b_admin_approve_with_edits_makes_verified(self, base_url, admin_headers):
        pn = getattr(TestDbFirstEngine, "pn", None)
        rid = getattr(TestDbFirstEngine, "rid", None)
        assert pn and rid, "prior test (A) did not run"
        edits = {
            "name": "Wing Mirror Motor (admin-edited)",
            "company": "Hyundai+Kia",
            "compatible_vehicles": ["Hyundai Creta"],
            "variant": "Diesel MT",
            "year": "2020-2024",
            "category": "Motors & Actuators",
        }
        a = requests.post(
            f"{base_url}/api/ai/research/{rid}/approve",
            headers=admin_headers, json=edits, timeout=30,
        )
        assert a.status_code == 200, a.text
        # Verify part master reflects Verified + admin-edited vehicles
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30)
        assert p.status_code == 200, p.text
        pj = p.json()
        assert pj["verification_status"] == "Verified", pj
        assert pj["compatible_vehicles"] == ["Hyundai Creta"], pj
        assert pj["name"] == edits["name"]
        assert pj["variant"] == edits["variant"]

    def test_c_db_first_hit_returns_verified_without_ai(self, base_url, admin_headers):
        pn = getattr(TestDbFirstEngine, "pn", None)
        assert pn, "prior tests did not run"
        r = requests.post(
            f"{base_url}/api/ai/research",
            headers=admin_headers,
            json={"part_number": pn},
            timeout=30,  # tight: must NOT depend on AI
        )
        assert r.status_code == 200, r.text
        d = r.json()
        assert d["from_database"] is True, d
        assert d["verification"] == "Verified", d
        assert d["confidence"] == 100, d
        assert d["approval_status"] == "Approved", d
        res = d["result"]
        assert res.get("status") == "SUCCESS", res
        assert res.get("compatible_vehicles") == ["Hyundai Creta"], res
