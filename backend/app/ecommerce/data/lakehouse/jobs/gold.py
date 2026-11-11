from __future__ import annotations

from common import TABLE_PROPS, ensure_namespaces, spark_session, table
from pyspark.sql import SparkSession


def daily_sales(spark: SparkSession) -> None:
    spark.sql(f"""
        CREATE OR REPLACE TABLE {table("gold", "daily_sales")} USING iceberg TBLPROPERTIES ({TABLE_PROPS}) AS
        WITH paid AS (
            SELECT order_id, sum(CASE WHEN is_successful THEN amount ELSE 0 END) AS paid_amount
            FROM {table("silver", "payments")} GROUP BY order_id
        )
        SELECT o.placed_date AS order_date, o.currency,
               count(*) AS orders_placed,
               count_if(o.status = 'CONFIRMED') AS orders_confirmed,
               count_if(o.status = 'CANCELLED') AS orders_cancelled,
               sum(CASE WHEN o.is_confirmed THEN o.subtotal ELSE 0 END) AS gross_revenue,
               sum(CASE WHEN o.is_confirmed THEN o.discount ELSE 0 END) AS discount,
               sum(CASE WHEN o.is_confirmed THEN o.total ELSE 0 END) AS net_revenue,
               round(try_divide(sum(CASE WHEN o.is_confirmed THEN o.total END), count_if(o.is_confirmed)), 2)
                   AS avg_order_value,
               coalesce(sum(p.paid_amount), 0) AS paid_amount,
               count(DISTINCT o.customer_id) AS unique_customers,
               current_timestamp() AS computed_at
        FROM {table("silver", "orders")} o
        LEFT JOIN paid p ON p.order_id = o.order_id
        GROUP BY o.placed_date, o.currency""")


def product_daily_sales(spark: SparkSession) -> None:
    spark.sql(f"""
        CREATE OR REPLACE TABLE {table("gold", "product_daily_sales")}
        USING iceberg PARTITIONED BY (months(order_date)) TBLPROPERTIES ({TABLE_PROPS}) AS
        SELECT l.placed_date AS order_date, l.product_id,
               coalesce(max(p.product_name), max(l.product_name)) AS product_name,
               l.currency,
               sum(l.quantity) AS units_sold,
               sum(l.line_amount) AS revenue,
               count(DISTINCT l.order_id) AS orders,
               current_timestamp() AS computed_at
        FROM {table("silver", "order_lines")} l
        LEFT JOIN {table("silver", "products")} p ON p.product_id = l.product_id
        WHERE l.is_confirmed
        GROUP BY l.placed_date, l.product_id, l.currency""")


def funnel_daily(spark: SparkSession) -> None:
    spark.sql(f"""
        CREATE OR REPLACE TABLE {table("gold", "funnel_daily")} USING iceberg TBLPROPERTIES ({TABLE_PROPS}) AS
        WITH s AS (
            SELECT event_date, session_id,
                   bool_or(event_type = 'product_view') AS viewed,
                   bool_or(event_type = 'add_to_cart') AS carted,
                   bool_or(event_type = 'begin_checkout') AS checked_out,
                   bool_or(event_type = 'purchase') AS purchased
            FROM {table("silver", "clickstream_events")}
            GROUP BY event_date, session_id
        )
        SELECT event_date, count(*) AS sessions,
               count_if(viewed) AS sessions_product_view,
               count_if(carted) AS sessions_add_to_cart,
               count_if(checked_out) AS sessions_begin_checkout,
               count_if(purchased) AS sessions_purchase,
               round(try_divide(count_if(carted), count_if(viewed)), 4) AS view_to_cart_rate,
               round(try_divide(count_if(purchased), count_if(carted)), 4) AS cart_to_purchase_rate,
               round(try_divide(count_if(purchased), count(*)), 4) AS conversion_rate,
               current_timestamp() AS computed_at
        FROM s GROUP BY event_date""")


def customer_features(spark: SparkSession) -> None:
    spark.sql(f"""
        CREATE OR REPLACE TABLE {table("gold", "customer_features")} USING iceberg TBLPROPERTIES ({TABLE_PROPS}) AS
        WITH orders AS (
            SELECT customer_id, currency,
                   min(placed_at) AS first_order_at, max(placed_at) AS last_order_at,
                   datediff(current_date(), max(placed_date)) AS recency_days,
                   count(*) AS frequency, sum(total) AS monetary,
                   round(avg(total), 2) AS avg_order_value
            FROM {table("silver", "orders")} WHERE is_confirmed
            GROUP BY customer_id, currency
        ), behaviour AS (
            SELECT customer_id,
                   count_if(event_type = 'product_view') AS product_views_30d,
                   count_if(event_type = 'add_to_cart') AS add_to_cart_30d,
                   count_if(event_type = 'search') AS searches_30d,
                   count(DISTINCT session_id) AS sessions_30d
            FROM {table("silver", "clickstream_events")}
            WHERE customer_id IS NOT NULL AND event_date >= date_sub(current_date(), 30)
            GROUP BY customer_id
        ), scored AS (
            SELECT o.*,
                   ntile(5) OVER (PARTITION BY currency ORDER BY recency_days DESC) AS r_score,
                   ntile(5) OVER (PARTITION BY currency ORDER BY frequency) AS f_score,
                   ntile(5) OVER (PARTITION BY currency ORDER BY monetary) AS m_score
            FROM orders o
        )
        SELECT s.*,
               CASE WHEN r_score >= 4 AND f_score >= 4 THEN 'champion'
                    WHEN f_score >= 4 THEN 'loyal'
                    WHEN r_score <= 2 AND f_score >= 3 THEN 'at_risk'
                    WHEN frequency = 1 AND r_score >= 4 THEN 'new'
                    WHEN r_score <= 2 THEN 'hibernating'
                    ELSE 'regular' END AS segment,
               coalesce(b.product_views_30d, 0) AS product_views_30d,
               coalesce(b.add_to_cart_30d, 0) AS add_to_cart_30d,
               coalesce(b.searches_30d, 0) AS searches_30d,
               coalesce(b.sessions_30d, 0) AS sessions_30d,
               current_timestamp() AS computed_at
        FROM scored s LEFT JOIN behaviour b ON b.customer_id = s.customer_id""")


def main() -> None:
    spark = spark_session("gold")
    ensure_namespaces(spark)
    daily_sales(spark)
    product_daily_sales(spark)
    funnel_daily(spark)
    customer_features(spark)
    for name in ("daily_sales", "product_daily_sales", "funnel_daily", "customer_features"):
        print(f"gold.{name}: {spark.table(table('gold', name)).count()} dòng")


if __name__ == "__main__":
    main()
