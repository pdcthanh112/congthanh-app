"""Port giả viết tay cho test chatbot-service."""

from __future__ import annotations

from typing import Any

from app.domain.chat import Turn
from app.ports import LLMError, ShopError, ShopUnavailable, TextDelta, ToolCall, TurnEnd


def money(amount: float, currency: str = "USD") -> dict[str, Any]:
    return {"amount": amount, "currency": currency, "formatted": f"${amount:,.2f}"}


def card(pid: str, name: str, amount: float) -> dict[str, Any]:
    return {
        "id": pid,
        "name": name,
        "slug": pid,
        "thumbnail": None,
        "price": money(amount),
        "originalPrice": money(amount),
        "discountPercent": 0,
        "ratingSummary": {"average": 4.5, "count": 10},
        "inWishlist": False,
    }


SHIRT = card("p1", "Cotton T-Shirt", 19.99)
JEANS = card("p2", "Slim Jeans", 45.0)


def text_turn(*chunks: str) -> list[Any]:
    """Một lần gọi model chỉ trả lời bằng chữ."""
    return [*(TextDelta(c) for c in chunks), TurnEnd([{"type": "text", "text": "".join(chunks)}], "end_turn")]


def tool_turn(*calls: tuple[str, str, dict[str, Any]], thinking: bool = False) -> list[Any]:
    """Một lần gọi model yêu cầu chạy tool: calls là (id, tên, tham số)."""
    blocks: list[dict[str, Any]] = [{"type": "thinking", "thinking": "", "signature": "sig"}] if thinking else []
    blocks += [{"type": "tool_use", "id": i, "name": n, "input": a} for i, n, a in calls]
    return [TurnEnd(blocks, "tool_use", [ToolCall(i, n, a) for i, n, a in calls])]


class ScriptedLLM:
    """Phát lần lượt các lượt đã soạn; ghi lại (system, messages, tools) của mỗi lần gọi."""

    def __init__(self, *turns: list[Any], supports_tools: bool = True, fail: bool = False):
        self.turns, self.supports_tools, self.fail, self.calls = list(turns), supports_tools, fail, []

    async def turn(self, system, messages, tools):
        self.calls.append((system, list(messages), tools))
        if self.fail:
            yield TextDelta("dở ")
            raise LLMError("busy")
        events = self.turns.pop(0) if self.turns else text_turn("ok")
        for e in events:
            yield e


class FakeShop:
    def __init__(self, products: list[dict[str, Any]] | None = None, down: bool = False):
        self.products = {p["id"]: p for p in (products if products is not None else [SHIRT, JEANS])}
        self.down = down
        self.lines: list[dict[str, Any]] = []
        self.calls: list[tuple[str, Any]] = []

    def _check(self):
        if self.down:
            raise ShopUnavailable("router down")

    async def search(self, text, *, min_price, max_price, sort, limit):
        self._check()
        self.calls.append(("search", (text, min_price, max_price, sort, limit)))
        hits = [p for p in self.products.values() if any(w in p["name"].lower() for w in text.lower().split())]
        if max_price is not None:
            hits = [p for p in hits if p["price"]["amount"] <= max_price]
        return hits[:limit]

    async def product(self, product_id, slug):
        self._check()
        return self.products.get(product_id or slug or "")

    async def recommendations(self, kind, product_id, limit, token, session_id):
        self._check()
        self.calls.append(("recommendations", (kind, product_id, token, session_id)))
        return list(self.products.values())[:limit]

    def _cart(self):
        return {
            "id": "1",
            "itemCount": sum(line["quantity"] for line in self.lines),
            "checkoutReady": bool(self.lines),
            "subtotal": money(sum(line["quantity"] * line["product"]["price"]["amount"] for line in self.lines)),
            "lines": [{**line, "lineTotal": None, "issue": None} for line in self.lines],
        }

    async def cart(self, token):
        self._check()
        self.calls.append(("cart", token))
        return self._cart()

    async def add_to_cart(self, token, product_id, quantity):
        self._check()
        self.calls.append(("add_to_cart", (token, product_id, quantity)))
        if product_id not in self.products:
            raise ShopError(f"product {product_id} not found")
        self.lines.append({"id": str(len(self.lines) + 1), "quantity": quantity, "product": self.products[product_id]})
        return self._cart()

    async def update_cart_item(self, token, item_id, quantity):
        self._check()
        self.lines = [
            {**line, "quantity": quantity} if line["id"] == item_id else line
            for line in self.lines
            if quantity or line["id"] != item_id
        ]
        return self._cart()

    async def orders(self, token, limit):
        self._check()
        return [
            {
                "orderCode": "ORD-1",
                "status": "SHIPPED",
                "itemCount": 2,
                "placedAt": "2026-11-20T10:00:00Z",
                "total": money(65),
            }
        ]

    async def order(self, token, order_code):
        self._check()
        if order_code != "ORD-1":
            raise ShopError("order not found")
        return {
            "orderCode": "ORD-1",
            "status": "SHIPPED",
            "lines": [{"name": "Slim Jeans", "quantity": 1, "total": money(45)}],
            "total": money(45),
            "shippingAddress": {"city": "Austin", "country": "US", "line1": "1 Secret St", "phone": "555"},
        }


class MemHistory:
    def __init__(self):
        self.d: dict[str, list[Turn]] = {}

    async def load(self, key, turns):
        return self.d.get(key, [])[-turns:]

    async def append(self, key, turn, keep_turns, ttl):
        self.d[key] = [*self.d.get(key, []), turn][-keep_turns:]

    async def clear(self, key):
        self.d.pop(key, None)
