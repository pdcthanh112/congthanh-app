import os
import time
import uuid

from django.db import models


def uuid7() -> uuid.UUID:
    """UUIDv7 (RFC 9562) như các service Go: khoá có thứ tự thời gian, index B-tree gọn."""
    ms = time.time_ns() // 1_000_000
    b = bytearray(ms.to_bytes(6, "big") + os.urandom(10))
    b[6] = (b[6] & 0x0F) | 0x70
    b[8] = (b[8] & 0x3F) | 0x80
    return uuid.UUID(bytes=bytes(b))


class Supplier(models.Model):
    class Status(models.TextChoices):
        PENDING = "PENDING"  # chờ admin duyệt
        ACTIVE = "ACTIVE"
        SUSPENDED = "SUSPENDED"
        CLOSED = "CLOSED"

    id = models.UUIDField(primary_key=True, default=uuid7, editable=False)
    owner_id = models.CharField(max_length=64, unique=True, help_text="Keycloak subject của người bán")
    name = models.CharField(max_length=200)
    slug = models.SlugField(max_length=220, unique=True)
    description = models.TextField(blank=True, default="")
    avatar_url = models.URLField(max_length=1500, blank=True, default="")
    background_url = models.URLField(max_length=1500, blank=True, default="")
    email = models.EmailField(blank=True, default="")
    phone = models.CharField(max_length=32, blank=True, default="")
    address = models.CharField(max_length=500, blank=True, default="")
    status = models.CharField(max_length=16, choices=Status, default=Status.PENDING)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    version = models.PositiveIntegerField(default=0)

    class Meta:
        db_table = "suppliers"
        indexes = [models.Index(fields=["status", "-created_at"], name="suppliers_status_created")]

    def __str__(self) -> str:
        return self.name
