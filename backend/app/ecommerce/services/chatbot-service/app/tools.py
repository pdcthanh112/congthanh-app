"""Bộ tool của trợ lý: định nghĩa gửi cho model, kiểm tham số và chạy qua port Shop."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any

from .ports import Shop, ShopError, ToolCall

MAX_RESULTS = 10
MAX_QUANTITY = 99
# Mô tả sản phẩm có thể rất dài (HTML của seller): chỉ đưa phần đầu cho model, đủ để tư vấn mà không tốn token.
DESCRIPTION_MAX_CHARS = 1_500
RECOMMENDATION_KINDS = ("SIMILAR", "BOUGHT_TOGETHER", "FOR_YOU", "TRENDING")
SORTS = ("RELEVANCE", "PRICE_ASC", "PRICE_DESC", "NEWEST")

_TAG = re.compile(r"<[^>]+>")
_SPACES = re.compile(r"\s+")


class InvalidToolInput(ValueError):
    pass


@dataclass(frozen=True, slots=True)
class ToolSpec:
    name: str
    description: str
    properties: dict[str, Any]
    required: tuple[str, ...] = ()
    requires_login: bool = False
    writes: bool = False

    def definition(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "strict": True,
            "input_schema": {
                "type": "object",
                "properties": self.properties,
                "required": list(self.required),
                "additionalProperties": False,
            },
        }


@dataclass(frozen=True, slots=True)
class ToolOutcome:
    """Kết quả một tool: content cho model, ui là dữ liệu có cấu trúc cho widget (thẻ sản phẩm, giỏ hàng)."""

    content: str
    is_error: bool = False
    products: list[dict[str, Any]] | None = None
    cart: dict[str, Any] | None = None


SPECS: tuple[ToolSpec, ...] = (
    ToolSpec(
        "search_products",
        "Search products on sale. Returns id, name, slug, price, rating. Use filters the customer mentioned.",
        {
            "query": {"type": "string", "description": "Keywords, e.g. 'men's waterproof jacket'"},
            "min_price": {"type": "number", "description": "Lowest price in the shop currency"},
            "max_price": {"type": "number", "description": "Highest price in the shop currency"},
            "sort": {"type": "string", "enum": list(SORTS)},
            "limit": {"type": "integer", "description": f"1-{MAX_RESULTS}, default 6"},
        },
        ("query",),
    ),
    ToolSpec(
        "get_product",
        "Full details of one product: description, specifications, stock, options and variants with their own "
        "price and stock. Give product_id or slug.",
        {"product_id": {"type": "string"}, "slug": {"type": "string"}},
    ),
    ToolSpec(
        "recommend_products",
        "Product recommendations. SIMILAR and BOUGHT_TOGETHER need product_id; FOR_YOU uses the customer's history; "
        "TRENDING is what is popular now.",
        {
            "kind": {"type": "string", "enum": list(RECOMMENDATION_KINDS)},
            "product_id": {"type": "string"},
            "limit": {"type": "integer", "description": f"1-{MAX_RESULTS}, default 6"},
        },
        ("kind",),
    ),
    ToolSpec("view_cart", "The customer's cart: lines (item id, product, quantity, price) and subtotal.", {}, (), True),
    ToolSpec(
        "add_to_cart",
        "Add a product (or the chosen variant id) to the customer's cart.",
        {
            "product_id": {"type": "string"},
            "quantity": {"type": "integer", "description": f"1-{MAX_QUANTITY}"},
        },
        ("product_id", "quantity"),
        True,
        True,
    ),
    ToolSpec(
        "update_cart_item",
        "Change the quantity of a cart line (item_id from view_cart); quantity 0 removes the line.",
        {
            "item_id": {"type": "string"},
            "quantity": {"type": "integer", "description": f"0-{MAX_QUANTITY}"},
        },
        ("item_id", "quantity"),
        True,
        True,
    ),
    ToolSpec(
        "list_orders",
        "The customer's recent orders: order code, status, total, date.",
        {"limit": {"type": "integer", "description": f"1-{MAX_RESULTS}, default 5"}},
        (),
        True,
    ),
    ToolSpec(
        "get_order",
        "One order by order code: status, lines, totals, shipping address.",
        {"order_code": {"type": "string"}},
        ("order_code",),
        True,
    ),
)
_SPECS_BY_NAME = {s.name: s for s in SPECS}


class ToolBox:
    """Chạy tool thay khách. Tool cần đăng nhập không được khai báo cho khách vãng lai, và vẫn bị chặn nếu model gọi."""

    def __init__(self, shop: Shop):
        self.shop = shop

    @staticmethod
    def definitions(signed_in: bool) -> list[dict[str, Any]]:
        return [s.definition() for s in SPECS if signed_in or not s.requires_login]

    @staticmethod
    def writes(name: str) -> bool:
        spec = _SPECS_BY_NAME.get(name)
        return spec is not None and spec.writes

    async def run(self, call: ToolCall, token: str | None, session_id: str | None) -> ToolOutcome:
        spec = _SPECS_BY_NAME.get(call.name)
        if spec is None:
            return _error(f"unknown tool {call.name}")
        if spec.requires_login and not token:
            return _error("the customer is not signed in; ask them to sign in first")
        try:
            return await self._dispatch(call.name, call.input, token, session_id)
        except (InvalidToolInput, ShopError) as e:
            return _error(str(e))

    async def _dispatch(
        self, name: str, args: dict[str, Any], token: str | None, session_id: str | None
    ) -> ToolOutcome:
        match name:
            case "search_products":
                items = await self.shop.search(
                    _text(args, "query", 200),
                    min_price=_number(args, "min_price"),
                    max_price=_number(args, "max_price"),
                    sort=_choice(args, "sort", SORTS),
                    limit=_limit(args, 6),
                )
                return _products(items)
            case "get_product":
                product_id, slug = _optional_text(args, "product_id"), _optional_text(args, "slug")
                if not product_id and not slug:
                    raise InvalidToolInput("give product_id or slug")
                product = await self.shop.product(product_id, None if product_id else slug)
                if product is None:
                    raise ShopError("product not found or no longer on sale")
                return ToolOutcome(_json(_product_detail(product)), products=[_card(product)])
            case "recommend_products":
                kind = _choice(args, "kind", RECOMMENDATION_KINDS) or "TRENDING"
                product_id = _optional_text(args, "product_id")
                if kind in ("SIMILAR", "BOUGHT_TOGETHER") and not product_id:
                    raise InvalidToolInput(f"{kind} needs product_id")
                items = await self.shop.recommendations(kind, product_id, _limit(args, 6), token, session_id)
                return _products(items)
            case "view_cart":
                return _cart(await self.shop.cart(_required(token)), updated=False)
            case "add_to_cart":
                quantity = _integer(args, "quantity", 1, MAX_QUANTITY)
                cart = await self.shop.add_to_cart(_required(token), _text(args, "product_id", 64), quantity)
                return _cart(cart, updated=True)
            case "update_cart_item":
                quantity = _integer(args, "quantity", 0, MAX_QUANTITY)
                cart = await self.shop.update_cart_item(_required(token), _text(args, "item_id", 64), quantity)
                return _cart(cart, updated=True)
            case "list_orders":
                orders = await self.shop.orders(_required(token), _limit(args, 5))
                return ToolOutcome(_json([_order_summary(o) for o in orders]))
            case "get_order":
                return ToolOutcome(
                    _json(_order(await self.shop.order(_required(token), _text(args, "order_code", 64))))
                )
        return _error(f"unknown tool {name}")


# ---------- kiểm tham số (model có thể sinh sai kiểu dù đã có schema) ----------


def _required(token: str | None) -> str:
    if not token:
        raise InvalidToolInput("the customer is not signed in")
    return token


def _optional_text(args: dict[str, Any], key: str) -> str | None:
    v = args.get(key)
    if v is None:
        return None
    if not isinstance(v, str):
        raise InvalidToolInput(f"{key} must be a string")
    return v.strip()[:200] or None


def _text(args: dict[str, Any], key: str, max_len: int) -> str:
    v = args.get(key)
    if not isinstance(v, str) or not v.strip():
        raise InvalidToolInput(f"{key} is required")
    return v.strip()[:max_len]


def _number(args: dict[str, Any], key: str) -> float | None:
    v = args.get(key)
    if v is None:
        return None
    if isinstance(v, bool) or not isinstance(v, int | float) or v < 0:
        raise InvalidToolInput(f"{key} must be a non-negative number")
    return float(v)


def _integer(args: dict[str, Any], key: str, low: int, high: int) -> int:
    v = args.get(key)
    if isinstance(v, bool) or not isinstance(v, int):
        raise InvalidToolInput(f"{key} must be an integer")
    if not low <= v <= high:
        raise InvalidToolInput(f"{key} must be between {low} and {high}")
    return v


def _limit(args: dict[str, Any], default: int) -> int:
    v = args.get("limit")
    if isinstance(v, bool) or not isinstance(v, int):
        return default
    return max(1, min(v, MAX_RESULTS))


def _choice(args: dict[str, Any], key: str, allowed: tuple[str, ...]) -> str | None:
    v = args.get(key)
    if v is None:
        return None
    if v not in allowed:
        raise InvalidToolInput(f"{key} must be one of {', '.join(allowed)}")
    return v


# ---------- rút gọn dữ liệu cho model ----------


def _error(message: str) -> ToolOutcome:
    return ToolOutcome(message, is_error=True)


def _json(data: Any) -> str:
    return json.dumps(data, ensure_ascii=False, separators=(",", ":"))


def _price(m: dict[str, Any] | None) -> str | None:
    return m.get("formatted") if m else None


def _card(p: dict[str, Any]) -> dict[str, Any]:
    """Thẻ sản phẩm cho widget: giữ nguyên kiểu ProductCard của storefront."""
    keys = (
        "id",
        "name",
        "slug",
        "thumbnail",
        "price",
        "originalPrice",
        "discountPercent",
        "ratingSummary",
        "inWishlist",
    )
    return {k: p.get(k) for k in keys}


def _brief(p: dict[str, Any]) -> dict[str, Any]:
    rating = p.get("ratingSummary") or {}
    return {
        "id": p.get("id"),
        "name": p.get("name"),
        "slug": p.get("slug"),
        "price": _price(p.get("price")),
        "discountPercent": p.get("discountPercent") or None,
        "rating": rating.get("average"),
        "reviews": rating.get("count"),
    }


def _products(items: list[dict[str, Any]]) -> ToolOutcome:
    if not items:
        return ToolOutcome("no matching products", products=[])
    return ToolOutcome(_json([_brief(p) for p in items]), products=[_card(p) for p in items])


def _plain(html: str | None) -> str:
    text = _SPACES.sub(" ", _TAG.sub(" ", html or "")).strip()
    return text[:DESCRIPTION_MAX_CHARS]


def _stock(s: dict[str, Any] | None) -> str | None:
    if not s:
        return None
    return f"{s.get('status')} ({s.get('available')} available)"


def _product_detail(p: dict[str, Any]) -> dict[str, Any]:
    return {
        **_brief(p),
        "brand": p.get("brandName"),
        "sku": p.get("sku"),
        "stock": _stock(p.get("stock")),
        "description": _plain(p.get("description")),
        "specifications": _specifications(p),
        "options": {o["name"]: o["values"] for o in p.get("options") or []},
        "variants": [
            {
                "id": v.get("id"),
                "options": {o["name"]: o["value"] for o in v.get("options") or []},
                "price": _price(v.get("price")) or _price(p.get("price")),
                "stock": _stock(v.get("stock")),
            }
            for v in p.get("variants") or []
        ],
    }


def _specifications(p: dict[str, Any]) -> dict[str, str]:
    """Thông số có cấu trúc trước, rồi thông số tự do (không ghi đè tên đã có)."""
    out = {a["name"]: a["value"] for a in p.get("attributes") or []}
    for group in p.get("specifications") or []:
        for item in group.get("items") or []:
            out.setdefault(item["name"], item["value"])
    return out


def _cart(cart: dict[str, Any], *, updated: bool) -> ToolOutcome:
    lines = [
        {
            "itemId": line.get("id"),
            "product": (line.get("product") or {}).get("name"),
            "productId": (line.get("product") or {}).get("id"),
            "quantity": line.get("quantity"),
            "lineTotal": _price(line.get("lineTotal")),
            "issue": line.get("issue"),
        }
        for line in cart.get("lines") or []
    ]
    summary = {
        "itemCount": cart.get("itemCount"),
        "subtotal": _price(cart.get("subtotal")),
        "checkoutReady": cart.get("checkoutReady"),
        "lines": lines,
    }
    return ToolOutcome(_json(summary), cart=cart if updated else None)


def _order_summary(o: dict[str, Any]) -> dict[str, Any]:
    return {
        "orderCode": o.get("orderCode"),
        "status": o.get("status"),
        "total": _price(o.get("total")),
        "items": o.get("itemCount"),
        "placedAt": o.get("placedAt"),
    }


def _order(o: dict[str, Any]) -> dict[str, Any]:
    address = o.get("shippingAddress") or {}
    return {
        "orderCode": o.get("orderCode"),
        "status": o.get("status"),
        "placedAt": o.get("placedAt"),
        "lines": [
            {"name": line.get("name"), "quantity": line.get("quantity"), "total": _price(line.get("total"))}
            for line in o.get("lines") or []
        ],
        "subtotal": _price(o.get("subtotal")),
        "discount": _price(o.get("discount")),
        "total": _price(o.get("total")),
        "paymentMethod": o.get("paymentMethod"),
        # Chỉ đưa thành phố/quốc gia cho model, không đưa địa chỉ đầy đủ và số điện thoại.
        "shipTo": ", ".join(x for x in (address.get("city"), address.get("country")) if x) or None,
        "cancelReason": o.get("cancelReason"),
    }
