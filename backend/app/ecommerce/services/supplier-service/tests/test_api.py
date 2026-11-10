import pytest
from rest_framework.test import APIClient

from suppliers.models import Supplier
from suppliers.service import make_slug

pytestmark = pytest.mark.django_db
URL = "/api/ecommerce/suppliers"


def client(sub=None, roles=""):
    c = APIClient()
    if sub:
        c.credentials(HTTP_X_USER_ID=sub, HTTP_X_USER_ROLES=roles)
    return c


def test_seller_creates_and_admin_activates():
    seller = client("s1", "SELLER")
    r = seller.post(URL, {"name": "Đồ Gia Dụng Hà Nội", "email": "a@b.vn"}, format="json")
    assert r.status_code == 201, r.content
    body = r.json()
    assert body["status"] == "Success" and body["data"]["slug"] == "do-gia-dung-ha-noi"
    assert body["data"]["status"] == "PENDING"
    sid = body["data"]["id"]

    # Chưa duyệt: khách không thấy, chủ gian hàng thấy.
    assert client().get(f"{URL}/{sid}").status_code == 404
    assert seller.get(f"{URL}/do-gia-dung-ha-noi").json()["data"]["email"] == "a@b.vn"
    assert seller.get(f"{URL}/me").json()["data"]["id"] == sid
    assert client().get(URL).json()["data"]["totalItems"] == 0

    assert seller.put(f"{URL}/{sid}/status", {"status": "ACTIVE"}, format="json").status_code == 403
    r = client("a1", "ADMIN").put(f"{URL}/{sid}/status", {"status": "ACTIVE"}, format="json")
    assert r.status_code == 200 and r.json()["data"]["status"] == "ACTIVE"

    pub = client().get(f"{URL}/{sid}").json()["data"]
    assert pub["name"] == "Đồ Gia Dụng Hà Nội" and "email" not in pub and "ownerId" not in pub
    page = client().get(URL, {"q": "gia dụng"}).json()["data"]
    assert page["totalItems"] == 1 and page["items"][0]["id"] == sid


def test_rules():
    assert client().post(URL, {"name": "x"}, format="json").status_code == 401
    assert client("c1", "CUSTOMER").post(URL, {"name": "x"}, format="json").status_code == 403
    seller = client("s2", "SELLER")
    assert seller.post(URL, {"name": "Shop A"}, format="json").status_code == 201
    r = seller.post(URL, {"name": "Shop B"}, format="json")
    assert r.status_code == 409 and r.json()["errorCode"] == 409
    r = seller.post(URL, {}, format="json")
    assert r.status_code == 400  # đã có gian hàng hoặc thiếu name đều bị chặn
    assert client("s3", "SELLER").post(URL, {"name": ""}, format="json").json()["message"].startswith("Invalid request")


def test_update_optimistic_lock_and_ownership():
    seller = client("s4", "SELLER")
    s = seller.post(URL, {"name": "Shop C"}, format="json").json()["data"]
    r = seller.patch(f"{URL}/{s['id']}", {"description": "mới", "version": 0}, format="json")
    assert r.status_code == 200 and r.json()["data"]["version"] == 1
    assert seller.patch(f"{URL}/{s['id']}", {"description": "cũ", "version": 0}, format="json").status_code == 409
    assert client("other", "SELLER").patch(f"{URL}/{s['id']}", {"name": "hack"}, format="json").status_code == 403
    assert client().patch(f"{URL}/{s['id']}", {"name": "hack"}, format="json").status_code == 401
    # Không tự đổi trạng thái qua PATCH.
    seller.patch(f"{URL}/{s['id']}", {"status": "ACTIVE"}, format="json")
    assert Supplier.objects.get(id=s["id"]).status == "PENDING"


def test_slug_unique():
    Supplier.objects.create(owner_id="x1", name="Shop", slug="shop")
    assert make_slug("Shop") == "shop-2"


def test_health():
    assert client().get("/health/ready").json() == {"status": "UP"}
