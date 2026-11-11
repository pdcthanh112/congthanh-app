from datetime import UTC, datetime

from fastapi.testclient import TestClient

from app import main
from app.domain.rules import Assessment, Decision, Hit
from app.ports import StoredAssessment
from app.service import Conflict


class StubService:
    def __init__(self):
        self.seen = None

    async def assess(self, tx):
        self.seen = tx
        return "a1", Assessment(60, Decision.REVIEW, (Hit("CARD_TESTING", 35, "x"), Hit("PAYMENT_FAILURES", 25, "y")))

    async def decide(self, aid, decision, by, note):
        if aid == "done":
            raise Conflict("review is already APPROVED")
        return StoredAssessment(
            aid, "c1", "u1", None, 1, "USD", 60, "REVIEW", [], "APPROVED", by, note, datetime.now(UTC)
        )


def client(monkeypatch):
    # Bỏ qua lifespan thật (Postgres/Redis): gắn service giả.
    stub = StubService()
    main.app.router.lifespan_context = _noop
    main.app.state.service = stub
    main.app.state.ready = _ready
    return TestClient(main.app), stub


async def _ready():
    return None


from contextlib import asynccontextmanager  # noqa: E402


@asynccontextmanager
async def _noop(_):
    yield


def test_assess(monkeypatch):
    c, stub = client(monkeypatch)
    with c:
        r = c.post(
            "/internal/fraud/assess",
            json={
                "checkoutId": "c1",
                "customerId": "u1",
                "amount": 100,
                "currency": "usd",
                "paymentMethod": "CARD",
                "shippingAddress": {"countryCode": "US", "line1": "x"},
                "lines": [{"productId": "p1", "quantity": 2, "unitPrice": 50}],
            },
        )
        assert r.status_code == 200, r.text
        d = r.json()["data"]
        assert d["decision"] == "REVIEW" and d["score"] == 60 and d["reasons"][0]["code"] == "CARD_TESTING"
        assert (
            stub.seen.currency == "USD" and stub.seen.shipping.country_code == "US" and stub.seen.lines[0].quantity == 2
        )
        bad = c.post("/internal/fraud/assess", json={"checkoutId": "c1"})
        assert bad.status_code == 400 and bad.json()["status"] == "Failed"


def test_backoffice_requires_role(monkeypatch):
    c, _ = client(monkeypatch)
    with c:
        assert c.post("/api/ecommerce/fraud/reviews/a1/decision", json={"decision": "APPROVE"}).status_code == 401
        main.app.dependency_overrides[main.auth.required] = lambda: main.Principal(
            subject="u9", roles=frozenset({"CUSTOMER"})
        )
        assert c.post("/api/ecommerce/fraud/reviews/a1/decision", json={"decision": "APPROVE"}).status_code == 403
        main.app.dependency_overrides[main.auth.required] = lambda: main.Principal(
            subject="ana", roles=frozenset({"FRAUD_ANALYST"})
        )
        r = c.post("/api/ecommerce/fraud/reviews/a1/decision", json={"decision": "APPROVE", "note": "ok"})
        assert r.status_code == 200 and r.json()["data"]["review"] == {
            "status": "APPROVED",
            "decidedBy": "ana",
            "note": "ok",
        }
        assert c.post("/api/ecommerce/fraud/reviews/done/decision", json={"decision": "APPROVE"}).status_code == 409
        main.app.dependency_overrides.clear()
