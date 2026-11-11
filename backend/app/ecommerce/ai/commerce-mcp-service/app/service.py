"""Nghiệp vụ của các tool MCP: kiểm scope OAuth → hỏi OpenFGA → gọi service sở hữu dữ liệu.

Ba lớp quyền, lớp sau không tin lớp trước:
1. Scope OAuth: người dùng cấp cho agent quyền gì (commerce:read / commerce:cart / commerce:orders.cancel).
2. OpenFGA: người dùng có quan hệ gì với giỏ/đơn (owner, buyer, support/admin của platform). Quan hệ
   sở hữu lấy từ chính service nguồn rồi gửi kèm dạng contextual tuple, nên chưa cần đồng bộ tuple vào store.
3. Service đích tự kiểm token chuyển tiếp như mọi request khác.
"""

from __future__ import annotations

from typing import Any

from ecommerce_platform.auth import Principal

from .domain import (
    SCOPE_CART,
    SCOPE_ORDERS_CANCEL,
    SCOPE_READ,
    Forbidden,
    InvalidInput,
    NotFound,
    Tuple,
    Unauthenticated,
)
from .ports import Authorizer, Carts, Catalog, Orders

MAX_QUANTITY = 999


class CommerceTools:
    def __init__(
        self,
        catalog: Catalog,
        carts: Carts,
        orders: Orders,
        authz: Authorizer,
        *,
        platform: str = "platform:congthanh",
        max_results: int = 20,
        history_window: int = 100,
    ):
        self.catalog, self.carts, self.orders, self.authz = catalog, carts, orders, authz
        self.platform, self.max_results, self.history_window = platform, max_results, history_window

    # ---------- quyền ----------

    @staticmethod
    def _require(p: Principal | None, scope: str | None = None) -> Principal:
        if p is None:
            raise Unauthenticated("sign in is required")
        if scope and not p.has_scope(scope):
            raise Forbidden(f"the agent was not granted the '{scope}' scope")
        return p

    def _role_tuples(self, p: Principal, obj: str) -> list[Tuple]:
        user = f"user:{p.subject}"
        out = [Tuple(self.platform, "platform", obj)]
        if p.has_role("ADMIN"):
            out.append(Tuple(user, "admin", self.platform))
        if p.has_role("SUPPORT"):
            out.append(Tuple(user, "support", self.platform))
        return out

    async def _check(self, p: Principal, relation: str, obj: str, ownership: list[Tuple]) -> None:
        if not await self.authz.check(f"user:{p.subject}", relation, obj, self._role_tuples(p, obj) + ownership):
            raise Forbidden(f"not allowed: {relation} on {obj.split(':', 1)[0]}")

    def _limit(self, limit: int) -> int:
        return max(1, min(limit, self.max_results))

    # ---------- catalog (dữ liệu công khai, chỉ cần đăng nhập) ----------

    async def search_products(self, p: Principal | None, query: str, limit: int = 10) -> list[dict[str, Any]]:
        self._require(p)
        query = query.strip()
        if not query:
            raise InvalidInput("query must not be empty")
        return await self.catalog.search(query[:200], self._limit(limit))

    async def get_product(self, p: Principal | None, product_id: str) -> dict[str, Any]:
        self._require(p)
        if not product_id.strip():
            raise InvalidInput("product_id must not be empty")
        return await self.catalog.detail(product_id.strip())

    # ---------- giỏ hàng ----------

    def _cart_ownership(self, p: Principal, cart: dict[str, Any]) -> list[Tuple]:
        obj = f"cart:{cart['id']}"
        return [Tuple(f"user:{p.subject}", "owner", obj)] if cart.get("customerId") == p.subject else []

    async def view_cart(self, p: Principal | None) -> dict[str, Any]:
        p = self._require(p, SCOPE_READ)
        cart = await self.carts.default_cart(p.token, p.subject)
        if cart is None:
            return {"id": None, "items": []}
        await self._check(p, "can_view", f"cart:{cart['id']}", self._cart_ownership(p, cart))
        return cart

    async def _editable_cart(self, p: Principal) -> dict[str, Any]:
        cart = await self.carts.default_cart(p.token, p.subject) or await self.carts.create_default(p.token)
        await self._check(p, "can_edit", f"cart:{cart['id']}", self._cart_ownership(p, cart))
        return cart

    async def add_to_cart(self, p: Principal | None, product_id: str, quantity: int = 1) -> dict[str, Any]:
        p = self._require(p, SCOPE_CART)
        if not 1 <= quantity <= MAX_QUANTITY:
            raise InvalidInput(f"quantity must be between 1 and {MAX_QUANTITY}")
        await self.catalog.detail(product_id)  # sản phẩm phải tồn tại; NotFound trả thẳng cho agent
        cart = await self._editable_cart(p)
        item = await self.carts.add_item(p.token, cart["id"], product_id, quantity)
        return {"cartId": cart["id"], "item": item}

    async def update_cart_item(self, p: Principal | None, item_id: int, quantity: int) -> dict[str, Any]:
        """quantity = 0 xoá dòng khỏi giỏ."""
        p = self._require(p, SCOPE_CART)
        if not 0 <= quantity <= MAX_QUANTITY:
            raise InvalidInput(f"quantity must be between 0 and {MAX_QUANTITY}")
        cart = await self._editable_cart(p)
        if not any(i["id"] == item_id for i in cart["items"]):
            raise NotFound("cart item not found")
        if quantity == 0:
            await self.carts.remove_item(p.token, cart["id"], item_id)
            return {"cartId": cart["id"], "removed": item_id}
        return {"cartId": cart["id"], "item": await self.carts.update_item(p.token, cart["id"], item_id, quantity)}

    # ---------- đơn hàng ----------

    async def _order_ownership(self, p: Principal, code: str) -> list[Tuple]:
        recent = await self.orders.history(p.token, self.history_window)
        mine = any(o.get("orderCode") == code for o in recent)
        return [Tuple(f"user:{p.subject}", "buyer", f"order:{code}")] if mine else []

    async def list_orders(self, p: Principal | None, limit: int = 10) -> list[dict[str, Any]]:
        p = self._require(p, SCOPE_READ)
        return await self.orders.history(p.token, self._limit(limit))

    async def get_order(self, p: Principal | None, order_code: str) -> dict[str, Any]:
        p = self._require(p, SCOPE_READ)
        code = order_code.strip()
        await self._check(p, "can_view", f"order:{code}", await self._order_ownership(p, code))
        return await self.orders.get(p.token, code)

    async def cancel_order(
        self, p: Principal | None, order_code: str, reason: str | None = None, confirm: bool = False
    ) -> dict[str, Any]:
        p = self._require(p, SCOPE_ORDERS_CANCEL)
        code = order_code.strip()
        if not confirm:
            # Agent phải hỏi lại người dùng rồi gọi lại với confirm=true; không tự quyết thao tác không hoàn tác.
            raise InvalidInput("cancelling is irreversible: ask the user to confirm, then call again with confirm=true")
        await self._check(p, "can_cancel", f"order:{code}", await self._order_ownership(p, code))
        await self.orders.cancel(p.token, code, (reason or "").strip()[:500] or None)
        return {"orderCode": code, "status": "CANCEL_REQUESTED"}
