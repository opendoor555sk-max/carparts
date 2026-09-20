"""Regression test for the keep-alive health check endpoint.

An external uptime service pings this every 5 minutes to stop the Render
free-tier backend from spinning down after inactivity (avoiding the
20s-2min cold-start delay on the next real request). It must stay public
(no auth), fast, and independent of the database -- a DB hiccup should
never make the keep-alive ping itself fail and defeat the point of pinging
it in the first place.

Like the other *_local.py tests, this runs fully offline: the real
FastAPI `app` in-process against an in-memory Mongo mock (mongomock-motor).
"""
import os

os.environ.setdefault("MONGO_URL", "mongodb://localhost:27017")
os.environ.setdefault("DB_NAME", "test_health")
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


def test_health_returns_ok_with_no_auth(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}


def test_health_is_not_under_the_api_prefix(client):
    # Registered on `app` directly, not the `/api`-prefixed router -- the
    # real path is /health, not /api/health.
    r = client.get("/api/health")
    assert r.status_code == 404


def test_health_endpoint_body_has_no_db_calls():
    # Static guarantee, not just behavioral: the handler itself must never
    # reference the db object, so a database outage can never take the
    # keep-alive ping down with it.
    import inspect
    source = inspect.getsource(srv.health)
    assert "db." not in source and "db[" not in source
