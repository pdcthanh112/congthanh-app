import pytest
from ecommerce_platform.auth import Principal

from app.domain import SCOPE_CART, SCOPE_ORDERS_CANCEL, SCOPE_READ, Forbidden, InvalidInput, NotFound, Unauthenticated
from app.service import CommerceTools

from .fakes import FakeAuthz, FakeCarts, FakeCatalog, FakeOrders

ALL = frozenset({SCOPE_READ, SCOPE_CART, SCOPE_ORDERS_CANCEL})


def user(sub="u1", scopes=ALL, roles=frozenset()):
    return Principal(subject=sub, roles=roles, token=f"tok-{sub}", scopes=scopes)


@pytest.fixture
def env():
    carts, orders, authz = FakeCarts(), FakeOrders(), FakeAuthz()
    return CommerceTools(FakeCatalog(), carts, orders, authz, max_results=5), carts, orders, authz


async def test_search_requires_login_and_clamps_limit(env):
    tools, *_ = env
    with pytest.raises(Unauthenticated):
        await tools.search_products(None, "jacket")
    assert [p["id"] for p in await tools.search_products(user(scopes=frozenset()), "  jacket ", 100)] == ["p1"]
    with pytest.raises(InvalidInput):
        await tools.search_products(user(), "   ")


async def test_add_to_cart_creates_default_cart_and_checks_owner(env):
    tools, _, _, authz = env
    out = await tools.add_to_cart(user(), "p1", 2)
    assert out["cartId"] == 1 and out["item"]["quantity"] == 2
    u, rel, obj, ctx = authz.calls[-1]
    assert (u, rel, obj) == ("user:u1", "can_edit", "cart:1")
    assert any(t.relation == "owner" and t.user == "user:u1" for t in ctx)
    assert (await tools.view_cart(user()))["items"][0]["productId"] == "p1"


async def test_cart_scope_is_required(env):
    tools, *_ = env
    with pytest.raises(Forbidden, match="commerce:cart"):
        await tools.add_to_cart(user(scopes=frozenset({SCOPE_READ})), "p1")


async def test_add_unknown_product_or_bad_quantity(env):
    tools, *_ = env
    with pytest.raises(NotFound):
        await tools.add_to_cart(user(), "nope")
    with pytest.raises(InvalidInput):
        await tools.add_to_cart(user(), "p1", 0)


async def test_cart_of_someone_else_is_denied(env):
    tools, carts, *_ = env
    # cart-service trả về giỏ không thuộc người gọi (vd. lỗi ở downstream): OpenFGA vẫn chặn.
    carts.carts["u2"] = {"id": 9, "customerId": "u1", "isDefault": True, "items": []}
    with pytest.raises(Forbidden):
        await tools.view_cart(user("u2"))


async def test_support_can_view_but_not_edit_foreign_cart(env):
    tools, carts, *_ = env
    carts.carts["s1"] = {"id": 7, "customerId": "u9", "isDefault": True, "items": []}
    support = user("s1", roles=frozenset({"SUPPORT"}))
    assert (await tools.view_cart(support))["id"] == 7
    with pytest.raises(Forbidden):
        await tools.update_cart_item(support, 1, 0)


async def test_update_and_remove_cart_item(env):
    tools, *_ = env
    await tools.add_to_cart(user(), "p1", 1)
    assert (await tools.update_cart_item(user(), 1, 5))["item"]["quantity"] == 5
    assert (await tools.update_cart_item(user(), 1, 0))["removed"] == 1
    with pytest.raises(NotFound):
        await tools.update_cart_item(user(), 1, 2)


async def test_cancel_requires_confirmation_scope_and_buyer(env):
    tools, _, orders, _ = env
    with pytest.raises(Forbidden, match=r"orders\.cancel"):
        await tools.cancel_order(user(scopes=frozenset({SCOPE_READ})), "OD1", confirm=True)
    with pytest.raises(InvalidInput, match="confirm"):
        await tools.cancel_order(user(), "OD1")
    with pytest.raises(Forbidden):
        await tools.cancel_order(user("u2"), "OD1", confirm=True)
    assert orders.cancelled == []
    out = await tools.cancel_order(user(), " OD1 ", reason="  changed my mind  ", confirm=True)
    assert out["orderCode"] == "OD1" and orders.cancelled == [("OD1", "changed my mind")]


async def test_get_order_only_for_buyer(env):
    tools, *_ = env
    assert (await tools.get_order(user(), "OD1"))["orderCode"] == "OD1"
    with pytest.raises(Forbidden):
        await tools.get_order(user("u2"), "OD1")
