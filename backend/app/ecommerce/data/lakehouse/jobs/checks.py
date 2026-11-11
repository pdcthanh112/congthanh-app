from __future__ import annotations

import sys

from common import spark_session, table

CHECKS = [
    ("silver.orders: order_id trùng", f"SELECT count(*) - count(DISTINCT order_id) FROM {table('silver', 'orders')}"),
    (
        "silver.orders: total ≠ subtotal - discount",
        f"SELECT count(*) FROM {table('silver', 'orders')} WHERE total <> subtotal - discount",
    ),
    (
        "silver.order_lines: số lượng hoặc đơn giá âm",
        f"SELECT count(*) FROM {table('silver', 'order_lines')} WHERE quantity <= 0 OR unit_price < 0",
    ),
    (
        "silver.clickstream_events: event_id trùng",
        f"SELECT count(*) - count(DISTINCT event_id) FROM {table('silver', 'clickstream_events')}",
    ),
    (
        "silver.outbox_events: event_id trùng",
        f"SELECT count(*) - count(DISTINCT event_id) FROM {table('silver', 'outbox_events')}",
    ),
]


def main() -> int:
    spark = spark_session("checks")
    failed = 0
    for name, sql in CHECKS:
        bad = spark.sql(sql).collect()[0][0] or 0
        print(f"{'FAIL' if bad else 'OK  '} {name}: {bad}")
        failed += 1 if bad else 0
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
