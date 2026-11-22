"""Tài liệu OpenAPI và Swagger UI phục vụ được mà không cần DB."""

from __future__ import annotations

from django.test import Client

from suppliers.domain import RESERVED_SLUGS
from suppliers.service import SupplierService
from tests.test_domain import FakeRepo

DOCS = "/api/ecommerce/suppliers"


def test_openapi_lists_supplier_paths():
    r = Client().get(f"{DOCS}/openapi.json")
    assert r.status_code == 200
    paths = r.json()["paths"]
    assert {"/api/ecommerce/suppliers", "/api/ecommerce/suppliers/{key}", "/api/ecommerce/suppliers/me"} <= set(paths)


def test_swagger_ui_served():
    r = Client().get(f"{DOCS}/docs")
    assert r.status_code == 200 and b"swagger" in r.content.lower()


def test_reserved_slugs_are_skipped():
    svc = SupplierService(FakeRepo())
    assert "docs" in RESERVED_SLUGS
    assert svc.unique_slug("Docs") == "docs-2"
