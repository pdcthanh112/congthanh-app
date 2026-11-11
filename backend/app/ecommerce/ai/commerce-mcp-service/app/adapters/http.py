from __future__ import annotations

from typing import Any
from urllib.parse import quote

import httpx

from ..domain import Forbidden, InvalidInput, NotFound, Unauthenticated, Unavailable


def _raise_for(r: httpx.Response, what: str) -> None:
    if r.is_success:
        return
    if r.status_code == 401:
        raise Unauthenticated("the access token was rejected; sign in again")
    if r.status_code == 403:
        raise Forbidden(f"not allowed to access this {what}")
    if r.status_code == 404:
        raise NotFound(f"{what} not found")
    if r.status_code in (400, 409, 422):
        try:
            msg = r.json().get("message") or r.text
        except ValueError:
            msg = r.text
        raise InvalidInput(str(msg)[:300])
    raise Unavailable(f"{what} service is unavailable ({r.status_code})")


async def _send(client: httpx.AsyncClient, what: str, method: str, url: str, **kw: Any) -> httpx.Response:
    try:
        r = await client.request(method, url, **kw)
    except httpx.HTTPError as e:
        raise Unavailable(f"{what} service is unavailable") from e
    _raise_for(r, what)
    return r


def _auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def _data(r: httpx.Response) -> Any:
    body = r.json()
    return body.get("data") if isinstance(body, dict) and "data" in body else body


class CatalogClient:
    """search-service (tìm kiếm) + product-service (chi tiết); dữ liệu công khai, không cần token."""

    def __init__(self, search: httpx.AsyncClient, product: httpx.AsyncClient):
        self.search_http, self.product_http = search, product

    async def search(self, query: str, limit: int) -> list[dict[str, Any]]:
        r = await _send(
            self.search_http, "product", "GET", "/api/ecommerce/search/products", params={"q": query, "size": limit}
        )
        hits = (_data(r) or {}).get("hits") or []
        return [
            {
                "id": h["id"],
                "name": h.get("name", ""),
                "slug": h.get("slug", ""),
                "price": h.get("price"),
                "currency": h.get("currency", "USD"),
            }
            for h in hits
            if h.get("status", "ACTIVE") == "ACTIVE"
        ]

    async def detail(self, product_id: str) -> dict[str, Any]:
        r = await _send(
            self.product_http, "product", "GET", f"/api/ecommerce/products/{quote(product_id, safe='')}/detail"
        )
        return _data(r) or {}


def _cart(raw: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": raw["id"],
        "customerId": raw.get("customerId") or raw.get("customer"),
        "isDefault": raw.get("isDefault", False),
        "items": [
            {"id": i["id"], "productId": i.get("productId") or i.get("product"), "quantity": i["quantity"]}
            for i in raw.get("cartItems") or []
        ],
    }


class CartClient:
    def __init__(self, client: httpx.AsyncClient):
        self.client = client

    async def default_cart(self, token: str, customer_id: str) -> dict[str, Any] | None:
        try:
            r = await _send(
                self.client,
                "cart",
                "GET",
                f"/api/ecommerce/carts/customer/{quote(customer_id, safe='')}/default",
                headers=_auth(token),
            )
        except NotFound:
            return None
        return _cart(_data(r))

    async def create_default(self, token: str) -> dict[str, Any]:
        r = await _send(
            self.client,
            "cart",
            "POST",
            "/api/ecommerce/carts/",
            json={"name": "Giỏ hàng", "isDefault": True},
            headers=_auth(token),
        )
        return _cart(_data(r))

    async def get(self, token: str, cart_id: int) -> dict[str, Any]:
        r = await _send(self.client, "cart", "GET", f"/api/ecommerce/carts/{cart_id}", headers=_auth(token))
        return _cart(_data(r))

    async def add_item(self, token: str, cart_id: int, product_id: str, quantity: int) -> dict[str, Any]:
        r = await _send(
            self.client,
            "cart",
            "POST",
            f"/api/ecommerce/carts/{cart_id}/items",
            json={"productId": product_id, "quantity": quantity},
            headers=_auth(token),
        )
        i = _data(r)
        return {"id": i["id"], "productId": i.get("product"), "quantity": i["quantity"]}

    async def update_item(self, token: str, cart_id: int, item_id: int, quantity: int) -> dict[str, Any]:
        r = await _send(
            self.client,
            "cart",
            "PUT",
            f"/api/ecommerce/carts/{cart_id}/items/{item_id}",
            params={"quantity": quantity},
            headers=_auth(token),
        )
        i = _data(r)
        return {"id": i["id"], "productId": i.get("product"), "quantity": i["quantity"]}

    async def remove_item(self, token: str, cart_id: int, item_id: int) -> None:
        await _send(
            self.client, "cart", "DELETE", f"/api/ecommerce/carts/{cart_id}/items/{item_id}", headers=_auth(token)
        )


class OrderClient:
    def __init__(self, client: httpx.AsyncClient):
        self.client = client

    async def history(self, token: str, limit: int) -> list[dict[str, Any]]:
        r = await _send(
            self.client, "order", "GET", "/api/ecommerce/orders", params={"limit": limit}, headers=_auth(token)
        )
        return list(_data(r) or [])

    async def get(self, token: str, order_code: str) -> dict[str, Any]:
        r = await _send(
            self.client, "order", "GET", f"/api/ecommerce/orders/{quote(order_code, safe='')}", headers=_auth(token)
        )
        return _data(r)

    async def cancel(self, token: str, order_code: str, reason: str | None) -> None:
        await _send(
            self.client,
            "order",
            "POST",
            f"/api/ecommerce/orders/{quote(order_code, safe='')}/cancel",
            json={"reason": reason},
            headers=_auth(token),
        )
