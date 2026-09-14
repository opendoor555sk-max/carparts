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
mismatch) — see server.py's _acquire_buy_lock for the full writeup and the
fix (a per-store-per-part mutex serializing the critical section).

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
async def test_concurrent_buys_race_closed_across_punctuation_variants(monkeypatch):
    """The lock key must canonicalize punctuation/casing (see _buy_lock_id)
    — otherwise two concurrent buys spelled differently for the SAME real
    part (e.g. "954A0-CCAF0" typed vs "954A0CCAF0" scanned) would acquire
    two DIFFERENT locks and race exactly as before, even with the mutex in
    place."""
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
