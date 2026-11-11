import json

import httpx
import pytest

from app.adapters.http import CartClient, CatalogClient, OrderClient
from app.adapters.openfga import OpenFgaAuthorizer
from app.domain import Forbidden, NotFound, Tuple, Unavailable


def client(handler, base="http://x"):
    return httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url=base)


async def test_cart_default_normalizes_and_forwards_token():
    def handler(req: httpx.Request):
        assert req.headers["authorization"] == "Bearer t"
        assert req.url.raw_path == b"/api/ecommerce/carts/customer/u%201/default"
        data = {
            "id": 3,
            "customerId": "u 1",
            "isDefault": True,
            "cartItems": [{"id": 1, "productId": "p", "quantity": 2}],
        }
        return httpx.Response(200, json={"data": data})

    cart = await CartClient(client(handler)).default_cart("t", "u 1")
    assert cart == {
        "id": 3,
        "customerId": "u 1",
        "isDefault": True,
        "items": [{"id": 1, "productId": "p", "quantity": 2}],
    }


async def test_cart_default_missing_is_none_and_errors_map():
    assert await CartClient(client(lambda r: httpx.Response(404))).default_cart("t", "u") is None
    with pytest.raises(Forbidden):
        await CartClient(client(lambda r: httpx.Response(403))).get("t", 1)
    with pytest.raises(Unavailable):
        await CartClient(client(lambda r: httpx.Response(502))).get("t", 1)


async def test_catalog_search_filters_inactive():
    def handler(req):
        hits = [{"id": "1", "name": "A", "status": "ACTIVE"}, {"id": "2", "name": "B", "status": "DRAFT"}]
        return httpx.Response(200, json={"data": {"hits": hits}})

    out = await CatalogClient(client(handler), client(lambda r: httpx.Response(404))).search("a", 5)
    assert [p["id"] for p in out] == ["1"]
    with pytest.raises(NotFound):
        await CatalogClient(client(handler), client(lambda r: httpx.Response(404))).detail("x")


async def test_order_cancel_posts_reason():
    seen = {}

    def handler(req):
        seen["path"], seen["body"] = req.url.raw_path.decode(), json.loads(req.content)
        return httpx.Response(204)

    await OrderClient(client(handler)).cancel("t", "OD/1", "x")
    assert seen == {"path": "/api/ecommerce/orders/OD%2F1/cancel", "body": {"reason": "x"}}


async def test_openfga_check_body_and_fail_closed():
    def handler(req):
        body = json.loads(req.content)
        assert req.url.path == "/stores/S/check"
        assert body["tuple_key"] == {"user": "user:u", "relation": "can_view", "object": "cart:1"}
        assert body["contextual_tuples"]["tuple_keys"][0]["relation"] == "owner"
        assert body["authorization_model_id"] == "M"
        return httpx.Response(200, json={"allowed": True})

    fga = OpenFgaAuthorizer(client(handler), "S", "M")
    assert await fga.check("user:u", "can_view", "cart:1", [Tuple("user:u", "owner", "cart:1")])
    with pytest.raises(Unavailable):
        await OpenFgaAuthorizer(client(lambda r: httpx.Response(500)), "S").check("user:u", "x", "cart:1", [])
