from __future__ import annotations

import asyncio
from collections.abc import Callable
from typing import Annotated, Any

from ecommerce_platform.auth import AuthError, Principal, Verifier, extract_roles
from mcp.server.auth.middleware.auth_context import get_access_token
from mcp.server.auth.provider import AccessToken
from mcp.server.mcpserver import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp_types import ToolAnnotations
from pydantic import Field

from .domain import CommerceError
from .service import CommerceTools

# Văn bản dưới đây do client LLM đọc nên viết tiếng Anh (cửa hàng bán cho khách ở Mỹ).
INSTRUCTIONS = """Shopping tools for CongThanh Shop, acting on behalf of the signed-in user.
Prices are in the currency returned with each amount (usually USD). Always ask the user to confirm before
cancelling an order; cancel_order only runs with confirm=true. There is no payment tool: the user places
and pays for orders on the website or app."""

READ = ToolAnnotations(read_only_hint=True, open_world_hint=False)
WRITE = ToolAnnotations(read_only_hint=False, destructive_hint=False, idempotent_hint=False, open_world_hint=False)
DESTRUCTIVE = ToolAnnotations(read_only_hint=False, destructive_hint=True, idempotent_hint=True, open_world_hint=False)


class JwtTokenVerifier:
    """TokenVerifier của MCP SDK bọc Verifier dùng chung (JWKS Keycloak, kiểm issuer + audience)."""

    def __init__(self, verifier: Verifier):
        self.verifier = verifier

    async def verify_token(self, token: str) -> AccessToken | None:
        try:
            # PyJWKClient gọi mạng đồng bộ khi cần làm mới khoá: chạy ngoài event loop.
            p = await asyncio.to_thread(self.verifier.verify, token)
        except AuthError:
            return None
        return AccessToken(
            token=token,
            client_id="",
            scopes=sorted(p.scopes),
            subject=p.subject,
            claims={"roles": sorted(p.roles)},
        )


def principal_from_context() -> Principal | None:
    at = get_access_token()
    if at is None or not at.subject:
        return None
    roles = extract_roles({"roles": (at.claims or {}).get("roles", [])})
    return Principal(subject=at.subject, roles=roles, token=at.token, scopes=frozenset(at.scopes))


def build_server(
    tools: CommerceTools,
    principal: Callable[[], Principal | None] = principal_from_context,
    **server_kwargs: Any,
) -> MCPServer:
    mcp = MCPServer(name="congthanh-commerce", title="CongThanh Shop", instructions=INSTRUCTIONS, **server_kwargs)

    async def run(coro_fn, *args):
        try:
            return await coro_fn(principal(), *args)
        except CommerceError as e:
            raise ToolError(str(e)) from e

    @mcp.tool(title="Tìm sản phẩm", annotations=READ)
    async def search_products(
        query: Annotated[str, Field(description="Search keywords, e.g. 'men's rain jacket'")],
        limit: Annotated[int, Field(ge=1, le=50)] = 10,
    ) -> list[dict[str, Any]]:
        """Search products on sale by keyword; returns id, name, slug and price."""
        return await run(tools.search_products, query, limit)

    @mcp.tool(title="Chi tiết sản phẩm", annotations=READ)
    async def get_product(product_id: str) -> dict[str, Any]:
        """Full details of one product (description, variants, price, displayed stock)."""
        return await run(tools.get_product, product_id)

    @mcp.tool(title="Xem giỏ hàng", annotations=READ)
    async def view_cart() -> dict[str, Any]:
        """The user's default cart. Requires scope commerce:read."""
        return await run(tools.view_cart)

    @mcp.tool(title="Thêm vào giỏ", annotations=WRITE)
    async def add_to_cart(product_id: str, quantity: Annotated[int, Field(ge=1, le=999)] = 1) -> dict[str, Any]:
        """Add a product to the default cart (creates the cart if needed). Requires scope commerce:cart."""
        return await run(tools.add_to_cart, product_id, quantity)

    @mcp.tool(title="Sửa số lượng trong giỏ", annotations=WRITE)
    async def update_cart_item(
        item_id: Annotated[int, Field(description="Cart line id (from view_cart)")],
        quantity: Annotated[int, Field(ge=0, le=999, description="0 = remove from cart")],
    ) -> dict[str, Any]:
        """Change the quantity of a cart line or remove it. Requires scope commerce:cart."""
        return await run(tools.update_cart_item, item_id, quantity)

    @mcp.tool(title="Đơn hàng gần đây", annotations=READ)
    async def list_orders(limit: Annotated[int, Field(ge=1, le=50)] = 10) -> list[dict[str, Any]]:
        """The user's most recent orders (order code, status, total). Requires scope commerce:read."""
        return await run(tools.list_orders, limit)

    @mcp.tool(title="Chi tiết đơn hàng", annotations=READ)
    async def get_order(order_code: str) -> dict[str, Any]:
        """Details of one order by order code. Requires scope commerce:read."""
        return await run(tools.get_order, order_code)

    @mcp.tool(title="Huỷ đơn hàng", annotations=DESTRUCTIVE)
    async def cancel_order(
        order_code: str,
        reason: str | None = None,
        confirm: Annotated[
            bool, Field(description="Set to true only after the user has confirmed the cancellation")
        ] = False,
    ) -> dict[str, Any]:
        """Cancel an order (cannot be undone). Requires scope commerce:orders.cancel and confirm=true."""
        return await run(tools.cancel_order, order_code, reason, confirm)

    return mcp
