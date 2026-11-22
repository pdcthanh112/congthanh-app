"""Use case của supplier-service: đăng ký, sửa, duyệt/khoá gian hàng; chỉ biết domain và port SupplierRepository."""

from __future__ import annotations

from collections.abc import Callable
from datetime import UTC, datetime

from .domain import RESERVED_SLUGS, Actor, Supplier, SupplierError, SupplierStatus, slugify
from .ports import SupplierRepository

# Id Snowflake tối đa 19 chữ số; khoá dài hơn chắc chắn là slug.
MAX_ID_DIGITS = 19


class SupplierService:
    def __init__(self, repo: SupplierRepository, clock: Callable[[], datetime] = lambda: datetime.now(UTC)) -> None:
        self._repo = repo
        self._clock = clock

    def find(self, key: str) -> Supplier | None:
        """Tìm theo id (Snowflake) hoặc slug; None nếu không có."""
        if key.isdigit() and len(key) <= MAX_ID_DIGITS:
            found = self._repo.find_by_id(int(key))
            if found:
                return found
        return self._repo.find_by_slug(key)

    def get_visible(self, key: str, actor: Actor | None) -> Supplier:
        """Gian hàng chưa duyệt hoặc bị khoá báo NOT_FOUND với người ngoài, để không lộ là có tồn tại."""
        supplier = self.find(key)
        if supplier is None or not supplier.is_visible_to(actor):
            raise SupplierError.not_found(f"supplier {key} not found")
        return supplier

    def mine(self, owner_id: str) -> Supplier:
        supplier = self._repo.find_by_owner(owner_id)
        if supplier is None:
            raise SupplierError.not_found("this account has no supplier")
        return supplier

    def search(self, query: str | None, status: str | None, page: int, size: int) -> tuple[list[Supplier], int]:
        return self._repo.search(query, status, (page - 1) * size, size)

    def register(self, actor: Actor, profile: dict) -> Supplier:
        """Mỗi tài khoản chỉ có một gian hàng; slug bỏ trống thì sinh từ tên và thêm hậu tố khi trùng."""
        if self._repo.find_by_owner(actor.id):
            raise SupplierError.conflict("this account already has a supplier")
        fields = dict(profile)
        slug = fields.pop("slug", None)
        if slug and self._slug_unavailable(slug):
            raise SupplierError.conflict(f"supplier slug {slug} already exists")
        supplier = Supplier.register(
            self._repo.next_id(), actor.id, slug or self.unique_slug(fields.get("name", "")), fields, self._clock()
        )
        return self._repo.add(supplier)

    def edit(self, actor: Actor, key: str, changes: dict) -> Supplier:
        """Không gửi version thì so với version vừa đọc; version lệch thì CONFLICT."""
        supplier = self.find(key)
        if supplier is None:
            raise SupplierError.not_found(f"supplier {key} not found")
        supplier.require_manageable_by(actor)
        fields = dict(changes)
        expected = fields.pop("version", None)
        expected = supplier.version if expected is None else expected
        slug = fields.pop("slug", None)
        if slug and slug != supplier.slug and self._slug_unavailable(slug):
            raise SupplierError.conflict(f"supplier slug {slug} already exists")
        supplier.edit(fields, slug, self._clock())
        return self._save(supplier, expected)

    def change_status(self, key: str, status: SupplierStatus) -> Supplier:
        """Admin duyệt/khoá/đóng gian hàng; chuyển sang đúng trạng thái hiện tại thì không đổi gì."""
        supplier = self.find(key)
        if supplier is None:
            raise SupplierError.not_found(f"supplier {key} not found")
        if not supplier.change_status(status, self._clock()):
            return supplier
        return self._save(supplier, supplier.version)

    def unique_slug(self, name: str) -> str:
        base = slugify(name)
        slug, n = base, 1
        while self._slug_unavailable(slug):
            n += 1
            slug = f"{base}-{n}"
        return slug

    def _slug_unavailable(self, slug: str) -> bool:
        return slug in RESERVED_SLUGS or self._repo.slug_taken(slug)

    def _save(self, supplier: Supplier, expected_version: int) -> Supplier:
        if not self._repo.update(supplier, expected_version):
            raise SupplierError.conflict("supplier was modified by someone else, reload and retry")
        supplier.version = expected_version + 1
        return supplier
