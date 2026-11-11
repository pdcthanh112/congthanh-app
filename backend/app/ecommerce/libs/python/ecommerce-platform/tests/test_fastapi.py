from typing import Annotated

from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from ecommerce_platform.auth import Principal
from ecommerce_platform.fastapi import Auth, install_error_handlers, ok


def test_roles_dependency():
    # verifier=None: chế độ dev, header X-User-Id tạo principal có vai trò ADMIN.
    auth = Auth(None)
    app = FastAPI()
    install_error_handlers(app)
    admin = auth.roles("ADMIN")
    seller = auth.roles("SELLER")

    @app.get("/admin")
    def a(p: Annotated[Principal, Depends(admin)]):
        return ok(p.subject)

    @app.get("/seller")
    def s(p: Annotated[Principal, Depends(seller)]):
        return ok(p.subject)

    c = TestClient(app)
    assert c.get("/admin").status_code == 401
    assert c.get("/admin", headers={"x-user-id": "u1"}).json()["data"] == "u1"
    r = c.get("/seller", headers={"x-user-id": "u1"})
    assert r.status_code == 403 and r.json()["message"] == "requires one of roles: SELLER"
