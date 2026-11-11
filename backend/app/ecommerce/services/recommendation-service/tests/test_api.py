from contextlib import asynccontextmanager

from fastapi.testclient import TestClient

from app import main
from app.domain.ranking import Reason, Scored


class Stub:
    def __init__(self):
        self.events = []
        self.for_you_subject = "unset"

    async def bought_together(self, pid, limit):
        return [Scored("b1", 0.123456, Reason.BOUGHT_TOGETHER)]

    async def similar(self, pid, limit):
        return []

    async def trending(self, limit, cat):
        return [Scored("t1", 2, Reason.TRENDING)]

    async def for_you(self, subject, limit):
        self.for_you_subject = subject
        return []

    async def record_event(self, subject, pid, kind):
        self.events.append((subject, pid, kind.value))


@asynccontextmanager
async def _noop(_):
    yield


async def _ready():
    return None


def client():
    stub = Stub()
    main.app.router.lifespan_context = _noop
    main.app.state.service = stub
    main.app.state.ready = _ready
    return TestClient(main.app), stub


def test_read_endpoints():
    c, stub = client()
    with c:
        r = c.get("/api/ecommerce/recommendations/products/p1/bought-together?limit=5")
        assert r.json()["data"] == [{"productId": "b1", "score": 0.1235, "reason": "BOUGHT_TOGETHER"}]
        assert c.get("/api/ecommerce/recommendations/trending").json()["data"][0]["reason"] == "TRENDING"
        assert c.get("/api/ecommerce/recommendations/trending?limit=99").status_code == 400
        c.get("/api/ecommerce/recommendations/for-you", headers={"X-Session-Id": "sess_12345678"})
        assert stub.for_you_subject == "s:sess_12345678"
        c.get("/api/ecommerce/recommendations/for-you", headers={"X-User-Id": "u1"})
        assert stub.for_you_subject == "u1"


def test_events():
    c, stub = client()
    with c:
        assert (
            c.post("/api/ecommerce/recommendations/events", json={"type": "VIEW", "productId": "p1"}).status_code == 400
        )
        r = c.post(
            "/api/ecommerce/recommendations/events",
            json={"type": "VIEW", "productId": "p1"},
            headers={"X-Session-Id": "sess_12345678"},
        )
        assert r.status_code == 202 and stub.events == [("s:sess_12345678", "p1", "VIEW")]
        bad = c.post(
            "/api/ecommerce/recommendations/events",
            json={"type": "PURCHASE", "productId": "p1"},
            headers={"X-User-Id": "u1"},
        )
        assert bad.status_code == 400
        assert c.post("/api/ecommerce/recommendations/rebuild").status_code == 401
