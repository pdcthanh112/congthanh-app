from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class OdsTable:
    source: str  
    name: str  
    keys: tuple[str, ...]
    columns: tuple[tuple[str, str], ...]  

    @property
    def topic(self) -> str:
        return f"ods.{self.source}"


_ADDRESS = tuple(
    (f"{p}_{f}", "string")
    for p in ("ship", "bill")
    for f in ("full_name", "phone", "line1", "line2", "ward", "district", "city", "country", "postal_code")
)

ODS_TABLES: tuple[OdsTable, ...] = (
    OdsTable(
        source="order_service.orders",
        name="orders",
        keys=("id",),
        columns=(
            ("id", "string"),
            ("order_code", "string"),
            ("customer_id", "string"),
            ("status", "string"),
            ("currency", "string"),
            ("subtotal", "decimal(19,4)"),
            ("discount", "decimal(19,4)"),
            ("total", "decimal(19,4)"),
            ("promotion_code", "string"),
            ("payment_method", "string"),
            ("reservation_id", "string"),
            ("payment_id", "string"),
            ("cancel_reason", "string"),
            ("note", "string"),
            *_ADDRESS,
            ("placed_at", "timestamp"),
            ("updated_at", "timestamp"),
            ("version", "bigint"),
        ),
    ),
    OdsTable(
        source="order_service.order_lines",
        name="order_lines",
        keys=("id",),
        columns=(
            ("id", "bigint"),
            ("order_id", "string"),
            ("product_id", "string"),
            ("sku", "string"),
            ("name", "string"),
            ("quantity", "int"),
            ("unit_price", "decimal(19,4)"),
        ),
    ),
    OdsTable(
        source="payment_service.payments",
        name="payments",
        keys=("id",),
        columns=(
            ("id", "string"),
            ("order_id", "string"),
            ("customer_id", "string"),
            ("amount", "decimal(19,4)"),
            ("currency", "string"),
            ("method", "string"),
            ("provider", "string"),
            ("provider_ref", "string"),
            ("status", "string"),
            ("failure_reason", "string"),
            ("created_at", "timestamp"),
            ("updated_at", "timestamp"),
            ("version", "bigint"),
        ),
    ),
    OdsTable(
        source="product_service.products",
        name="products",
        keys=("id",),
        columns=(
            ("id", "string"),
            ("name", "string"),
            ("sku", "string"),
            ("slug", "string"),
            ("gtin", "string"),
            ("status", "string"),
            ("price", "decimal(19,2)"),
            ("has_variant", "boolean"),
            ("is_featured", "boolean"),
            ("brand", "bigint"),
            ("thumbnail", "bigint"),
            ("parent_id", "string"),
            ("supplier", "string"),
            ("description", "string"),
        ),
    ),
)

META_COLUMNS: tuple[tuple[str, str], ...] = (
    ("_op", "string"),  
    ("_source_ts", "timestamp"),  
    ("_lsn", "bigint"),  
    ("_ods_updated_at", "timestamp"),
)
