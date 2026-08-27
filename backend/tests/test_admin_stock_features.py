"""Tests for Admin-only features added this iteration:
 - Stock adjust (/api/stock/adjust) and delete unit (/api/stock/unit/{id})
 - Admin password view/reset (/api/admin/users/{id}/password + PATCH)
 - Physical stock verification (/api/stock/verification + /api/stock/verify)
"""
import os
import time
import uuid
import pytest
import requests

BASE_URL = os.environ.get("EXPO_PUBLIC_BACKEND_URL", "").rstrip("/") or \
           os.environ.get("EXPO_BACKEND_URL", "").rstrip("/")
if not BASE_URL:
    # fall back to /app/frontend/.env
    from pathlib import Path
    for line in Path("/app/frontend/.env").read_text().splitlines():
        if line.startswith("EXPO_PUBLIC_BACKEND_URL="):
            BASE_URL = line.split("=", 1)[1].strip().strip('"').rstrip("/")
            break

ADMIN = {"username": "abdul", "password": "Salam@123"}
STAFF_UN = f"teststaff{uuid.uuid4().hex[:6]}"
STAFF_PW = "Testp@ss1"


@pytest.fixture(scope="module")
def admin_token():
    r = requests.post(f"{BASE_URL}/api/auth/login", json=ADMIN, timeout=30)
    assert r.status_code == 200, r.text
    return r.json()["access_token"]


