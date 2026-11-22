"""Test aggregate Supplier và use case với repository giả, không cần DB."""

from __future__ import annotations

from datetime import UTC, datetime

import pytest

from suppliers.domain import Actor, ErrorKind, Supplier, SupplierError, SupplierStatus, slugify
from suppliers.service import SupplierService

NOW = datetime(2026, 11, 22, tzinfo=UTC)


class FakeRepo:
    def __init__(self) -> None:
        self.rows: dict[int, Supplier] = {}
        self._next = 0

    def next_id(self) -> int:
        self._next += 1
        return self._next

    def find_by_id(self, supplier_id: int) -> Supplier | None:
        return self.rows.get(supplier_id)

    def find_by_slug(self, slug: str) -> Supplier | None:
        return next((s for s in self.rows.values() if s.slug == slug), None)

    def find_by_owner(self, owner_id: str) -> Supplier | None:
        return next((s for s in self.rows.values() if s.owner_id == owner_id), None)

    def slug_taken(self, slug: str) -> bool:
        return self.find_by_slug(slug) is not None

    def add(self, supplier: Supplier) -> Supplier:
        self.rows[supplier.id] = supplier
        return supplier

    def update(self, supplier: Supplier, expected_version: int) -> bool:
        return self.rows[supplier.id].version == expected_version

    def search(self, query, status, offset, limit):
        items = list(self.rows.values())
        return items[offset : offset + limit], len(items)


def _supplier(**kw) -> Supplier:
    return Supplier.register(1, "owner", "shop", {"name": "Shop", **kw}, NOW)


@pytest.mark.parametrize(
    ("name", "slug"),
    [("Brooklyn Home Goods", "brooklyn-home-goods"), ("Đồ gỗ Đà Nẵng", "do-go-da-nang"), ("!!!", "supplier")],
)
def test_slugify(name, slug):
    assert slugify(name) == slug


def test_register_starts_pending_and_requires_name():
    assert _supplier().status == SupplierStatus.PENDING
    with pytest.raises(SupplierError) as e:
        Supplier.register(1, "owner", "shop", {"name": "  "}, NOW)
    assert e.value.kind == ErrorKind.INVALID


@pytest.mark.parametrize(
    ("start", "target", "allowed"),
    [
        (SupplierStatus.PENDING, SupplierStatus.ACTIVE, True),
        (SupplierStatus.PENDING, SupplierStatus.SUSPENDED, False),
        (SupplierStatus.ACTIVE, SupplierStatus.SUSPENDED, True),
        (SupplierStatus.SUSPENDED, SupplierStatus.ACTIVE, True),
        (SupplierStatus.CLOSED, SupplierStatus.ACTIVE, False),
    ],
)
def test_status_transitions(start, target, allowed):
    s = _supplier()
    s.status = start
    if allowed:
        assert s.change_status(target, NOW) and s.status == target
        return
    with pytest.raises(SupplierError) as e:
        s.change_status(target, NOW)
    assert e.value.kind == ErrorKind.CONFLICT


def test_same_status_is_noop():
    assert _supplier().change_status(SupplierStatus.PENDING, NOW) is False


def test_only_owner_or_admin_can_manage():
    s = _supplier()
    assert s.can_be_managed_by(Actor("owner")) and s.can_be_managed_by(Actor("x", is_admin=True))
    with pytest.raises(SupplierError) as e:
        s.require_manageable_by(Actor("stranger"))
    assert e.value.kind == ErrorKind.FORBIDDEN


def test_hidden_until_active():
    s = _supplier()
    assert not s.is_visible_to(None) and s.is_visible_to(Actor("owner"))
    s.change_status(SupplierStatus.ACTIVE, NOW)
    assert s.is_visible_to(None)


def test_service_rejects_second_supplier_per_account():
    svc = SupplierService(FakeRepo(), clock=lambda: NOW)
    svc.register(Actor("u1"), {"name": "A"})
    with pytest.raises(SupplierError) as e:
        svc.register(Actor("u1"), {"name": "B"})
    assert e.value.kind == ErrorKind.CONFLICT


def test_service_edit_with_stale_version_conflicts():
    svc = SupplierService(FakeRepo(), clock=lambda: NOW)
    s = svc.register(Actor("u1"), {"name": "A"})
    with pytest.raises(SupplierError) as e:
        svc.edit(Actor("u1"), str(s.id), {"description": "x", "version": 5})
    assert e.value.kind == ErrorKind.CONFLICT
