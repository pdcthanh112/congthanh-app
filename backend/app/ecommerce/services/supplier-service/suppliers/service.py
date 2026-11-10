"""Nghiệp vụ gian hàng (tách khỏi view để test không cần HTTP)."""

from __future__ import annotations

import uuid

from django.db import IntegrityError, transaction
from django.db.models import F, Q, QuerySet
from django.utils.text import slugify

from .models import Supplier


class Conflict(Exception):
    pass


def make_slug(name: str) -> str:
    # slugify bỏ dấu qua NFKD nhưng "đ" không tách được: đổi tay trước.
    base = slugify(name.replace("đ", "d").replace("Đ", "D"))[:200] or "supplier"
    slug, n = base, 1
    while Supplier.objects.filter(slug=slug).exists():
        n += 1
        slug = f"{base}-{n}"
    return slug


def find(key: str) -> Supplier | None:
    """Tìm theo id (UUID) hoặc slug."""
    try:
        return Supplier.objects.filter(id=uuid.UUID(key)).first()
    except ValueError:
        return Supplier.objects.filter(slug=key).first()


def search(q: str | None, status: str | None) -> QuerySet[Supplier]:
    qs = Supplier.objects.all()
    if status:
        qs = qs.filter(status=status)
    if q:
        qs = qs.filter(Q(name__icontains=q) | Q(slug__icontains=q))
    return qs.order_by("-created_at", "-id")


def create(owner_id: str, data: dict) -> Supplier:
    if Supplier.objects.filter(owner_id=owner_id).exists():
        raise Conflict("this account already has a supplier")
    try:
        with transaction.atomic():
            return Supplier.objects.create(
                owner_id=owner_id, slug=data.pop("slug", None) or make_slug(data["name"]), **data
            )
    except IntegrityError as e:
        raise Conflict("supplier slug or owner already exists") from e


def update(s: Supplier, data: dict) -> Supplier:
    expected = data.pop("version", None)
    if "slug" in data and not data["slug"]:
        data.pop("slug")
    try:
        with transaction.atomic():
            qs = Supplier.objects.filter(pk=s.pk)
            if expected is not None:
                qs = qs.filter(version=expected)
            if not qs.update(**data, version=F("version") + 1):
                raise Conflict("supplier was modified by someone else, reload and retry")
    except IntegrityError as e:
        raise Conflict("supplier slug already exists") from e
    s.refresh_from_db()
    return s


def set_status(s: Supplier, status: str) -> Supplier:
    return update(s, {"status": status})
