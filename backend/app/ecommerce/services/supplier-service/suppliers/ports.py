"""Port (driven) của supplier-service: kho gian hàng; hiện thực bằng Django ORM ở `repository.py`."""

from __future__ import annotations

from typing import Protocol

from .domain import Supplier


class SupplierRepository(Protocol):
    def next_id(self) -> int:
        """Id Snowflake mới (ADR 0028)."""
        ...

    def find_by_id(self, supplier_id: int) -> Supplier | None: ...

    def find_by_slug(self, slug: str) -> Supplier | None: ...

    def find_by_owner(self, owner_id: str) -> Supplier | None: ...

    def slug_taken(self, slug: str) -> bool: ...

    def add(self, supplier: Supplier) -> Supplier:
        """Lưu gian hàng mới, trả bản đã lưu; trùng owner hoặc slug thì ném SupplierError CONFLICT."""
        ...

    def update(self, supplier: Supplier, expected_version: int) -> bool:
        """Khoá lạc quan: False nếu version trong DB khác expected_version. Trùng slug thì ném SupplierError CONFLICT."""
        ...

    def search(self, query: str | None, status: str | None, offset: int, limit: int) -> tuple[list[Supplier], int]:
        """Danh sách mới nhất trước, lọc theo tên/slug chứa query và theo trạng thái; trả (trang, tổng số)."""
        ...
