"""Composition root: nối adapter Django vào use case; view chỉ lấy service qua đây."""

from __future__ import annotations

from functools import cache

from .repository import DjangoSupplierRepository
from .service import SupplierService


@cache
def supplier_service() -> SupplierService:
    return SupplierService(DjangoSupplierRepository())
