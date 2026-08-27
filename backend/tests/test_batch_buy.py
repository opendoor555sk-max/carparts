"""Tests for MULTIPLE BUY (batch buy) feature + regression.

Covered:
- Login abdul/Salam@123
- POST /api/buy called 3x for BATCHPN9 with override:true -> GET /api/parts/<pn> stock_count=3
- Regression: GET /api/categories total=73
- Regression: single buy works, sell decreases stock
"""
import os
import uuid
import pytest
import requests

BASE = os.environ["EXPO_PUBLIC_BACKEND_URL"].rstrip("/") + "/api"


@pytest.fixture(scope="module")
def token():
    r = requests.post(f"{BASE}/auth/login", json={"username": "abdul", "password": "Salam@123"}, timeout=30)
    assert r.status_code == 200, r.text
    body = r.json()
    assert "access_token" in body
    return body["access_token"]


@pytest.fixture(scope="module")
def auth(token):
    s = requests.Session()
    s.headers.update({"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    return s


# -------------- MULTIPLE BUY (batch) --------------
class TestBatchBuy:
    def test_batch_buy_three_times_increments_stock(self, auth):
        pn = f"BATCHPN9-{uuid.uuid4().hex[:6].upper()}"
        for i in range(3):
            r = auth.post(
                f"{BASE}/buy",
                json={"part_number": pn, "condition": "Unknown", "override": True},
                timeout=30,
            )
            assert r.status_code in (200, 201), f"attempt {i}: {r.status_code} {r.text}"

        g = auth.get(f"{BASE}/parts/{pn}", timeout=30)
        assert g.status_code == 200, g.text
        data = g.json()
        # stock_count must be 3 after 3 buys
        stock_count = data.get("stock_count") or data.get("stockCount") or len(data.get("units", []) or [])
        assert stock_count == 3, f"expected 3 stock units, got {stock_count}. Response: {data}"

    def test_batch_buy_exact_spec_batchpn9(self, auth):
        """Exact literal from spec: BATCHPN9 with 3 calls."""
        pn = "BATCHPN9"
        # Clean start not required — just count from GET before and after
        pre = auth.get(f"{BASE}/parts/{pn}", timeout=30)
        pre_count = 0
        if pre.status_code == 200:
            d = pre.json()
            pre_count = d.get("stock_count") or len(d.get("units", []) or [])
        for _ in range(3):
            r = auth.post(f"{BASE}/buy", json={"part_number": pn, "condition": "Unknown", "override": True}, timeout=30)
            assert r.status_code in (200, 201), r.text
        g = auth.get(f"{BASE}/parts/{pn}", timeout=30)
        assert g.status_code == 200
        d = g.json()
        after = d.get("stock_count") or len(d.get("units", []) or [])
        assert after - pre_count == 3, f"expected +3, got {after - pre_count} (pre={pre_count}, after={after})"


# -------------- Regression --------------
class TestRegression:
    def test_categories_73(self, auth):
        r = auth.get(f"{BASE}/categories", timeout=30)
        assert r.status_code == 200, r.text
        body = r.json()
        total = body.get("total") if isinstance(body, dict) else None
        if total is None and isinstance(body, list):
            total = len(body)
        assert total == 73, f"expected 73 categories, got {total}"

    def test_single_buy_then_sell_decreases_stock(self, auth):
        pn = f"TEST-REG-{uuid.uuid4().hex[:6].upper()}"
        # buy once
        r = auth.post(f"{BASE}/buy", json={"part_number": pn, "condition": "Unknown", "override": True}, timeout=30)
        assert r.status_code in (200, 201), r.text
        g = auth.get(f"{BASE}/parts/{pn}", timeout=30)
        assert g.status_code == 200
        d = g.json()
        before = d.get("stock_count") or len(d.get("units", []) or [])
        assert before >= 1

        # sell one
        s = auth.post(f"{BASE}/sell", json={"part_number": pn, "price": 100}, timeout=30)
        assert s.status_code in (200, 201), s.text

        g2 = auth.get(f"{BASE}/parts/{pn}", timeout=30)
        assert g2.status_code == 200
        d2 = g2.json()
        after = d2.get("stock_count") or len(d2.get("units", []) or [])
        assert after == before - 1, f"expected stock to decrease by 1: before={before}, after={after}"

    def test_login_ok(self, auth):
        r = auth.get(f"{BASE}/auth/me", timeout=30)
        assert r.status_code == 200
        assert r.json().get("username") == "abdul"
