"""Regression test for the store-admin staff verification/approval flow.

Separate from the platform Owner's store_requests approval (a different
flow, scoped to approving a whole new STORE): this is a single store's own
admin approving their own newly-added STAFF accounts. A staff account
created via POST /admin/users now starts verified=False and can't log in
until that store's admin approves it via POST /admin/users/{id}/verify.
Admin-role accounts are always implicitly verified -- there's no one else
in the store to approve them.

Like the other *_local.py tests, this runs fully offline: the real
FastAPI `app` in-process against an in-memory Mongo mock (mongomock-motor).
"""
import asyncio
import os
import uuid

os.environ.setdefault("MONGO_URL", "mongodb://localhost:27017")
os.environ.setdefault("DB_NAME", "test_staff_verification")
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


def test_store_admin_created_via_otp_flow_is_verified(client):
    admin = _register_store_admin(client, "ADMINVERIFIED")
    # Confirmed directly against the DB, not just the login response, since
    # login() itself would already reject an unverified staff account but
    # an admin account isn't gated by the verified check at all.
    doc = asyncio.run(srv.db.users.find_one({"id": admin["user"]["id"]}))
    assert doc["verified"] is True


def test_new_staff_starts_unverified_and_cannot_login(client):
    admin = _register_store_admin(client, "STAFFPENDING")
    admin_headers = _auth(admin["access_token"])

    un = f"staff_{uuid.uuid4().hex[:8]}"
    created = client.post(
        "/api/admin/users",
        json={"name": "New Staff", "username": un, "password": "Test@1234"},
        headers=admin_headers,
    )
    assert created.status_code == 200, created.text
    assert created.json()["verified"] is False

    listed = client.get("/api/admin/users", headers=admin_headers)
    row = [u for u in listed.json() if u["username"] == un][0]
    assert row["verified"] is False

    login_attempt = client.post("/api/auth/login", json={"username": un, "password": "Test@1234"})
    assert login_attempt.status_code == 403
    detail = login_attempt.json()["detail"]
    msg = detail if isinstance(detail, str) else detail.get("message", "")
    assert "pending approval" in msg.lower()


def test_admin_verify_unblocks_login_and_unverify_reblocks_it(client):
    admin = _register_store_admin(client, "STAFFVERIFYFLOW")
    admin_headers = _auth(admin["access_token"])

    un = f"staff_{uuid.uuid4().hex[:8]}"
    created = client.post(
        "/api/admin/users",
        json={"name": "Flow Staff", "username": un, "password": "Test@1234"},
        headers=admin_headers,
    )
    user_id = created.json()["id"]

    blocked = client.post("/api/auth/login", json={"username": un, "password": "Test@1234"})
    assert blocked.status_code == 403

    verify = client.post(f"/api/admin/users/{user_id}/verify", headers=admin_headers)
    assert verify.status_code == 200, verify.text
    assert verify.json() == {"ok": True, "id": user_id, "verified": True}

    allowed = client.post("/api/auth/login", json={"username": un, "password": "Test@1234"})
    assert allowed.status_code == 200, allowed.text
    assert allowed.json()["user"]["verified"] is True

    unverify = client.post(f"/api/admin/users/{user_id}/unverify", headers=admin_headers)
    assert unverify.status_code == 200, unverify.text
    assert unverify.json() == {"ok": True, "id": user_id, "verified": False}

    reblocked = client.post("/api/auth/login", json={"username": un, "password": "Test@1234"})
    assert reblocked.status_code == 403


def test_verify_endpoint_is_store_isolated(client):
    admin_a = _register_store_admin(client, "STOREA")
    admin_b = _register_store_admin(client, "STOREB")

    un = f"staff_{uuid.uuid4().hex[:8]}"
    created = client.post(
        "/api/admin/users",
        json={"name": "Store A Staff", "username": un, "password": "Test@1234"},
        headers=_auth(admin_a["access_token"]),
    )
    user_id = created.json()["id"]

    cross_store = client.post(f"/api/admin/users/{user_id}/verify", headers=_auth(admin_b["access_token"]))
    assert cross_store.status_code == 403

    same_store = client.post(f"/api/admin/users/{user_id}/verify", headers=_auth(admin_a["access_token"]))
    assert same_store.status_code == 200


def test_admin_accounts_cannot_be_unverified(client):
    admin = _register_store_admin(client, "PROTECTADMIN")
    r = client.post(f"/api/admin/users/{admin['user']['id']}/unverify", headers=_auth(admin["access_token"]))
    assert r.status_code == 400


def test_startup_backfill_grandfathers_preexisting_users_as_verified():
    # Simulates a staff account created before this feature shipped: no
    # "verified" key in the stored doc at all.
    pre_existing_id = srv.new_id()
    doc = {
        "id": pre_existing_id, "name": "Legacy Staff", "username": f"legacy_{uuid.uuid4().hex[:8]}",
        "password_hash": srv.hash_pw("Test@1234"), "password_enc": srv.encrypt_pw("Test@1234"),
        "role": "staff", "store_id": "some-store-id", "permissions": srv.STAFF_DEFAULT,
        "disabled": False, "created_at": srv.now_iso(),
        # deliberately no "verified" key
    }
    asyncio.run(srv.db.users.insert_one(dict(doc)))
    assert "verified" not in asyncio.run(srv.db.users.find_one({"id": pre_existing_id}))

    asyncio.run(srv.startup())

    refreshed = asyncio.run(srv.db.users.find_one({"id": pre_existing_id}))
    assert refreshed["verified"] is True, "pre-existing staff must never be locked out by this backfill"

    # A staff account that already has verified=False (created by the new
    # code, after this feature shipped) must be left untouched by the
    # backfill -- enrich-only, never overwrite.
    pending_id = srv.new_id()
    doc2 = {**doc, "id": pending_id, "username": f"pending_{uuid.uuid4().hex[:8]}", "verified": False}
    asyncio.run(srv.db.users.insert_one(dict(doc2)))
    asyncio.run(srv.startup())
    refreshed2 = asyncio.run(srv.db.users.find_one({"id": pending_id}))
    assert refreshed2["verified"] is False, "backfill must never overwrite an existing verified value"
