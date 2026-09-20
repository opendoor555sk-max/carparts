"""Local, offline verification for the admin/super_admin permission-bypass
change (see server.py's require()/create_user/update_user).

Unlike the rest of tests/ (which hit a live preview deployment + real Mongo
over the network — unavailable in an offline/sandboxed run), this file spins
up the actual FastAPI `app` in-process against an in-memory Mongo mock
(mongomock_motor), so it can run with zero network access and no real
database. It exercises exactly the three behaviors this change touches:

1. An "admin" (store owner) can now create another "admin"-role user in
   their OWN store (previously silently downgraded to "staff").
2. An "admin" can now PATCH another admin-role user in their OWN store
   (previously blocked with 403 unless the caller was super_admin or
   editing themselves).
3. Store isolation is UNCHANGED: an admin still cannot create/edit users in
   a different store, and still cannot list all stores (super_admin-only)
   — confirming this change did not touch multi-tenant boundaries, per the
   explicit decision to keep those as-is.
"""
import os
import uuid

os.environ.setdefault("MONGO_URL", "mongodb://localhost:27017")
os.environ.setdefault("DB_NAME", "test_admin_bypass")
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


def _register(client, prefix):
    # /auth/register no longer exists -- store creation now goes through the
    # owner-approved OTP flow (store_requests). Drives the exact same
    # request -> owner generates OTP -> verify-otp sequence a real signup
    # would, using the platform seed account (abdul/Salam@123, which is also
    # the Owner per OWNER_CONTACT) to stand in for the owner approving it.
    suffix = uuid.uuid4().hex[:8]
    payload = {
        "store_name": f"TEST_{prefix}_{suffix}",
        "name": f"TEST Owner {prefix}",
        "username": f"test_{prefix}_{suffix}",
        "password": "Test@1234",
        "contact": "9999999999",
    }
    mobile = f"90000{uuid.uuid4().int % 100000:05d}"
    reqr = client.post("/api/store-requests", json={"name": payload["store_name"], "mobile": mobile})
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
    d = r.json()
    return {"payload": payload, "token": d["access_token"], "user": d["user"]}


def _auth(token):
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture(scope="module")
def store_a(client):
    return _register(client, "A")


@pytest.fixture(scope="module")
def store_b(client):
    return _register(client, "B")


@pytest.fixture(scope="module")
def super_token(client):
    r = client.post("/api/auth/login", json={"username": "abdul", "password": "Salam@123"})
    assert r.status_code == 200, r.text
    d = r.json()
    assert d["user"]["role"] == "super_admin"
    return d["access_token"]


class TestAdminCanCreateAdmin:
    """Was: forced to 'staff'. Now: admin creating admin (own store) works."""

    def test_store_admin_can_now_create_admin(self, client, store_a):
        un = f"test_newadmin_{uuid.uuid4().hex[:6]}"
        r = client.post(
            "/api/admin/users",
            json={"name": "New Admin", "username": un, "password": "Test@1234", "role": "admin"},
            headers=_auth(store_a["token"]),
        )
        assert r.status_code == 200, r.text
        d = r.json()
        assert d["role"] == "admin", f"expected admin role bypass to work, got {d['role']}"
        assert d["store_id"] == store_a["user"]["store_id"]
        assert d["permissions"] == srv.ALL_PERMISSIONS, "admin-role users must carry full permissions"

    def test_staff_still_cannot_create_admin(self, client, store_a):
        # A plain staff account (created without role="admin") must still be
        # forced to staff when it tries to spawn an "admin" — the bypass is
        # for admin/super_admin actors only, not staff.
        un = f"test_staffcreator_{uuid.uuid4().hex[:6]}"
        r = client.post(
            "/api/admin/users",
            json={"name": "Plain Staff", "username": un, "password": "Test@1234"},
            headers=_auth(store_a["token"]),
        )
        assert r.status_code == 200, r.text
        staff_token = client.post(
            "/api/auth/login", json={"username": un, "password": "Test@1234"}
        ).json()["access_token"]

        un2 = f"test_staffmadeadmin_{uuid.uuid4().hex[:6]}"
        r2 = client.post(
            "/api/admin/users",
            json={"name": "Should Be Staff", "username": un2, "password": "Test@1234", "role": "admin"},
            headers=_auth(staff_token),
        )
        # staff has no manage_users permission by default -> 403 from require()
        assert r2.status_code == 403


class TestAdminCanEditAdmin:
    """Was: 403 unless super_admin or editing self. Now: admin can edit
    another admin-role account within their OWN store."""

    def test_store_admin_can_now_edit_same_store_admin(self, client, store_a):
        un = f"test_editable_admin_{uuid.uuid4().hex[:6]}"
        created = client.post(
            "/api/admin/users",
            json={"name": "Editable Admin", "username": un, "password": "Test@1234", "role": "admin"},
            headers=_auth(store_a["token"]),
        )
        assert created.status_code == 200, created.text
        target_id = created.json()["id"]

        r = client.patch(
            f"/api/admin/users/{target_id}",
            json={"name": "Renamed By Peer Admin"},
            headers=_auth(store_a["token"]),
        )
        assert r.status_code == 200, r.text
        assert r.json()["name"] == "Renamed By Peer Admin"


class TestStoreIsolationUnchanged:
    """The explicit decision was: keep store isolation as-is. These confirm
    the bypass did NOT leak across stores."""

    def test_cross_store_user_edit_still_blocked(self, client, store_a, store_b):
        un = f"test_cross_{uuid.uuid4().hex[:6]}"
        rb = client.post(
            "/api/admin/users",
            json={"name": "B Staff", "username": un, "password": "Test@1234"},
            headers=_auth(store_b["token"]),
        )
        assert rb.status_code == 200, rb.text
        target_id = rb.json()["id"]

        r = client.patch(
            f"/api/admin/users/{target_id}",
            json={"name": "hijacked"},
            headers=_auth(store_a["token"]),
        )
        assert r.status_code == 403

    def test_cross_store_admin_edit_still_blocked(self, client, store_a, store_b):
        # Even with the new same-store admin-editing-admin bypass, store A's
        # admin must still be unable to touch store B's admin account.
        r = client.patch(
            f"/api/admin/users/{store_b['user']['id']}",
            json={"name": "hijacked owner"},
            headers=_auth(store_a["token"]),
        )
        assert r.status_code == 403

    def test_store_admin_still_cannot_list_all_stores(self, client, store_a):
        r = client.get("/api/admin/stores", headers=_auth(store_a["token"]))
        assert r.status_code == 403

    def test_super_admin_can_list_all_stores(self, client, super_token, store_a, store_b):
        r = client.get("/api/admin/stores", headers=_auth(super_token))
        assert r.status_code == 200
        ids = {s["id"] for s in r.json()}
        assert store_a["user"]["store_id"] in ids
        assert store_b["user"]["store_id"] in ids


class TestPermissionSlugBypassAlreadyWorks:
    """Layer 1 (permission-slug system) — confirms it was already fully
    bypassed for admin/super_admin before this change, and still is."""

    def test_admin_has_every_permission_slug(self, store_a):
        assert set(store_a["user"]["permissions"]) == set(srv.ALL_PERMISSIONS)

    def test_super_admin_has_every_permission_slug(self, client, super_token):
        r = client.get("/api/auth/me", headers=_auth(super_token))
        assert r.status_code == 200
        assert set(r.json()["permissions"]) == set(srv.ALL_PERMISSIONS)
