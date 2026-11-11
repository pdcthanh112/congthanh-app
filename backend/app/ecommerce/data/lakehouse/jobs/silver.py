from __future__ import annotations

from common import LOOKBACK_HOURS, TABLE_PROPS, ensure_namespaces, spark_session, table
from pyspark.sql import SparkSession

CLICK_TYPES = "'page_view','product_view','search','add_to_cart','remove_from_cart','begin_checkout','purchase'"
BOT_UA = "(?i)(bot|crawler|spider|headless|lighthouse)"


def ods_to_silver(spark: SparkSession) -> None:
    spark.sql(f"""
        CREATE OR REPLACE TABLE {table("silver", "orders")}
        USING iceberg PARTITIONED BY (months(placed_at)) TBLPROPERTIES ({TABLE_PROPS}) AS
        SELECT id AS order_id, order_code, customer_id, upper(status) AS status, upper(currency) AS currency,
               subtotal, discount, total, promotion_code, upper(payment_method) AS payment_method,
               initcap(trim(ship_city)) AS ship_city, upper(trim(ship_country)) AS ship_country,
               placed_at, updated_at, CAST(placed_at AS date) AS placed_date,
               upper(status) = 'CONFIRMED' AS is_confirmed
        FROM {table("ods", "orders")}
        WHERE id IS NOT NULL AND placed_at IS NOT NULL""")

    spark.sql(f"""
        CREATE OR REPLACE TABLE {table("silver", "order_lines")}
        USING iceberg PARTITIONED BY (months(placed_at)) TBLPROPERTIES ({TABLE_PROPS}) AS
        SELECT l.id AS order_line_id, l.order_id, l.product_id, l.sku, l.name AS product_name,
               l.quantity, l.unit_price, l.quantity * l.unit_price AS line_amount,
               o.customer_id, o.status, o.currency, o.placed_at, o.placed_date, o.is_confirmed
        FROM {table("ods", "order_lines")} l
        JOIN {table("silver", "orders")} o ON o.order_id = l.order_id""")

    spark.sql(f"""
        CREATE OR REPLACE TABLE {table("silver", "payments")}
        USING iceberg TBLPROPERTIES ({TABLE_PROPS}) AS
        SELECT id AS payment_id, order_id, customer_id, amount, upper(currency) AS currency,
               upper(method) AS method, upper(provider) AS provider, upper(status) AS status,
               failure_reason, created_at, updated_at,
               upper(status) IN ('AUTHORIZED', 'CAPTURED') AS is_successful
        FROM {table("ods", "payments")}""")

    spark.sql(f"""
        CREATE OR REPLACE TABLE {table("silver", "products")}
        USING iceberg TBLPROPERTIES ({TABLE_PROPS}) AS
        SELECT id AS product_id, name AS product_name, sku, slug, gtin, upper(status) AS status, price,
               has_variant, coalesce(is_featured, false) AS is_featured, brand AS brand_id,
               parent_id, supplier
        FROM {table("ods", "products")}
        WHERE coalesce(upper(status), '') <> 'DELETED'""")


def outbox_events(spark: SparkSession) -> None:
    target = table("silver", "outbox_events")
    spark.sql(f"""
        CREATE TABLE IF NOT EXISTS {target} (
            event_id string, event_type string, domain string, aggregate_id string,
            occurred_at timestamp, event_date date, traceparent string, payload binary,
            kafka_topic string, kafka_partition int, kafka_offset bigint)
        USING iceberg PARTITIONED BY (domain, days(occurred_at)) TBLPROPERTIES ({TABLE_PROPS})""")
    spark.sql(f"""
        CREATE OR REPLACE TEMP VIEW silver_outbox_src AS
        SELECT * FROM (
            SELECT coalesce(event_id, concat_ws(':', topic, `partition`, `offset`)) AS event_id,
                   event_type,
                   regexp_extract(topic, '^ecommerce\\\\.([a-z_-]+)\\\\.events$', 1) AS domain,
                   key AS aggregate_id, kafka_ts AS occurred_at, CAST(kafka_ts AS date) AS event_date,
                   traceparent, value AS payload,
                   topic AS kafka_topic, `partition` AS kafka_partition, `offset` AS kafka_offset,
                   row_number() OVER (PARTITION BY coalesce(event_id, concat_ws(':', topic, `partition`, `offset`))
                                      ORDER BY kafka_ts, `offset`) AS rn
            FROM {table("bronze", "outbox_events")}
            WHERE kafka_ts >= current_timestamp() - INTERVAL {LOOKBACK_HOURS} HOURS)
        WHERE rn = 1""")
    spark.sql(f"""
        MERGE INTO {target} t
        USING (SELECT * EXCEPT (rn) FROM silver_outbox_src) s
        ON t.event_id = s.event_id AND t.domain = s.domain AND t.event_date = s.event_date
        WHEN NOT MATCHED THEN INSERT *""")


