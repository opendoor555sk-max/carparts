"""Regression test for a cross-tenant data leak in the platform Owner/
super_admin seeding logic (see server.py's @app.on_event("startup")).

Root cause: the seed block that (re-)establishes the platform's own account
as super_admin on every startup matched purely by username — `db.users.
find_one({"username": ADMIN_USERNAME})` (default "abdul") — then
unconditionally set role="super_admin" (and, after the Owner-role change,
contact=OWNER_CONTACT too) on WHATEVER document it found. usernames are
globally unique, so if any tenant (a store's own admin) had ever already
created a staff or admin account named "abdul" — very plausible, since it's
this app's own documented demo/default username, not an obviously reserved
one — the NEXT startup would find THAT account by the same username lookup
(nothing else with that name would exist yet) and silently promote it,
instantly breaking its store scoping: resolve_store() gives a super_admin a
cross-store god-view when no store_id is specified, which is exactly "a
staff user can see data belonging to other stores too".

Fixed by keying the seed lookup on a dedicated `platform_seed: True` marker
set only by this block, never reachable from any user-facing endpoint —
a tenant account can no longer match it no matter what it's named.

Like the other *_local.py tests, this runs fully offline against an
in-memory Mongo mock (mongomock-motor) with the real FastAPI app in-process.
Unlike them, it deliberately seeds a colliding tenant user directly into the
mock DB BEFORE the app's first startup() ever runs — reproducing the actual
bug ordering (the tenant account already existed when startup() first ran
against it), which is the part a fixture built around TestClient's own
already-completed startup can't otherwise reproduce (the genuine seed
account would already own the username by the time a test tries to create
a colliding one through the API).
"""
import asyncio
import os
import uuid

os.environ.setdefault("MONGO_URL", "mongodb://localhost:27017")
os.environ.setdefault("DB_NAME", "test_owner_seed_isolation")
os.environ.setdefault("JWT_SECRET", "test-secret-not-for-prod")
os.environ.setdefault("FERNET_KEY", "kM3vQe8yV3nq9x8h2b0Zt9mE1Fq4Yx2G8oB7dS6cR0k=")
os.environ.setdefault("ADMIN_USERNAME", "abdul")
os.environ.setdefault("ADMIN_PASSWORD", "Salam@123")

import pytest
from fastapi.testclient import TestClient
from mongomock_motor import AsyncMongoMockClient

import server as srv

srv.db = AsyncMongoMockClient()[os.environ["DB_NAME"]]

SEED_USERNAME = os.environ["ADMIN_USERNAME"]
COLLIDING_PASSWORD = "Test@1234"
STORE_ID = f"store_{uuid.uuid4().hex[:8]}"
OTHER_STORE_ID = f"store_other_{uuid.uuid4().hex[:8]}"


async def _seed_colliding_tenant_data():
    """Directly inserts a tenant store + a staff user named exactly like the
    reserved seed username — bypassing the API so this exists BEFORE the
    app's very first startup() call, matching the real bug's ordering."""
    await srv.db.stores.insert_one({
        "id": STORE_ID, "name": "New India", "owner_username": "newindia_owner",
        "contact": "+911111111111", "created_at": srv.now_iso(), "status": "active",
    })
    await srv.db.users.insert_one({
        "id": srv.new_id(), "name": "Staff Abdul", "username": SEED_USERNAME,
        "password_hash": srv.hash_pw(COLLIDING_PASSWORD), "password_enc": srv.encrypt_pw(COLLIDING_PASSWORD),
        "role": "staff", "store_id": STORE_ID, "permissions": srv.STAFF_DEFAULT,
        "disabled": False, "created_at": srv.now_iso(),
    })
    # A second, unrelated store with its own data, to prove isolation
    # concretely rather than just checking role/store_id fields in isolation.
    await srv.db.stores.insert_one({
        "id": OTHER_STORE_ID, "name": "Other Store", "owner_username": "other_owner",
        "contact": "+912222222222", "created_at": srv.now_iso(), "status": "active",
    })
    await srv.db.parts.insert_one({
        "id": srv.new_id(), "store_id": OTHER_STORE_ID, "part_number": "OTHERSTORE-PART",
        "company": "All", "name": "", "category": "", "compatible_vehicles": [], "variant": "",
        "verification_status": "Unverified", "created_at": srv.now_iso(),
    })


asyncio.run(_seed_colliding_tenant_data())


@pytest.fixture(scope="module")
def client():
    # Entering this triggers startup() for the FIRST time against a DB that
    # already contains the colliding tenant account above.
    with TestClient(srv.app) as c:
        yield c


def _auth(token):
    return {"Authorization": f"Bearer {token}"}


def test_preexisting_tenant_account_named_like_seed_username_is_not_promoted(client):
    login = client.post("/api/auth/login", json={"username": SEED_USERNAME, "password": COLLIDING_PASSWORD})
    assert login.status_code == 200, login.text
    body = login.json()
    assert body["user"]["role"] == "staff", (
        f"tenant staff account was promoted to {body['user']['role']!r} by the startup seed logic — "
        "this is the cross-tenant leak"
    )
    assert body["user"]["store_id"] == STORE_ID
    assert not body["user"].get("contact"), "tenant staff account must never carry the Owner contact"

    staff_headers = _auth(body["access_token"])
    parts = client.get("/api/parts", headers=staff_headers)
    assert parts.status_code == 200, parts.text
    seen_store_ids = {p["store_id"] for p in parts.json()}
    assert seen_store_ids <= {STORE_ID}, f"staff account saw part(s) from other store(s): {seen_store_ids - {STORE_ID}}"
    seen_pns = {p["part_number"] for p in parts.json()}
    assert "OTHERSTORE-PART" not in seen_pns, "staff account can see another store's part"


def test_genuine_platform_account_still_becomes_super_admin(client):
    # The real platform account must still be seeded and functional — this
    # change must not weaken/remove the legitimate seeding, only stop it
    # from matching the wrong document. The exact username the fix falls
    # back to when ADMIN_USERNAME is already taken by a tenant isn't part of
    # this test's contract — assert via the platform_seed marker instead,
    # which is, then confirm that account can actually log in.
    seeded = asyncio.run(srv.db.users.find_one({"platform_seed": True}))
    assert seeded is not None, "no platform_seed account was created at all"
    assert seeded["role"] == "super_admin"
    assert seeded.get("contact") == srv.OWNER_CONTACT
    assert seeded.get("store_id") is None

    login = client.post(
        "/api/auth/login",
        json={"username": seeded["username"], "password": os.environ["ADMIN_PASSWORD"]},
    )
    assert login.status_code == 200, login.text
    assert login.json()["user"]["role"] == "super_admin"
