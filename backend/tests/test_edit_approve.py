"""Tests for edit-before-approve / edit-before-add feature."""
import uuid
import requests


class TestApproveWithEdits:
    def test_approve_with_edits_overrides_ai(self, base_url, admin_headers):
        pn = f"AI_EDIT_{uuid.uuid4().hex[:6].upper()}"
        # run AI research
        r = requests.post(f"{base_url}/api/ai/research", headers=admin_headers,
                         json={"part_number": pn, "company": "Hyundai+Kia"}, timeout=120)
        assert r.status_code == 200, f"AI research failed: {r.text}"
        rid = r.json()["id"]
        # approve with admin edits overriding AI
        edits = {
            "name": "EDITED-Wing Mirror Motor",
            "category": "Motors & Actuators",
            "compatible_vehicles": ["Hyundai Creta", "Kia Seltos", "Kia Sonet"],
            "variant": "Diesel MT",
            "year": "2020-2024",
            "technical_info": "ADMIN EDITED TECH INFO",
        }
        a = requests.post(f"{base_url}/api/ai/research/{rid}/approve",
                         headers=admin_headers, json=edits, timeout=30)
        assert a.status_code == 200, a.text
        # verify part master reflects EDITED values (not raw AI)
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        assert p["name"] == edits["name"]
        assert p["category"] == edits["category"]
        assert p["compatible_vehicles"] == edits["compatible_vehicles"]
        assert p["variant"] == edits["variant"]
        assert p["year"] == edits["year"]
        assert p["technical_info"] == edits["technical_info"]
        assert p["verification_status"] == "Verified"
        assert p["source"] == "AI (Admin approved)"

    def test_approve_without_edits_uses_ai(self, base_url, admin_headers):
        pn = f"AI_NOEDIT_{uuid.uuid4().hex[:6].upper()}"
        r = requests.post(f"{base_url}/api/ai/research", headers=admin_headers,
                         json={"part_number": pn, "company": "Hyundai+Kia"}, timeout=120)
        assert r.status_code == 200
        ai_doc = r.json()
        rid = ai_doc["id"]
        ai_name = ai_doc["result"].get("name", "")
        # approve without body -> AI values used
        a = requests.post(f"{base_url}/api/ai/research/{rid}/approve",
                         headers=admin_headers, timeout=30)
        assert a.status_code == 200
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        assert p["verification_status"] == "Verified"
        if ai_name:
            assert p["name"] == ai_name


class TestNewPartCreation:
    def test_create_new_part_with_details(self, base_url, admin_headers):
        pn = f"MANUAL_{uuid.uuid4().hex[:6].upper()}"
        payload = {
            "part_number": pn, "source": "Manual",
            "name": "Custom BCM", "category": "Body Control Module (BCM)",
            "company": "Hyundai+Kia",
            "compatible_vehicles": ["Hyundai i20", "Hyundai Verna"],
            "variant": "Petrol", "year": "2018",
            "technical_info": "12V, 20-pin connector",
        }
        r = requests.post(f"{base_url}/api/parts", headers=admin_headers, json=payload, timeout=30)
        assert r.status_code == 200, r.text
        # verify persisted
        g = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        assert g["name"] == "Custom BCM"
        assert g["compatible_vehicles"] == ["Hyundai i20", "Hyundai Verna"]
        assert g["verification_status"] == "Unverified"

    def test_duplicate_part_creation_rejected(self, base_url, admin_headers):
        pn = f"DUP_{uuid.uuid4().hex[:6].upper()}"
        r1 = requests.post(f"{base_url}/api/parts", headers=admin_headers,
                          json={"part_number": pn, "name": "X"}, timeout=30)
        assert r1.status_code == 200
        r2 = requests.post(f"{base_url}/api/parts", headers=admin_headers,
                          json={"part_number": pn, "name": "Y"}, timeout=30)
        assert r2.status_code == 400


class TestEditExistingPart:
    def test_patch_updates_part(self, base_url, admin_headers):
        pn = f"PATCH_{uuid.uuid4().hex[:6].upper()}"
        # create part
        requests.post(f"{base_url}/api/parts", headers=admin_headers,
                     json={"part_number": pn, "name": "Old", "variant": "OldVariant"}, timeout=30)
        # patch
        r = requests.patch(f"{base_url}/api/parts/{pn}", headers=admin_headers,
                          json={"variant": "NewVariant", "year": "2025"}, timeout=30)
        assert r.status_code == 200
        # verify
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30).json()
        assert p["variant"] == "NewVariant"
        assert p["year"] == "2025"
        assert p["name"] == "Old"  # unchanged


class TestRejectAI:
    def test_reject_ai_no_verified_part(self, base_url, admin_headers):
        pn = f"REJ_{uuid.uuid4().hex[:6].upper()}"
        r = requests.post(f"{base_url}/api/ai/research", headers=admin_headers,
                         json={"part_number": pn, "company": "All"}, timeout=120)
        assert r.status_code == 200
        rid = r.json()["id"]
        rej = requests.post(f"{base_url}/api/ai/research/{rid}/reject",
                           headers=admin_headers, timeout=30)
        assert rej.status_code == 200
        # confirm approval_status Rejected
        listing = requests.get(f"{base_url}/api/ai/research",
                              headers=admin_headers,
                              params={"part_number": pn}, timeout=30).json()
        assert listing[0]["approval_status"] == "Rejected"
        # part should NOT be created as Verified (no part expected)
        p = requests.get(f"{base_url}/api/parts/{pn}", headers=admin_headers, timeout=30)
        assert p.status_code == 404
