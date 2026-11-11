from __future__ import annotations

from typing import Any

from app.domain import NotFound, Tuple


class FakeCatalog:
    def __init__(self):
        self.products = {"p1": {"id": "p1", "name": "Rain Jacket", "price": 89.99, "currency": "USD"}}

    async def search(self, query: str, limit: int) -> list[dict[str, Any]]:
        return [p for p in self.products.values() if query.lower() in p["name"].lower()][:limit]

    async def detail(self, product_id: str) -> dict[str, Any]:
        if product_id not in self.products:
            raise NotFound("product not found")
        return self.products[product_id]


class FakeCarts:
    def __init__(self):
        self.carts: dict[str, dict[str, Any]] = {}
        self.next_id = 1

    async def default_cart(self, token: str, customer_id: str):
        return self.carts.get(customer_id)

    async def create_default(self, token: str):
        owner = token.removeprefix("tok-")
        cart = {"id": self.next_id, "customerId": owner, "isDefault": True, "items": []}
        self.next_id += 1
        self.carts[owner] = cart
        return cart

    def _by_id(self, cart_id: int):
        return next(c for c in self.carts.values() if c["id"] == cart_id)

    async def get(self, token: str, cart_id: int):
        return self._by_id(cart_id)

    async def add_item(self, token: str, cart_id: int, product_id: str, quantity: int):
        cart = self._by_id(cart_id)
        item = {"id": len(cart["items"]) + 1, "productId": product_id, "quantity": quantity}
        cart["items"].append(item)
        return item

    async def update_item(self, token: str, cart_id: int, item_id: int, quantity: int):
        item = next(i for i in self._by_id(cart_id)["items"] if i["id"] == item_id)
        item["quantity"] = quantity
        return item

    async def remove_item(self, token: str, cart_id: int, item_id: int):
        cart = self._by_id(cart_id)
        cart["items"] = [i for i in cart["items"] if i["id"] != item_id]


class FakeOrders:
    def __init__(self):
        self.orders = {"u1": [{"orderCode": "OD1", "status": "PENDING", "total": {"amount": 1, "currency": "USD"}}]}
        self.cancelled: list[tuple[str, str | None]] = []

    async def history(self, token: str, limit: int):
        return self.orders.get(token.removeprefix("tok-"), [])[:limit]

    async def get(self, token: str, order_code: str):
        return {"orderCode": order_code, "status": "PENDING"}

    async def cancel(self, token: str, order_code: str, reason: str | None):
        self.cancelled.append((order_code, reason))


class FakeAuthz:
    """Đánh giá đúng phần model OpenFGA cần cho test (owner/buyer/support/admin) trên contextual tuples."""

    def __init__(self):
        self.calls: list[tuple[str, str, str, list[Tuple]]] = []

    async def check(self, user: str, relation: str, obj: str, contextual: list[Tuple]) -> bool:
        self.calls.append((user, relation, obj, contextual))
        has = {(t.user, t.relation, t.object) for t in contextual}
        platform = next((t.user for t in contextual if t.relation == "platform" and t.object == obj), None)
        admin = platform is not None and (user, "admin", platform) in has
        support = platform is not None and (admin or (user, "support", platform) in has)
        kind = obj.split(":", 1)[0]
        if kind == "cart":
            owner = (user, "owner", obj) in has
            return owner or (relation == "can_view" and support)
        buyer = (user, "buyer", obj) in has
        return {"can_view": buyer or support, "can_cancel": buyer or admin}.get(relation, False)
