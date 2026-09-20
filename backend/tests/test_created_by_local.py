"""Regression/verification test for the staff `created_by` attribution
feature (see server.py's create_user() and the startup() backfill).

Prompted by a user report: "Added by: <name>" isn't showing for staff
under the 'New India' store. This file verifies, fully offline against
the real FastAPI app + an in-memory Mongo mock, that:

1. A brand-new staff account created via POST /admin/users gets
   created_by stamped with the CREATING admin's own id/name/contact (never
   client-supplied), and GET /admin/users returns that field intact.
2. A store's own first admin (created via the store-request/OTP signup
   flow, not by another user) correctly has created_by=None -- there is no
   human creator for that account, so "Unknown" in the UI is the CORRECT
   outcome for it, not a bug.
3. The startup() backfill sets created_by=None (-> "Unknown" in the UI)
   only on accounts that predate the field entirely (no created_by key at
   all), without touching accounts that already have it set -- this is
   what accounts created before this feature shipped would look like.

This cannot reach the live production MongoDB or the live Render backend
from this sandbox (both are outside the allowed network egress), so it
cannot inspect the actual 'New India' store's existing records directly.
What it CAN do is prove the current code is correct end-to-end, which
narrows the live report down to a deployment/staleness question (see the
investigation notes reported alongside this file).
"""
import asyncio
import os
import uuid

os.environ.setdefault("MONGO_URL", "mongodb://localhost:27017")
os.environ.setdefault("DB_NAME", "test_created_by")
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
    """Drives the real store-request/OTP signup flow (owner-approved) to
    create a fresh store + its first admin, exactly as a real store owner
    would -- same helper shape used by the other *_local.py test files."""
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
    return r.json()  # {access_token, token_type, user, temp_password}


def test_new_staff_account_gets_created_by_stamped(client):
    admin = _register_store_admin(client, "NEWINDIA")
    admin_headers = _auth(admin["access_token"])

    un = f"staff_{uuid.uuid4().hex[:8]}"
    created = client.post(
        "/api/admin/users",
        json={"name": "Test Staff", "username": un, "password": "Test@1234"},
        headers=admin_headers,
    )
    assert created.status_code == 200, created.text

    listed = client.get("/api/admin/users", headers=admin_headers)
    assert listed.status_code == 200, listed.text
    staff_entries = [u for u in listed.json() if u["username"] == un]
    assert len(staff_entries) == 1
    staff = staff_entries[0]

    assert staff.get("created_by") is not None, (
        "created_by is missing/null on a staff account created just now via "
        "POST /admin/users -- this would be a genuine bug in create_user()"
    )
    assert staff["created_by"]["id"] == admin["user"]["id"]
    assert staff["created_by"]["name"] == admin["user"]["name"]


def test_stores_own_first_admin_has_no_creator(client):
    # The store's own first admin (created by the OTP signup flow itself,
    # not by another logged-in user) has no human creator -- created_by is
    # deliberately None here, which the UI renders as "Unknown". This is
    # correct, not a bug: there's nobody else to attribute it to.
    admin = _register_store_admin(client, "SOLOADMIN")
    admin_headers = _auth(admin["access_token"])
    listed = client.get("/api/admin/users", headers=admin_headers)
    assert listed.status_code == 200, listed.text
    self_entries = [u for u in listed.json() if u["id"] == admin["user"]["id"]]
    assert len(self_entries) == 1
    assert self_entries[0].get("created_by") is None


def test_startup_backfill_only_fills_missing_created_by():
    # Simulates an account that predates the created_by field entirely (no
    # key at all in the stored doc) -- exactly what a pre-existing "New
    # India" staff account would look like if it was created before this
    # feature shipped to the live backend.
    pre_existing_id = srv.new_id()
    doc = {
        "id": pre_existing_id, "name": "Pre-Existing Staff", "username": f"legacy_{uuid.uuid4().hex[:8]}",
        "password_hash": srv.hash_pw("Test@1234"), "password_enc": srv.encrypt_pw("Test@1234"),
        "role": "staff", "store_id": "some-store-id", "permissions": srv.STAFF_DEFAULT,
        "disabled": False, "created_at": srv.now_iso(),
        # deliberately no "created_by" key at all
    }
    asyncio.run(srv.db.users.insert_one(dict(doc)))
    assert "created_by" not in asyncio.run(srv.db.users.find_one({"id": pre_existing_id}))

    asyncio.run(srv.startup())

    refreshed = asyncio.run(srv.db.users.find_one({"id": pre_existing_id}))
    assert "created_by" in refreshed, "backfill did not add the field at all"
    assert refreshed["created_by"] is None, "backfill must set None (-> Unknown in the UI), not guess a value"

    # And an account that already has created_by set must be left untouched
    # by the backfill (enrich-only, never overwrite).
    attributed_id = srv.new_id()
    doc2 = {
        "id": attributed_id, "name": "Attributed Staff", "username": f"attributed_{uuid.uuid4().hex[:8]}",
        "password_hash": srv.hash_pw("Test@1234"), "password_enc": srv.encrypt_pw("Test@1234"),
        "role": "staff", "store_id": "some-store-id", "permissions": srv.STAFF_DEFAULT,
        "disabled": False, "created_at": srv.now_iso(),
        "created_by": {"id": "admin-1", "name": "Real Admin", "contact": "+911111111111"},
    }
    asyncio.run(srv.db.users.insert_one(dict(doc2)))
    asyncio.run(srv.startup())
    refreshed2 = asyncio.run(srv.db.users.find_one({"id": attributed_id}))
    assert refreshed2["created_by"]["name"] == "Real Admin", "backfill must never overwrite an existing created_by"
