"""Regression test for the purchase-limit race condition.

Two earlier fixes (ba366de, ce7f981) addressed *identity* bugs: a limit set
under one punctuation/casing spelling of a part number not matching stock
recorded under a different spelling of the same real part. Both assumed
/buy's check-then-insert (read existing_stock via compute_limit(), decide,
THEN db.stock.insert_one()) was itself safe. It wasn't — nothing serialized
two /buy calls for the same part, so two concurrent requests could both read
"under limit" before either write committed, and both insert, exceeding the
configured limit with no error to either caller. This is a genuinely
different root cause from the two prior fixes (a timing race, not a data
mismatch).

bb81986 first fixed this with a mutex in a separate db.locks collection,
racing a brand-new unique index on "id" created at startup. That passed
every test here locally, but the bug persisted in production — and
startup()'s index creation is wrapped in a bare try/except that only logs a
warning on failure. If that CREATE INDEX ever silently failed on the live
database, db.locks.insert_one() would simply never raise DuplicateKeyError
and the "lock" would become a silent no-op, reopening the exact same race
with no error anywhere to point at it. Rebuilt to remove that dependency
entirely: the mutex now lives ON the part document itself (a
buy_lock_until field), guarded by the (store_id, part_number) unique index
on db.parts that predates this feature by a long way — see server.py's
_acquire_part_buy_lock for the full writeup.

Like test_admin_bypass_local.py, this runs fully offline: the actual
FastAPI `app` in-process against an in-memory Mongo mock (mongomock-motor),
so it needs no network access and no real database — unlike tests/ which
hits a live preview deployment.
"""
import asyncio
import os
import uuid

os.environ.setdefault("MONGO_URL", "mongodb://localhost:27017")
os.environ.setdefault("DB_NAME", "test_purchase_limit_race")
os.environ.setdefault("JWT_SECRET", "test-secret-not-for-prod")
os.environ.setdefault("FERNET_KEY", "kM3vQe8yV3nq9x8h2b0Zt9mE1Fq4Yx2G8oB7dS6cR0k=")

import httpx
import pytest
import pytest_asyncio
from mongomock_motor import AsyncMongoMockClient

import server as srv

srv.db = AsyncMongoMockClient()[os.environ["DB_NAME"]]


@pytest_asyncio.fixture(autouse=True, loop_scope="module")
async def _startup():
    # Idempotent (index creation, super-admin seed check, best-effort storage
    # init) — safe to run once per test in this module.
    await srv.startup()


async def _register_and_set_limit(client: httpx.AsyncClient, limit: int):
    suffix = uuid.uuid4().hex[:8]
    reg = await client.post("/api/auth/register", json={
        "store_name": f"RACE_{suffix}", "name": "Race Owner",
        "username": f"race_{suffix}", "password": "Test@1234", "contact": "9999999999",
    })
    assert reg.status_code == 200, reg.text
    token = reg.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}
    pn = f"RACEPN{suffix.upper()}"
    r = await client.post("/api/limits/part", json={"part_number": pn, "limit": limit, "enabled": True},
                          headers=headers)
    assert r.status_code == 200, r.text
    assert r.json()["allowed_limit"] == limit
    return headers, pn


@pytest.mark.asyncio
async def test_concurrent_buys_never_exceed_limit_under_realistic_latency(monkeypatch):
    """The core regression case: without the fix, this reliably lets every
    concurrent request through (reproduced pre-fix: 12/12 succeeded against
    a limit of 5). Injects a delay into compute_limit() to simulate real
    network/DB latency between the read and /buy's insert — mongomock's
    in-memory ops otherwise resolve too fast for asyncio.gather to actually
    interleave two requests' critical sections, masking the race in a naive
    test the same way it was masked in production ("sometimes blocks,
    sometimes doesn't" rather than a clean reproduction)."""
    orig_compute_limit = srv.compute_limit

    async def delayed_compute_limit(*a, **kw):
        result = await orig_compute_limit(*a, **kw)
        await asyncio.sleep(0.05)
        return result

    monkeypatch.setattr(srv, "compute_limit", delayed_compute_limit)

    transport = httpx.ASGITransport(app=srv.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        headers, pn = await _register_and_set_limit(client, limit=5)

        N = 12
        results = await asyncio.gather(*[
            client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=headers)
            for _ in range(N)
        ])
        oks = [r for r in results if r.status_code == 200]
        blocked = [r for r in results if r.status_code == 409]
        assert len(oks) == 5, f"expected exactly 5 buys to succeed against limit=5, got {len(oks)}"
        assert len(blocked) == N - 5
        for r in blocked:
            assert r.json()["detail"]["code"] == "LIMIT_REACHED"

        inv = await client.get("/api/inventory", headers=headers)
        assert inv.status_code == 200
        assert len(inv.json()) == 5, "actual stock docs must match the limit, not the request count"


