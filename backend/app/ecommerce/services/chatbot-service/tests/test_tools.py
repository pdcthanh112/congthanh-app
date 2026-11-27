import json

import pytest

from app.ports import ToolCall
from app.tools import DESCRIPTION_MAX_CHARS, ToolBox
from tests.fakes import FakeShop, card


async def run(name, args, token="tok", shop=None):
    return await ToolBox(shop or FakeShop()).run(ToolCall("t1", name, args), token, "session-0001")


def test_guest_definitions_hide_account_tools():
    guest = {d["name"] for d in ToolBox.definitions(signed_in=False)}
    member = {d["name"] for d in ToolBox.definitions(signed_in=True)}
    assert guest == {"search_products", "get_product", "recommend_products"}
    assert {"view_cart", "add_to_cart", "update_cart_item", "list_orders", "get_order"} <= member
    assert all(d["strict"] and d["input_schema"]["additionalProperties"] is False for d in ToolBox.definitions(True))


@pytest.mark.parametrize(
    ("name", "args", "message"),
    [
        ("add_to_cart", {"product_id": "p1", "quantity": 0}, "between 1 and 99"),
        ("add_to_cart", {"product_id": "p1", "quantity": "2"}, "must be an integer"),
        ("update_cart_item", {"item_id": "1", "quantity": 100}, "between 0 and 99"),
        ("search_products", {"query": " "}, "query is required"),
        ("search_products", {"query": "x", "max_price": -1}, "non-negative"),
        ("search_products", {"query": "x", "sort": "CHEAPEST"}, "sort must be one of"),
        ("get_product", {}, "give product_id or slug"),
        ("recommend_products", {"kind": "SIMILAR"}, "needs product_id"),
        ("delete_everything", {}, "unknown tool"),
    ],
)
async def test_invalid_input_is_returned_as_tool_error(name, args, message):
    out = await run(name, args)
    assert out.is_error and message in out.content


async def test_business_error_from_shop_is_tool_error():
    out = await run("add_to_cart", {"product_id": "nope", "quantity": 1})
    assert out.is_error and "not found" in out.content


async def test_product_detail_is_condensed_for_model():
    product = {
        **card("p9", "Phone", 999),
        "description": "<p>Great&nbsp;phone</p>" + "x" * 5000,
        "attributes": [{"name": "RAM", "value": "8 GB"}],
        "specifications": [
            {"name": "Pin", "items": [{"name": "Dung lượng", "value": "5000 mAh"}, {"name": "RAM", "value": "x"}]}
        ],
        "options": [{"name": "Color", "values": ["Black"]}],
        "variants": [{"id": "p9-b", "options": [{"name": "Color", "value": "Black"}], "price": None, "stock": None}],
        "stock": {"status": "IN_STOCK", "available": 3},
    }
    out = await run("get_product", {"product_id": "p9"}, shop=FakeShop([product]))
    data = json.loads(out.content)
    assert "<p>" not in data["description"] and len(data["description"]) <= DESCRIPTION_MAX_CHARS
    assert data["specifications"] == {"RAM": "8 GB", "Dung lượng": "5000 mAh"}
    assert data["variants"][0] == {"id": "p9-b", "options": {"Color": "Black"}, "price": "$999.00", "stock": None}
    assert out.products and out.products[0]["id"] == "p9"


async def test_order_hides_full_address():
    data = json.loads((await run("get_order", {"order_code": "ORD-1"})).content)
    assert data["shipTo"] == "Austin, US"
    assert "Secret" not in json.dumps(data) and "555" not in json.dumps(data)


async def test_view_cart_does_not_emit_cart_update():
    out = await run("view_cart", {})
    assert not out.is_error and out.cart is None
