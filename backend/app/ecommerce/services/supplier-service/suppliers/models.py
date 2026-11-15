from django.db import models
from ecommerce_platform.ids import snowflake


def snowflake_id() -> int:
    """Id Snowflake dùng chung (ADR 0028), lưu BIGINT; API trả dạng chuỗi để JS không mất chính xác."""
    return int(snowflake())


class Supplier(models.Model):
    class Status(models.TextChoices):
        PENDING = "PENDING"
        ACTIVE = "ACTIVE"
        SUSPENDED = "SUSPENDED"
        CLOSED = "CLOSED"

    id = models.BigIntegerField(primary_key=True, default=snowflake_id, editable=False)
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
