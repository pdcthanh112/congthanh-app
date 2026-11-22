"""Adapter ra: hiện thực SupplierRepository bằng Django ORM; đổi bản ghi ORM ↔ aggregate Supplier tại đây."""

from __future__ import annotations

from django.db import IntegrityError, transaction
from django.db.models import F, Q

from .domain import PROFILE_FIELDS, Supplier, SupplierError, SupplierStatus
from .models import Supplier as SupplierRecord
from .models import snowflake_id

_WRITABLE = (*PROFILE_FIELDS, "slug", "status", "updated_at")


class DjangoSupplierRepository:
    def next_id(self) -> int:
        return snowflake_id()

    def find_by_id(self, supplier_id: int) -> Supplier | None:
        return _first(SupplierRecord.objects.filter(id=supplier_id))

    def find_by_slug(self, slug: str) -> Supplier | None:
        return _first(SupplierRecord.objects.filter(slug=slug))

    def find_by_owner(self, owner_id: str) -> Supplier | None:
        return _first(SupplierRecord.objects.filter(owner_id=owner_id))

    def slug_taken(self, slug: str) -> bool:
        return SupplierRecord.objects.filter(slug=slug).exists()

    def add(self, supplier: Supplier) -> Supplier:
        try:
            with transaction.atomic():
                record = SupplierRecord.objects.create(
                    id=supplier.id,
                    owner_id=supplier.owner_id,
                    version=supplier.version,
                    **{f: getattr(supplier, f) for f in (*PROFILE_FIELDS, "slug", "status")},
                )
        except IntegrityError as e:
            raise SupplierError.conflict("supplier slug or owner already exists") from e
        return _to_domain(record)

    def update(self, supplier: Supplier, expected_version: int) -> bool:
        values = {f: getattr(supplier, f) for f in _WRITABLE}
        try:
            with transaction.atomic():
                changed = SupplierRecord.objects.filter(pk=supplier.id, version=expected_version).update(
                    **values, version=F("version") + 1
                )
        except IntegrityError as e:
            raise SupplierError.conflict("supplier slug already exists") from e
        return changed == 1

    def search(self, query: str | None, status: str | None, offset: int, limit: int) -> tuple[list[Supplier], int]:
        qs = SupplierRecord.objects.all()
        if status:
            qs = qs.filter(status=status)
        if query:
            qs = qs.filter(Q(name__icontains=query) | Q(slug__icontains=query))
        qs = qs.order_by("-created_at", "-id")
        return [_to_domain(r) for r in qs[offset : offset + limit]], qs.count()


def _first(qs) -> Supplier | None:
    record = qs.first()
    return _to_domain(record) if record else None


def _to_domain(r: SupplierRecord) -> Supplier:
    return Supplier(
        id=r.id,
        owner_id=r.owner_id,
        name=r.name,
        slug=r.slug,
        description=r.description,
        avatar_url=r.avatar_url,
        background_url=r.background_url,
        email=r.email,
        phone=r.phone,
        address=r.address,
        status=SupplierStatus(r.status),
        created_at=r.created_at,
        updated_at=r.updated_at,
        version=r.version,
    )
