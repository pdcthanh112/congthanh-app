import pytest
from rest_framework.test import APIClient

from suppliers.models import Supplier

pytestmark = pytest.mark.django_db
URL = "/api/ecommerce/suppliers"


def client(sub=None, roles=""):
    c = APIClient()
    if sub:
        c.credentials(HTTP_X_USER_ID=sub, HTTP_X_USER_ROLES=roles)
    return c


def test_seller_creates_and_admin_activates():
    seller = client("s1", "SELLER")
    r = seller.post(URL, {"name": "Brooklyn Home Goods", "email": "a@b.com"}, format="json")
    assert r.status_code == 201, r.content
    body = r.json()
    assert body["status"] == "Success" and body["data"]["slug"] == "brooklyn-home-goods"
    assert body["data"]["status"] == "PENDING"
    sid = body["data"]["id"]
    assert isinstance(sid, str) and sid.isdigit()

    assert client().get(f"{URL}/{sid}").status_code == 404
    assert seller.get(f"{URL}/brooklyn-home-goods").json()["data"]["email"] == "a@b.com"
    assert seller.get(f"{URL}/me").json()["data"]["id"] == sid
    assert client().get(URL).json()["data"]["totalItems"] == 0

    assert seller.put(f"{URL}/{sid}/status", {"status": "ACTIVE"}, format="json").status_code == 403
    r = client("a1", "ADMIN").put(f"{URL}/{sid}/status", {"status": "ACTIVE"}, format="json")
    assert r.status_code == 200 and r.json()["data"]["status"] == "ACTIVE"

    pub = client().get(f"{URL}/{sid}").json()["data"]
    assert pub["name"] == "Brooklyn Home Goods" and "email" not in pub and "ownerId" not in pub
    page = client().get(URL, {"q": "home"}).json()["data"]
    assert page["totalItems"] == 1 and page["items"][0]["id"] == sid


def test_rules():
    assert client().post(URL, {"name": "x"}, format="json").status_code == 401
    assert client("c1", "CUSTOMER").post(URL, {"name": "x"}, format="json").status_code == 403
    seller = client("s2", "SELLER")
    assert seller.post(URL, {"name": "Shop A"}, format="json").status_code == 201
    r = seller.post(URL, {"name": "Shop B"}, format="json")
    assert r.status_code == 409 and r.json()["errorCode"] == 409
    r = seller.post(URL, {}, format="json")
    assert r.status_code == 400
    assert client("s3", "SELLER").post(URL, {"name": ""}, format="json").json()["message"].startswith("Invalid request")


def test_update_optimistic_lock_and_ownership():
    seller = client("s4", "SELLER")
    s = seller.post(URL, {"name": "Shop C"}, format="json").json()["data"]
    r = seller.patch(f"{URL}/{s['id']}", {"description": "mới", "version": 0}, format="json")
    assert r.status_code == 200 and r.json()["data"]["version"] == 1
    assert seller.patch(f"{URL}/{s['id']}", {"description": "cũ", "version": 0}, format="json").status_code == 409
    assert client("other", "SELLER").patch(f"{URL}/{s['id']}", {"name": "hack"}, format="json").status_code == 403
    assert client().patch(f"{URL}/{s['id']}", {"name": "hack"}, format="json").status_code == 401

    seller.patch(f"{URL}/{s['id']}", {"status": "ACTIVE"}, format="json")
    assert Supplier.objects.get(id=s["id"]).status == "PENDING"


def test_slug_unique():
    Supplier.objects.create(owner_id="x1", name="Shop", slug="shop")
    r = client("s5", "SELLER").post(URL, {"name": "Shop"}, format="json")
    assert r.status_code == 201 and r.json()["data"]["slug"] == "shop-2"
    r = client("s6", "SELLER").post(URL, {"name": "Other", "slug": "shop"}, format="json")
    assert r.status_code == 409


def test_status_transitions_follow_lifecycle():
    seller = client("s7", "SELLER")
    sid = seller.post(URL, {"name": "Shop D"}, format="json").json()["data"]["id"]
    admin = client("a1", "ADMIN")
    assert admin.put(f"{URL}/{sid}/status", {"status": "SUSPENDED"}, format="json").status_code == 409
    assert admin.put(f"{URL}/{sid}/status", {"status": "CLOSED"}, format="json").status_code == 200
    assert admin.put(f"{URL}/{sid}/status", {"status": "ACTIVE"}, format="json").status_code == 409


def test_edit_updates_timestamp():
    seller = client("s8", "SELLER")
    s = seller.post(URL, {"name": "Shop E"}, format="json").json()["data"]
    updated = seller.patch(f"{URL}/{s['id']}", {"description": "x"}, format="json").json()["data"]
    assert updated["updatedAt"] >= s["updatedAt"] and updated["version"] == 1


def test_health():
    assert client().get("/health/ready").json() == {"status": "UP"}