@pytest.fixture(scope="module")
def staff_token(admin_token):
    # create a temporary staff via admin
    r = requests.post(f"{BASE_URL}/api/admin/users",
                      json={"name": "TEST Staff", "username": STAFF_UN, "password": STAFF_PW, "role": "staff"},
                      headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
    assert r.status_code == 200, r.text
    staff_id = r.json()["id"]
    lr = requests.post(f"{BASE_URL}/api/auth/login",
                       json={"username": STAFF_UN, "password": STAFF_PW}, timeout=30)
    assert lr.status_code == 200
    yield lr.json()["access_token"], staff_id
    # cleanup: soft-delete
    requests.delete(f"{BASE_URL}/api/admin/users/{staff_id}",
                    headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)


@pytest.fixture(scope="module")
def test_part(admin_token):
    pn = f"TESTADJ{uuid.uuid4().hex[:6].upper()}"
    r = requests.post(f"{BASE_URL}/api/parts",
                      json={"part_number": pn, "company": "All", "name": "TEST adjust part"},
                      headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
    assert r.status_code == 200, r.text
    yield pn
    # leave as is — no delete endpoint. Adjust units back to zero.
    st = requests.get(f"{BASE_URL}/api/parts/{pn}",
                      headers={"Authorization": f"Bearer {admin_token}"}, timeout=30).json()
    cnt = st.get("stock_count", 0)
    if cnt:
        requests.post(f"{BASE_URL}/api/stock/adjust",
                      json={"part_number": pn, "delta": -cnt},
                      headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)


class TestStockAdjust:
    def test_adjust_increase(self, admin_token, test_part):
        r = requests.post(f"{BASE_URL}/api/stock/adjust",
                          json={"part_number": test_part, "delta": 3},
                          headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200, r.text
        j = r.json()
        assert j["added"] == 3 and j["removed"] == 0
        # verify
        p = requests.get(f"{BASE_URL}/api/parts/{test_part}",
                        headers={"Authorization": f"Bearer {admin_token}"}, timeout=30).json()
        assert p["stock_count"] == 3

    def test_adjust_decrease(self, admin_token, test_part):
        r = requests.post(f"{BASE_URL}/api/stock/adjust",
                          json={"part_number": test_part, "delta": -1},
                          headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200, r.text
        j = r.json()
        assert j["removed"] == 1
        p = requests.get(f"{BASE_URL}/api/parts/{test_part}",
                        headers={"Authorization": f"Bearer {admin_token}"}, timeout=30).json()
        assert p["stock_count"] == 2

    def test_delete_unit(self, admin_token, test_part):
        p = requests.get(f"{BASE_URL}/api/parts/{test_part}",
                        headers={"Authorization": f"Bearer {admin_token}"}, timeout=30).json()
        assert len(p["units"]) >= 1
        uid = p["units"][0]["id"]
        r = requests.delete(f"{BASE_URL}/api/stock/unit/{uid}",
                            headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200, r.text
        p2 = requests.get(f"{BASE_URL}/api/parts/{test_part}",
                         headers={"Authorization": f"Bearer {admin_token}"}, timeout=30).json()
        assert p2["stock_count"] == p["stock_count"] - 1

    def test_adjust_requires_admin(self, staff_token, test_part):
        tok, _ = staff_token
        r = requests.post(f"{BASE_URL}/api/stock/adjust",
                          json={"part_number": test_part, "delta": 1},
                          headers={"Authorization": f"Bearer {tok}"}, timeout=30)
        assert r.status_code == 403

    def test_delete_unit_requires_admin(self, staff_token, admin_token, test_part):
        # ensure at least one unit exists
        requests.post(f"{BASE_URL}/api/stock/adjust",
                      json={"part_number": test_part, "delta": 1},
                      headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        p = requests.get(f"{BASE_URL}/api/parts/{test_part}",
                        headers={"Authorization": f"Bearer {admin_token}"}, timeout=30).json()
        uid = p["units"][0]["id"]
        tok, _ = staff_token
        r = requests.delete(f"{BASE_URL}/api/stock/unit/{uid}",
                            headers={"Authorization": f"Bearer {tok}"}, timeout=30)
        assert r.status_code == 403


class TestAdminPasswordView:
    def test_admin_can_view_own_password(self, admin_token):
        me = requests.get(f"{BASE_URL}/api/auth/me",
                          headers={"Authorization": f"Bearer {admin_token}"}, timeout=30).json()
        r = requests.get(f"{BASE_URL}/api/admin/users/{me['id']}/password",
                         headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200, r.text
        assert r.json()["password"] == "Salam@123"

    def test_admin_can_view_staff_password(self, admin_token, staff_token):
        _, sid = staff_token
        r = requests.get(f"{BASE_URL}/api/admin/users/{sid}/password",
                         headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200
        assert r.json()["password"] == STAFF_PW

    def test_staff_cannot_view_password(self, staff_token):
        tok, sid = staff_token
        r = requests.get(f"{BASE_URL}/api/admin/users/{sid}/password",
                         headers={"Authorization": f"Bearer {tok}"}, timeout=30)
        assert r.status_code == 403

    def test_admin_reset_password_and_reveal(self, admin_token, staff_token):
        _, sid = staff_token
        new_pw = "Newp@ss2"
        r = requests.patch(f"{BASE_URL}/api/admin/users/{sid}",
                           json={"password": new_pw},
                           headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200, r.text
        # login with new
        lr = requests.post(f"{BASE_URL}/api/auth/login",
                           json={"username": STAFF_UN, "password": new_pw}, timeout=30)
        assert lr.status_code == 200
        # reveal returns new
        vr = requests.get(f"{BASE_URL}/api/admin/users/{sid}/password",
                          headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert vr.status_code == 200 and vr.json()["password"] == new_pw

    def test_admin_edit_name_and_username(self, admin_token, staff_token):
        _, sid = staff_token
        new_un = STAFF_UN + "x"
        r = requests.patch(f"{BASE_URL}/api/admin/users/{sid}",
                           json={"name": "TEST Renamed", "username": new_un},
                           headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200
        assert r.json()["name"] == "TEST Renamed"
        assert r.json()["username"] == new_un.lower()
        # revert username so future tests still valid
        requests.patch(f"{BASE_URL}/api/admin/users/{sid}",
                       json={"username": STAFF_UN},
                       headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)


class TestStockVerification:
    def test_verification_list(self, admin_token, test_part):
        # make sure part has some stock
        requests.post(f"{BASE_URL}/api/stock/adjust",
                      json={"part_number": test_part, "delta": 2},
                      headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        r = requests.get(f"{BASE_URL}/api/stock/verification",
                         headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200
        body = r.json()
        assert "items" in body
        pns = {i["part_number"]: i["expected"] for i in body["items"]}
        assert test_part in pns and pns[test_part] >= 2

    def test_verify_detects_missing_and_extra(self, admin_token, test_part):
        # get current expected
        v = requests.get(f"{BASE_URL}/api/stock/verification",
                         headers={"Authorization": f"Bearer {admin_token}"}, timeout=30).json()
        expected = {i["part_number"]: i["expected"] for i in v["items"]}
        # counts: intentionally undercount test_part by 1 (MISSING)
        counts = [{"part_number": pn, "counted": exp} for pn, exp in expected.items()]
        for c in counts:
            if c["part_number"] == test_part:
                c["counted"] = expected[test_part] - 1
        r = requests.post(f"{BASE_URL}/api/stock/verify",
                          json={"counts": counts},
                          headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200, r.text
        rep = r.json()
        assert rep["total_parts"] == len(expected)
        # find our discrepancy
        our = [d for d in rep["discrepancies"] if d["part_number"] == test_part]
        assert len(our) == 1
        assert our[0]["status"] == "MISSING" and our[0]["diff"] == -1

    def test_verify_requires_admin(self, staff_token):
        tok, _ = staff_token
        r = requests.post(f"{BASE_URL}/api/stock/verify",
                          json={"counts": []},
                          headers={"Authorization": f"Bearer {tok}"}, timeout=30)
        assert r.status_code == 403
        r2 = requests.get(f"{BASE_URL}/api/stock/verification",
                          headers={"Authorization": f"Bearer {tok}"}, timeout=30)
        assert r2.status_code == 403


class TestRegressionBuyLimitAndGps:
    def test_buy_limit_still_enforced(self, admin_token):
        pn = f"TESTLIM{uuid.uuid4().hex[:6].upper()}"
        # buy once (creates part)
        r = requests.post(f"{BASE_URL}/api/buy",
                         json={"part_number": pn, "condition": "Working",
                               "location": {"gps": "21.170200,72.831100", "rack": "A1"},
                               "override": True},
                         headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200, r.text
        assert r.json()["unit"]["location"].get("gps") == "21.170200,72.831100"
        # set limit=1
        lr = requests.post(f"{BASE_URL}/api/limits/part",
                          json={"part_number": pn, "limit": 1, "enabled": True},
                          headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert lr.status_code == 200
        # buy again with override:false → 409
        b2 = requests.post(f"{BASE_URL}/api/buy",
                          json={"part_number": pn, "condition": "Working", "override": False},
                          headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert b2.status_code == 409
        d = b2.json().get("detail", {})
        assert d.get("code") == "LIMIT_REACHED"

    def test_change_password_still_works(self, admin_token):
        # change and revert
        r = requests.post(f"{BASE_URL}/api/auth/change-password",
                          json={"current_password": "Salam@123", "new_password": "Salam@1234"},
                          headers={"Authorization": f"Bearer {admin_token}"}, timeout=30)
        assert r.status_code == 200
        # revert
        lr = requests.post(f"{BASE_URL}/api/auth/login",
                           json={"username": "abdul", "password": "Salam@1234"}, timeout=30)
        assert lr.status_code == 200
        tok2 = lr.json()["access_token"]
        r2 = requests.post(f"{BASE_URL}/api/auth/change-password",
                          json={"current_password": "Salam@1234", "new_password": "Salam@123"},
                          headers={"Authorization": f"Bearer {tok2}"}, timeout=30)
        assert r2.status_code == 200
        # confirm reverted
        lr2 = requests.post(f"{BASE_URL}/api/auth/login",
                            json={"username": "abdul", "password": "Salam@123"}, timeout=30)
        assert lr2.status_code == 200