@pytest.mark.asyncio
async def test_concurrent_buys_never_exceed_limit_fast_path():
    """Same scenario with no injected delay — sanity-checks the lock doesn't
    depend on the artificial latency above to do its job."""
    transport = httpx.ASGITransport(app=srv.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        headers, pn = await _register_and_set_limit(client, limit=3)

        results = await asyncio.gather(*[
            client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=headers)
            for _ in range(10)
        ])
        oks = [r for r in results if r.status_code == 200]
        assert len(oks) == 3

        inv = await client.get("/api/inventory", headers=headers)
        assert len(inv.json()) == 3


@pytest.mark.asyncio
async def test_sequential_buys_still_block_correctly():
    """The Multiple-Buy draft-confirm flow's own loop is sequential (verified
    directly in buy.tsx/buy.web.tsx — a `for` loop that awaits each /buy),
    so this isn't the race itself, but confirms the new per-part lock
    acquire/release cycles cleanly across repeated sequential calls from one
    caller and doesn't itself introduce a hang or a stuck lock."""
    transport = httpx.ASGITransport(app=srv.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        headers, pn = await _register_and_set_limit(client, limit=2)

        statuses = []
        for _ in range(5):
            r = await client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=headers)
            statuses.append(r.status_code)
        assert statuses == [200, 200, 409, 409, 409]


@pytest.mark.asyncio
async def test_race_closed_without_any_db_locks_index(monkeypatch):
    """Directly validates the reason for the redesign: bb81986's mutex lived
    in db.locks and depended on a unique index created at startup succeeding
    — a dependency that, per the investigation, may have silently failed in
    production and reopened the race with zero visible error. The current
    design doesn't touch db.locks for this at all, so the race must stay
    closed even with ZERO indexes on it (simulating that exact failure mode
    as the worst case, rather than just asserting the collection is
    untouched)."""
    await srv.db.locks.drop_indexes()

    orig_compute_limit = srv.compute_limit

    async def delayed_compute_limit(*a, **kw):
        result = await orig_compute_limit(*a, **kw)
        await asyncio.sleep(0.05)
        return result

    monkeypatch.setattr(srv, "compute_limit", delayed_compute_limit)

    transport = httpx.ASGITransport(app=srv.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        headers, pn = await _register_and_set_limit(client, limit=5)

        results = await asyncio.gather(*[
            client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=headers)
            for _ in range(12)
        ])
        oks = [r for r in results if r.status_code == 200]
        assert len(oks) == 5, f"expected exactly 5 buys to succeed with db.locks unindexed, got {len(oks)}"

        inv = await client.get("/api/inventory", headers=headers)
        assert len(inv.json()) == 5


@pytest.mark.asyncio
async def test_concurrent_brand_new_part_buys_dont_500():
    """_ensure_part_for_buy's DuplicateKeyError handling: two concurrent
    /buy calls for a part number NEVER SEEN BEFORE (no existing db.parts
    doc) race to create it. Without the catch-and-refetch, the loser's
    insert_one against the pre-existing (store_id, part_number) unique
    index would raise an uncaught DuplicateKeyError -> 500, rather than
    proceeding normally."""
    transport = httpx.ASGITransport(app=srv.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        suffix = uuid.uuid4().hex[:8]
        reg = await client.post("/api/auth/register", json={
            "store_name": f"NEWPART_{suffix}", "name": "New Part Owner",
            "username": f"newpart_{suffix}", "password": "Test@1234", "contact": "9999999999",
        })
        headers = {"Authorization": f"Bearer {reg.json()['access_token']}"}
        pn = f"BRANDNEW{suffix.upper()}"  # no limit set, never bought before

        results = await asyncio.gather(*[
            client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=headers)
            for _ in range(8)
        ])
        statuses = [r.status_code for r in results]
        assert all(s == 200 for s in statuses), f"expected no errors (no limit set), got {statuses}"

        parts = await srv.db.parts.count_documents({"store_id": reg.json()["user"]["store_id"], "part_number": pn})
        assert parts == 1, "concurrent first-buys must converge on exactly one part doc, not fragment"

        inv = await client.get("/api/inventory", headers=headers)
        assert len(inv.json()) == 8


