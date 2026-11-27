"""Shop qua GraphQL storefront (Cosmo Router): cùng cửa và cùng kiểm quyền với web, chạy bằng token của khách."""

from __future__ import annotations

from typing import Any

import httpx

from ..ports import ShopError, ShopUnavailable

MONEY = "amount currency formatted"
# Giống PRODUCT_CARD và CART của frontend để widget dùng thẳng (cập nhật cache giỏ hàng, thẻ sản phẩm).
PRODUCT_CARD = (
    f"id name slug thumbnail price {{ {MONEY} }} originalPrice {{ {MONEY} }} discountPercent "
    "ratingSummary { average count } inWishlist"
)
CART = (
    f"id itemCount checkoutReady subtotal {{ {MONEY} }} lines {{ id quantity purchasable issue "
    f"unitPrice {{ {MONEY} }} lineTotal {{ {MONEY} }} product {{ id name slug thumbnail }} }}"
)

SEARCH = f"query($input: ProductSearchInput!) {{ searchCatalog(input: $input) {{ items {{ {PRODUCT_CARD} }} }} }}"
PRODUCT = f"""query($id: ID, $slug: String) {{
  product(id: $id, slug: $slug) {{
    {PRODUCT_CARD} sku brandName description stock {{ available status }}
    attributes {{ name value }} specifications {{ name items {{ name value }} }} options {{ name values }}
    variants {{ id name price {{ {MONEY} }} options {{ name value }} stock {{ available status }} }}
  }}
}}"""
SIMILAR = f"""query($id: ID!, $kind: RecommendationKind!, $limit: Int!) {{
  product(id: $id) {{ recommendations(kind: $kind, limit: $limit) {{ product {{ {PRODUCT_CARD} }} }} }}
}}"""
FOR_YOU = f"query($limit: Int!) {{ recommendedForYou(limit: $limit) {{ product {{ {PRODUCT_CARD} }} }} }}"
TRENDING = f"query($limit: Int!) {{ trendingProducts(limit: $limit) {{ product {{ {PRODUCT_CARD} }} }} }}"
MY_CART = f"query {{ myCart {{ {CART} }} }}"
ADD_TO_CART = f"mutation($id: ID!, $q: Int!) {{ addToCart(productId: $id, quantity: $q) {{ {CART} }} }}"
UPDATE_CART_ITEM = f"mutation($id: ID!, $q: Int!) {{ updateCartItem(itemId: $id, quantity: $q) {{ {CART} }} }}"
MY_ORDERS = (
    f"query($limit: Int!) {{ myOrders(limit: $limit) {{ orderCode status itemCount placedAt total {{ {MONEY} }} }} }}"
)
ORDER = f"""query($code: String!) {{
  order(orderCode: $code) {{
    orderCode status placedAt paymentMethod cancelReason
    subtotal {{ {MONEY} }} discount {{ {MONEY} }} total {{ {MONEY} }}
    lines {{ name quantity total {{ {MONEY} }} }} shippingAddress {{ city country }}
  }}
}}"""

# Mã lỗi GraphQL là lỗi nghiệp vụ: đưa message cho model để nó giải thích với khách.
_BUSINESS_CODES = frozenset({"BAD_USER_INPUT", "NOT_FOUND", "FORBIDDEN", "UNAUTHENTICATED", "CONFLICT"})


class GraphqlShop:
    def __init__(self, client: httpx.AsyncClient, url: str):
        self.client, self.url = client, url

    async def search(
        self, text: str, *, min_price: float | None, max_price: float | None, sort: str | None, limit: int
    ) -> list[dict[str, Any]]:
        criteria: dict[str, Any] = {"text": text, "limit": limit}
        if min_price is not None:
            criteria["minPrice"] = min_price
        if max_price is not None:
            criteria["maxPrice"] = max_price
        if sort:
            criteria["sort"] = sort
        data = await self._call(SEARCH, {"input": criteria})
        return data["searchCatalog"]["items"]

    async def product(self, product_id: str | None, slug: str | None) -> dict[str, Any] | None:
        data = await self._call(PRODUCT, {"id": product_id, "slug": slug})
        return data["product"]

    async def recommendations(
        self, kind: str, product_id: str | None, limit: int, token: str | None, session_id: str | None
    ) -> list[dict[str, Any]]:
        if kind == "FOR_YOU":
            rows = (await self._call(FOR_YOU, {"limit": limit}, token, session_id))["recommendedForYou"]
        elif kind == "TRENDING":
            rows = (await self._call(TRENDING, {"limit": limit}))["trendingProducts"]
        else:
            product = (await self._call(SIMILAR, {"id": product_id, "kind": kind, "limit": limit}))["product"]
            if product is None:
                raise ShopError("product not found or no longer on sale")
            rows = product["recommendations"]
        return [r["product"] for r in rows]

    async def cart(self, token: str) -> dict[str, Any]:
        return (await self._call(MY_CART, {}, token))["myCart"]

    async def add_to_cart(self, token: str, product_id: str, quantity: int) -> dict[str, Any]:
        return (await self._call(ADD_TO_CART, {"id": product_id, "q": quantity}, token))["addToCart"]

    async def update_cart_item(self, token: str, item_id: str, quantity: int) -> dict[str, Any]:
        return (await self._call(UPDATE_CART_ITEM, {"id": item_id, "q": quantity}, token))["updateCartItem"]

    async def orders(self, token: str, limit: int) -> list[dict[str, Any]]:
        return (await self._call(MY_ORDERS, {"limit": limit}, token))["myOrders"]

    async def order(self, token: str, order_code: str) -> dict[str, Any]:
        return (await self._call(ORDER, {"code": order_code}, token))["order"]

    async def _call(
        self, query: str, variables: dict[str, Any], token: str | None = None, session_id: str | None = None
    ) -> dict[str, Any]:
        headers = {"content-type": "application/json"}
        if token:
            headers["authorization"] = f"Bearer {token}"
        if session_id:
            headers["x-session-id"] = session_id
        try:
            r = await self.client.post(self.url, json={"query": query, "variables": variables}, headers=headers)
        except httpx.HTTPError as e:
            raise ShopUnavailable("graphql router unreachable") from e
        if r.status_code >= 500:
            raise ShopUnavailable(f"graphql router returned {r.status_code}")
        body = r.json()
        errors = body.get("errors") or []
        if errors:
            first = errors[0]
            code = (first.get("extensions") or {}).get("code")
            if code in _BUSINESS_CODES:
                raise ShopError(first.get("message") or code.lower())
            raise ShopUnavailable(f"graphql error {code}: {first.get('message')}")
        data = body.get("data")
        if not isinstance(data, dict):
            raise ShopUnavailable("graphql response has no data")
        return data
