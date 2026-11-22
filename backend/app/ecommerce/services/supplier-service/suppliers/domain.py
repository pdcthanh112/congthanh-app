"""Aggregate Supplier (gian hàng của người bán): Python thuần, không import Django hay DRF.

Vòng đời: PENDING (chờ duyệt) → ACTIVE ⇄ SUSPENDED; mọi trạng thái chưa đóng đều chuyển được sang CLOSED; CLOSED là
trạng thái cuối. Chỉ chủ gian hàng hoặc admin được sửa; khách chỉ thấy gian hàng ACTIVE.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum

# Slug gốc tối đa 200 ký tự để còn chỗ cho hậu tố "-N" khi trùng (cột slug dài 220).
SLUG_BASE_MAX = 200
NAME_MAX = 200
# Trùng với route cố định dưới /api/ecommerce/suppliers/ nên không dùng làm slug gian hàng.
RESERVED_SLUGS = frozenset({"me", "docs", "openapi.json"})
PROFILE_FIELDS = ("name", "description", "avatar_url", "background_url", "email", "phone", "address")


class SupplierStatus(StrEnum):
    PENDING = "PENDING"
    ACTIVE = "ACTIVE"
    SUSPENDED = "SUSPENDED"
    CLOSED = "CLOSED"


ALLOWED_TRANSITIONS: dict[SupplierStatus, frozenset[SupplierStatus]] = {
    SupplierStatus.PENDING: frozenset({SupplierStatus.ACTIVE, SupplierStatus.CLOSED}),
    SupplierStatus.ACTIVE: frozenset({SupplierStatus.SUSPENDED, SupplierStatus.CLOSED}),
    SupplierStatus.SUSPENDED: frozenset({SupplierStatus.ACTIVE, SupplierStatus.CLOSED}),
    SupplierStatus.CLOSED: frozenset(),
}


class ErrorKind(StrEnum):
    INVALID = "INVALID"
    NOT_FOUND = "NOT_FOUND"
    CONFLICT = "CONFLICT"
    FORBIDDEN = "FORBIDDEN"


class SupplierError(Exception):
    """Lỗi nghiệp vụ; adapter web đổi `kind` sang HTTP status."""

    def __init__(self, kind: ErrorKind, message: str) -> None:
        super().__init__(message)
        self.kind = kind

    @classmethod
    def invalid(cls, message: str) -> SupplierError:
        return cls(ErrorKind.INVALID, message)

    @classmethod
    def not_found(cls, message: str) -> SupplierError:
        return cls(ErrorKind.NOT_FOUND, message)

    @classmethod
    def conflict(cls, message: str) -> SupplierError:
        return cls(ErrorKind.CONFLICT, message)

    @classmethod
    def forbidden(cls, message: str) -> SupplierError:
        return cls(ErrorKind.FORBIDDEN, message)


@dataclass(frozen=True, slots=True)
class Actor:
    """Người đang gọi API (subject Keycloak) và có phải admin không."""

    id: str
    is_admin: bool = False


@dataclass
class Supplier:
    id: int
    owner_id: str
    name: str
    slug: str
    description: str = ""
    avatar_url: str = ""
    background_url: str = ""
    email: str = ""
    phone: str = ""
    address: str = ""
    status: SupplierStatus = SupplierStatus.PENDING
    created_at: datetime | None = None
    updated_at: datetime | None = None
    version: int = 0

    @classmethod
    def register(cls, id: int, owner_id: str, slug: str, profile: dict, now: datetime) -> Supplier:
        """Gian hàng mới luôn ở trạng thái PENDING, chờ admin duyệt."""
        if not owner_id:
            raise SupplierError.invalid("owner is required")
        supplier = cls(id=id, owner_id=owner_id, name="", slug=slug, created_at=now, updated_at=now)
        supplier._apply_profile({"name": "", **profile})
        return supplier

    def edit(self, profile: dict, slug: str | None, now: datetime) -> None:
        """Sửa thông tin hiển thị; slug rỗng hoặc None thì giữ slug cũ."""
        self._apply_profile(profile)
        if slug:
            self.slug = slug
        self.updated_at = now

    def change_status(self, status: SupplierStatus, now: datetime) -> bool:
        """Chuyển trạng thái theo ALLOWED_TRANSITIONS; trả False nếu đã ở trạng thái đó (idempotent)."""
        if status == self.status:
            return False
        if status not in ALLOWED_TRANSITIONS[self.status]:
            raise SupplierError.conflict(f"cannot change supplier status from {self.status} to {status}")
        self.status = status
        self.updated_at = now
        return True

    def can_be_managed_by(self, actor: Actor | None) -> bool:
        return actor is not None and (actor.is_admin or actor.id == self.owner_id)

    def require_manageable_by(self, actor: Actor | None) -> None:
        if not self.can_be_managed_by(actor):
            raise SupplierError.forbidden("only the owner or an admin can edit this supplier")

    def is_visible_to(self, actor: Actor | None) -> bool:
        """Gian hàng chưa duyệt hoặc bị khoá chỉ chủ và admin thấy."""
        return self.status == SupplierStatus.ACTIVE or self.can_be_managed_by(actor)

    def _apply_profile(self, profile: dict) -> None:
        unknown = set(profile) - set(PROFILE_FIELDS)
        if unknown:
            raise SupplierError.invalid(f"unknown supplier fields: {', '.join(sorted(unknown))}")
        if "name" in profile:
            name = (profile["name"] or "").strip()
            if not name:
                raise SupplierError.invalid("name is required")
            if len(name) > NAME_MAX:
                raise SupplierError.invalid(f"name is at most {NAME_MAX} characters")
            self.name = name
        for field in PROFILE_FIELDS[1:]:
            if field in profile:
                setattr(self, field, profile[field] or "")


def slugify(name: str) -> str:
    """Slug ASCII chữ thường từ tên (giống django.utils.text.slugify, thêm xử lý chữ đ tiếng Việt)."""
    value = name.replace("đ", "d").replace("Đ", "D")
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    value = re.sub(r"[^\w\s-]", "", value.lower())
    value = re.sub(r"[-\s]+", "-", value).strip("-_")
    return value[:SLUG_BASE_MAX] or "supplier"
