from fastapi.testclient import TestClient

from app import main


class StubService:
    async def translate(self, req):
        return [t.upper() for t in req.texts]


def test_translate_endpoints():
    with TestClient(main.app) as c:
        main.app.state.service = StubService()
        main.app.dependency_overrides[main.auth.required] = lambda: main.Principal(subject="u1")
        r = c.post("/api/ecommerce/translate", json={"texts": ["a", "b"], "targetLang": "en"})
        assert r.status_code == 200 and r.json()["data"]["translations"] == ["A", "B"]
        assert c.get("/api/ecommerce/translate/languages").json()["data"][0]["code"] == "en"
        r = c.post("/api/ecommerce/translate", json={"texts": [], "targetLang": "en"})
        assert r.status_code == 400 and r.json()["status"] == "Failed"
        main.app.dependency_overrides.clear()


def test_requires_auth():
    with TestClient(main.app) as c:
        main.app.state.service = StubService()
        r = c.post("/api/ecommerce/translate", json={"texts": ["a"], "targetLang": "en"})
        assert r.status_code == 401
