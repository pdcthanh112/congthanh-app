from ecommerce_platform.auth import Principal
from mcp import Client

from app.domain import SCOPE_CART, SCOPE_READ
from app.server import build_server
from app.service import CommerceTools

from .fakes import FakeAuthz, FakeCarts, FakeCatalog, FakeOrders


def server(principal):
    tools = CommerceTools(FakeCatalog(), FakeCarts(), FakeOrders(), FakeAuthz())
    return build_server(tools, principal=lambda: principal)


async def test_tools_are_listed_with_annotations():
    async with Client(server(None)) as c:
        tools = {t.name: t for t in (await c.list_tools()).tools}
    assert set(tools) == {
        "search_products",
        "get_product",
        "view_cart",
        "add_to_cart",
        "update_cart_item",
        "list_orders",
        "get_order",
        "cancel_order",
    }
    assert tools["search_products"].annotations.read_only_hint is True
    assert tools["cancel_order"].annotations.destructive_hint is True


async def test_call_tool_end_to_end():
    p = Principal(subject="u1", token="tok-u1", scopes=frozenset({SCOPE_READ, SCOPE_CART}))
    async with Client(server(p)) as c:
        r = await c.call_tool("add_to_cart", {"product_id": "p1", "quantity": 3})
        assert not r.is_error and r.structured_content["item"]["quantity"] == 3
        r = await c.call_tool("cancel_order", {"order_code": "OD1", "confirm": True})
        assert r.is_error and "commerce:orders.cancel" in r.content[0].text


async def test_anonymous_gets_tool_error():
    async with Client(server(None)) as c:
        r = await c.call_tool("search_products", {"query": "jacket"})
    assert r.is_error and "sign in" in r.content[0].text