@pytest.mark.asyncio
async def test_sequential_buys_with_realistic_delay_reread_fresh_stock():
    """Targets the "misreads current stock count" hypothesis directly:
    sequential, non-concurrent buys with real think-time between them
    (simulating a staff member scanning items one at a time) must each
    re-read the true current count fresh — no caching anywhere in
    compute_limit(). Also changes the limit mid-stream (lowering it) and
    confirms the very next buy immediately respects the NEW value, ruling
    out a stale first-read of the limit itself."""
    transport = httpx.ASGITransport(app=srv.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        headers, pn = await _register_and_set_limit(client, limit=5)

        statuses = []
        for _ in range(3):
            r = await client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=headers)
            statuses.append(r.status_code)
            await asyncio.sleep(0.02)  # real think-time, not back-to-back
        assert statuses == [200, 200, 200]

        # Lower the limit to the current stock level — the very next buy
        # must block immediately, proving compute_limit() re-reads both the
        # limit AND the stock count fresh rather than a value cached from
        # the /limits/part call or an earlier /buy in this same session.
        r = await client.post("/api/limits/part", json={"part_number": pn, "limit": 3, "enabled": True},
                              headers=headers)
        assert r.status_code == 200
        assert r.json()["status"] == "STOP", "lowering the limit to == current stock must show STOP immediately"

        r = await client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=headers)
        assert r.status_code == 409, "first buy after a limit change must respect the NEW limit, not a stale one"

        # Raise it again — buys should resume immediately, no leftover state.
        r = await client.post("/api/limits/part", json={"part_number": pn, "limit": 4, "enabled": True},
                              headers=headers)
        assert r.status_code == 200
        r = await client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=headers)
        assert r.status_code == 200
        r = await client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=headers)
        assert r.status_code == 409

        inv = await client.get("/api/inventory", headers=headers)
        assert len(inv.json()) == 4


@pytest.mark.asyncio
async def test_concurrent_buys_race_closed_across_punctuation_variants(monkeypatch):
    """The lock lives on the canonical part document (see
    _ensure_part_for_buy/_acquire_part_buy_lock) — so two concurrent buys
    spelled differently for the SAME real part (e.g. "954A0-CCAF0" typed vs
    "954A0CCAF0" scanned) must resolve to that one document and share its
    lock, not race past each other under two different identities."""
    orig_compute_limit = srv.compute_limit

    async def delayed_compute_limit(*a, **kw):
        result = await orig_compute_limit(*a, **kw)
        await asyncio.sleep(0.05)
        return result

    monkeypatch.setattr(srv, "compute_limit", delayed_compute_limit)

    transport = httpx.ASGITransport(app=srv.app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        suffix = uuid.uuid4().hex[:6]
        reg = await client.post("/api/auth/register", json={
            "store_name": f"RACEV_{suffix}", "name": "Race Owner",
            "username": f"racev_{suffix}", "password": "Test@1234", "contact": "9999999999",
        })
        headers = {"Authorization": f"Bearer {reg.json()['access_token']}"}
        hyphenated = f"954{suffix.upper()}-CCAF0"
        bare = f"954{suffix.upper()}CCAF0"  # same real part, no separator

        r = await client.post("/api/limits/part", json={"part_number": hyphenated, "limit": 4, "enabled": True},
                              headers=headers)
        assert r.status_code == 200, r.text

        # Fire buys under BOTH spellings concurrently, interleaved.
        payloads = ([hyphenated] * 6) + ([bare] * 6)
        results = await asyncio.gather(*[
            client.post("/api/buy", json={"part_number": pn, "condition": "Working"}, headers=headers)
            for pn in payloads
        ])
        oks = [r for r in results if r.status_code == 200]
        assert len(oks) == 4, f"expected exactly 4 buys across both spellings, got {len(oks)}"

        inv = await client.get("/api/inventory", headers=headers)
        assert len(inv.json()) == 4
