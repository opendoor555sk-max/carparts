"""Regression test for the view_part_details permission gating full part
data on the search/part-lookup flow.

A staff account with only "search" (the STAFF_DEFAULT set, no
view_part_details) must be able to tell whether a part exists in the
store's inventory, but never see its name, company, exact stock count,
verification status, price, cost, purchase history, or vendor info.
GET /parts (the Catalog tab's category-browse list -- the only caller of
this endpoint in the app) is the one place this session's investigation
found actually leaking those fields regardless of permission, so this
verifies its reduced shape directly, plus the permission model itself
(new permission exists, opt-in only, not granted by default).

Like the other *_local.py tests, this runs fully offline: the real
FastAPI `app` in-process against an in-memory Mongo mock (mongomock-motor).
"""
import os
import uuid

os.environ.setdefault("MONGO_URL", "mongodb://localhost:27017")
os.environ.setdefault("DB_NAME", "test_view_part_details")
os.environ.setdefault("JWT_SECRET", "test-secret-not-for-prod")
os.environ.setdefault("FERNET_KEY", "kM3vQe8yV3nq9x8h2b0Zt9mE1Fq4Yx2G8oB7dS6cR0k=")

import pytest
from fastapi.testclient import TestClient
from mongomock_motor import AsyncMongoMockClient

import server as srv

srv.db = AsyncMongoMockClient()[os.environ["DB_NAME"]]


@pytest.fixture(scope="module")
def client():
    with TestClient(srv.app) as c:
        yield c


def _auth(token):
    return {"Authorization": f"Bearer {token}"}


def _register_store_admin(client, prefix):
    suffix = uuid.uuid4().hex[:8]
    mobile = f"90000{uuid.uuid4().int % 100000:05d}"
    reqr = client.post("/api/store-requests", json={"name": f"TEST_{prefix}_{suffix}", "mobile": mobile})
    assert reqr.status_code == 200, reqr.text
    request_id = reqr.json()["id"]

    owner_login = client.post("/api/auth/login", json={"username": "abdul", "password": "Salam@123"})
    assert owner_login.status_code == 200, owner_login.text
    owner_token = owner_login.json()["access_token"]

    otpr = client.post(f"/api/owner/store-requests/{request_id}/generate-otp", headers=_auth(owner_token))
    assert otpr.status_code == 200, otpr.text
    otp = otpr.json()["otp"]

    r = client.post(f"/api/store-requests/{request_id}/verify-otp", json={"otp": otp})
    assert r.status_code == 200, r.text
    return r.json()


def test_view_part_details_is_a_real_permission_not_granted_by_default():
    assert "view_part_details" in srv.ALL_PERMISSIONS
    assert "view_part_details" not in srv.STAFF_DEFAULT


def test_staff_without_permission_sees_only_existence_on_parts_list(client):
    admin = _register_store_admin(client, "PARTVIEW")
    admin_headers = _auth(admin["access_token"])

    pn = f"PV-{uuid.uuid4().hex[:8].upper()}"
    created = client.post(
        "/api/parts",
        json={"part_number": pn, "name": "Secret Part Name", "category": "Sensors", "company": "Maruti Suzuki"},
        headers=admin_headers,
    )
    assert created.status_code == 200, created.text

    un = f"staff_{uuid.uuid4().hex[:8]}"
    staff_created = client.post(
        "/api/admin/users",
        json={"name": "Plain Staff", "username": un, "password": "Test@1234"},
        headers=admin_headers,
    )
    assert staff_created.status_code == 200, staff_created.text
    assert "view_part_details" not in staff_created.json()["permissions"], (
        "a freshly created staff account must not have view_part_details unless explicitly granted"
    )
    staff_login = client.post("/api/auth/login", json={"username": un, "password": "Test@1234"})
    assert staff_login.status_code == 200, staff_login.text
    staff_headers = _auth(staff_login.json()["access_token"])

    listed = client.get("/api/parts?category=Sensors", headers=staff_headers)
    assert listed.status_code == 200, listed.text
    rows = [r for r in listed.json() if r["part_number"] == pn]
    assert len(rows) == 1, listed.text
    row = rows[0]
    assert set(row.keys()) == {"id", "part_number", "exists"}, (
        f"staff without view_part_details must only see id/part_number/exists, got: {sorted(row.keys())}"
    )
    assert row["exists"] is False  # no stock units bought yet

    # Buy a unit so the part has stock, and confirm existence flips to True
    # while the row still stays reduced to just id/part_number/exists.
    buy = client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=admin_headers)
    assert buy.status_code == 200, buy.text
    listed2 = client.get("/api/parts?category=Sensors", headers=staff_headers)
    row2 = [r for r in listed2.json() if r["part_number"] == pn][0]
    assert set(row2.keys()) == {"id", "part_number", "exists"}
    assert row2["exists"] is True

    # Now grant view_part_details and confirm the SAME token immediately
    # sees the full row -- permissions are re-checked fresh from the DB on
    # every request, not cached in the JWT.
    grant = client.patch(
        f"/api/admin/users/{staff_created.json()['id']}",
        json={"permissions": ["search", "view_part_details"]},
        headers=admin_headers,
    )
    assert grant.status_code == 200, grant.text
    listed3 = client.get("/api/parts?category=Sensors", headers=staff_headers)
    row3 = [r for r in listed3.json() if r["part_number"] == pn][0]
    assert row3["name"] == "Secret Part Name"
    assert row3["company"] == "Maruti Suzuki"
    assert row3["stock_count"] == 1


def test_admin_always_sees_full_parts_list(client):
    admin = _register_store_admin(client, "PARTVIEWADMIN")
    admin_headers = _auth(admin["access_token"])
    pn = f"PVA-{uuid.uuid4().hex[:8].upper()}"
    client.post(
        "/api/parts",
        json={"part_number": pn, "name": "Admin Visible Part", "category": "Sensors", "company": "All"},
        headers=admin_headers,
    )
    listed = client.get("/api/parts?category=Sensors", headers=admin_headers)
    row = [r for r in listed.json() if r["part_number"] == pn][0]
    assert row["name"] == "Admin Visible Part"