def clickstream_events(spark: SparkSession) -> None:
    target = table("silver", "clickstream_events")
    spark.sql(f"""
        CREATE TABLE IF NOT EXISTS {target} (
            event_id string, event_type string, occurred_at timestamp, event_date date,
            session_id string, anonymous_id string, customer_id string,
            page_url string, referrer string, product_id string, quantity int,
            price decimal(19,4), currency string, search_query string, order_id string,
            received_at timestamp, user_agent string, ip_hash string)
        USING iceberg PARTITIONED BY (days(occurred_at)) TBLPROPERTIES ({TABLE_PROPS})""")
    schema = (
        "event_id string, event_type string, occurred_at string, session_id string, anonymous_id string, "
        "customer_id string, page_url string, referrer string, product_id string, quantity string, "
        "price string, currency string, search_query string, order_id string, received_at string, "
        "user_agent string, ip_hash string"
    )
    spark.sql(f"""
        CREATE OR REPLACE TEMP VIEW silver_click_src AS
        WITH parsed AS (
            SELECT from_json(value, '{schema}') AS e, kafka_ts, `offset`
            FROM {table("bronze", "clickstream_events")}
            WHERE kafka_ts >= current_timestamp() - INTERVAL {LOOKBACK_HOURS} HOURS
        ), typed AS (
            SELECT e.event_id, lower(e.event_type) AS event_type,
                   try_cast(e.occurred_at AS timestamp) AS occurred_at,
                   e.session_id, e.anonymous_id, nullif(e.customer_id, '') AS customer_id,
                   e.page_url, e.referrer, e.product_id, try_cast(e.quantity AS int) AS quantity,
                   try_cast(e.price AS decimal(19,4)) AS price, upper(e.currency) AS currency,
                   lower(trim(e.search_query)) AS search_query, e.order_id,
                   try_cast(e.received_at AS timestamp) AS received_at, e.user_agent, e.ip_hash,
                   kafka_ts, `offset`
            FROM parsed
        )
        SELECT event_id, event_type, occurred_at, CAST(occurred_at AS date) AS event_date,
               session_id, anonymous_id, customer_id, page_url, referrer, product_id, quantity,
               price, currency, search_query, order_id, received_at, user_agent, ip_hash
        FROM (
            SELECT *, row_number() OVER (PARTITION BY event_id ORDER BY kafka_ts, `offset`) AS rn
            FROM typed
            WHERE event_id IS NOT NULL AND session_id IS NOT NULL AND occurred_at IS NOT NULL
              AND event_type IN ({CLICK_TYPES})
              AND coalesce(user_agent, '') NOT RLIKE '{BOT_UA}'
              AND (received_at IS NULL OR occurred_at <= received_at + INTERVAL 5 MINUTES))
        WHERE rn = 1""")
    spark.sql(f"""
        MERGE INTO {target} t
        USING silver_click_src s
        ON t.event_id = s.event_id AND t.event_date = s.event_date
        WHEN NOT MATCHED THEN INSERT *""")


def main() -> None:
    spark = spark_session("silver")
    ensure_namespaces(spark)
    ods_to_silver(spark)
    outbox_events(spark)
    clickstream_events(spark)
    for name in ("orders", "order_lines", "payments", "products", "outbox_events", "clickstream_events"):
        print(f"silver.{name}: {spark.table(table('silver', name)).count()} dòng")


if __name__ == "__main__":
    main()
