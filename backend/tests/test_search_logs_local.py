"""Regression test for the search-activity log (who searched what, when,
from where) surfaced to store admins (/admin/search-logs) and the platform
Owner (/owner/search-logs).

db.search_logs is a NEW, separate collection from db.search_history (an
existing per-part-per-store AGGREGATE counter used by /demand and
/search-history -- count/last_searched/last_status, no per-event
who/when/where at all), confirmed by reading server.py before adding
anything: this feature has no existing mechanism to reuse for the
per-event log itself, only the existing _parse_gps()/"lat,lng" string
convention (already used for requirements.gps and stock.location.gps,
surfaced today via admin_gps_locations()), which this reuses rather than
inventing a new location format.

Like the other *_local.py tests, this runs fully offline: the real
FastAPI `app` in-process against an in-memory Mongo mock (mongomock-motor).
"""
import os
import uuid

os.environ.setdefault("MONGO_URL", "mongodb://localhost:27017")
os.environ.setdefault("DB_NAME", "test_search_logs")
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


def _owner_headers(client):
    owner_login = client.post("/api/auth/login", json={"username": "abdul", "password": "Salam@123"})
    assert owner_login.status_code == 200, owner_login.text
    return _auth(owner_login.json()["access_token"])


def test_search_logs_is_a_separate_collection_from_search_history(client):
    admin = _register_store_admin(client, "SEARCHLOGSEP")
    admin_headers = _auth(admin["access_token"])
    pn = f"SEP-{uuid.uuid4().hex[:8].upper()}"

    r = client.get(f"/api/search?q={pn}", headers=admin_headers)
    assert r.status_code == 200, r.text

    hist = client.get("/api/search-history", headers=admin_headers).json()
    hist_row = [h for h in hist if h["part_number"] == pn][0]
    assert "count" in hist_row and "user_id" not in hist_row, (
        "search_history is the pre-existing aggregate counter, not a per-event log"
    )

    logs = client.get("/api/admin/search-logs", headers=admin_headers).json()
    log_row = [l for l in logs["items"] if l["part_number_searched"] == pn][0]
    assert log_row["user_id"] == admin["user"]["id"]
    assert log_row["user_name"] == admin["user"]["name"]
    assert "created_at" in log_row


def test_search_and_parts_both_log_with_correct_gps_handling(client):
    admin = _register_store_admin(client, "SEARCHLOGGPS")
    admin_headers = _auth(admin["access_token"])

    pn = f"GPS-{uuid.uuid4().hex[:8].upper()}"
    r = client.get(f"/api/search?q={pn}&gps=12.9716,77.5946", headers=admin_headers)
    assert r.status_code == 200, r.text

    logs = client.get("/api/admin/search-logs", headers=admin_headers).json()
    row = [l for l in logs["items"] if l["part_number_searched"] == pn][0]
    assert row["gps"] == "12.9716,77.5946"
    assert row["gps_coord"] == {"lat": 12.9716, "lng": 77.5946}

    # /parts (Catalog browse) never captures GPS -- no new permission
    # prompt is added anywhere for it, so its log rows must have no location.
    client.get("/api/parts?category=Sensors", headers=admin_headers)
    logs2 = client.get("/api/admin/search-logs", headers=admin_headers).json()
    browse_rows = [l for l in logs2["items"] if l["part_number_searched"] == "(browse: Sensors)"]
    assert len(browse_rows) == 1
    assert browse_rows[0]["gps"] is None
    assert browse_rows[0]["gps_coord"] is None


def test_admin_search_logs_is_store_isolated_and_paginated(client):
    admin_a = _register_store_admin(client, "LOGSTOREA")
    admin_b = _register_store_admin(client, "LOGSTOREB")

    client.get(f"/api/search?q=AAA-{uuid.uuid4().hex[:6]}", headers=_auth(admin_a["access_token"]))
    client.get(f"/api/search?q=BBB-{uuid.uuid4().hex[:6]}", headers=_auth(admin_b["access_token"]))

    logs_a = client.get("/api/admin/search-logs", headers=_auth(admin_a["access_token"])).json()
    assert all(row["store_id"] == admin_a["user"]["store_id"] for row in logs_a["items"])
    assert not any(row["part_number_searched"].startswith("BBB-") for row in logs_a["items"])

    paged = client.get("/api/admin/search-logs?page=1&page_size=1", headers=_auth(admin_a["access_token"])).json()
    assert len(paged["items"]) == 1
    assert paged["page"] == 1
    assert paged["page_size"] == 1
    assert paged["total"] >= 1


def test_admin_search_logs_requires_manage_users_permission(client):
    admin = _register_store_admin(client, "LOGPERM")
    admin_headers = _auth(admin["access_token"])
    un = f"staff_{uuid.uuid4().hex[:8]}"
    created = client.post(
        "/api/admin/users", json={"name": "Plain Staff", "username": un, "password": "Test@1234"},
        headers=admin_headers,
    )
    client.post(f"/api/admin/users/{created.json()['id']}/verify", headers=admin_headers)
    staff_login = client.post("/api/auth/login", json={"username": un, "password": "Test@1234"})
    staff_headers = _auth(staff_login.json()["access_token"])

    r = client.get("/api/admin/search-logs", headers=staff_headers)
    assert r.status_code == 403


def test_owner_search_logs_spans_every_store(client):
    admin_a = _register_store_admin(client, "OWNERLOGA")
    admin_b = _register_store_admin(client, "OWNERLOGB")
    pn_a = f"OA-{uuid.uuid4().hex[:8].upper()}"
    pn_b = f"OB-{uuid.uuid4().hex[:8].upper()}"
    client.get(f"/api/search?q={pn_a}", headers=_auth(admin_a["access_token"]))
    client.get(f"/api/search?q={pn_b}", headers=_auth(admin_b["access_token"]))

    logs = client.get("/api/owner/search-logs", headers=_owner_headers(client)).json()
    found_pns = {row["part_number_searched"] for row in logs["items"]}
    assert pn_a in found_pns
    assert pn_b in found_pns
    a_row = [r for r in logs["items"] if r["part_number_searched"] == pn_a][0]
    assert a_row["store_name"] == admin_a["user"]["store_name"]


def test_owner_search_logs_requires_owner(client):
    admin = _register_store_admin(client, "NOTOWNERLOG")
    r = client.get("/api/owner/search-logs", headers=_auth(admin["access_token"]))
    assert r.status_code == 403
