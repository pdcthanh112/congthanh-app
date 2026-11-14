"""Định nghĩa feature cho Feast, nguồn là gold.customer_features (dbt build hằng giờ).

Dùng cho machine-learning/customer-purchase-prediction:
    training : store.get_historical_features(entity_df="SELECT customer_id, current_timestamp AS event_timestamp ...",
                                              features=store.get_feature_service("customer_purchase_v1"))
    online   : POST http://localhost:6566/get-online-features  {"feature_service": "customer_purchase_v1",
                                                               "entities": {"customer_id": ["customer-1"]}}
"""

from datetime import timedelta

from feast import Entity, FeatureService, FeatureView, Field, ValueType
from feast.infra.offline_stores.contrib.trino_offline_store.trino_source import TrinoSource
from feast.types import Float64, Int64, String

customer = Entity(
    name="customer",
    join_keys=["customer_id"],
    value_type=ValueType.STRING,
    description="Khách hàng (customer_id của order-service).",
)

# Ép decimal/integer về double/bigint vì Feast chỉ map các kiểu cơ bản của Trino.
# Một khách có thể mua bằng nhiều tiền tệ: lấy dòng của tiền tệ có giá trị mua lớn nhất.
customer_features_source = TrinoSource(
    name="gold_customer_features",
    timestamp_field="computed_at",
    query="""
        SELECT customer_id,
               CAST(recency_days AS bigint) AS recency_days,
               CAST(frequency AS bigint) AS frequency,
               CAST(monetary AS double) AS monetary,
               CAST(avg_order_value AS double) AS avg_order_value,
               currency,
               CAST(r_score AS bigint) AS r_score,
               CAST(f_score AS bigint) AS f_score,
               CAST(m_score AS bigint) AS m_score,
               segment,
               CAST(product_views_30d AS bigint) AS product_views_30d,
               CAST(add_to_cart_30d AS bigint) AS add_to_cart_30d,
               CAST(searches_30d AS bigint) AS searches_30d,
               CAST(sessions_30d AS bigint) AS sessions_30d,
               CAST(payment_failures_1h AS bigint) AS payment_failures_1h,
               CAST(cancellations_24h AS bigint) AS cancellations_24h,
               CAST(computed_at AS timestamp(6) with time zone) AS computed_at
        FROM (
            SELECT *, row_number() OVER (PARTITION BY customer_id ORDER BY monetary DESC) AS rn
            FROM gold.customer_features
        )
        WHERE rn = 1
    """,
)

customer_rfm = FeatureView(
    name="customer_rfm",
    entities=[customer],
    ttl=timedelta(days=3),
    schema=[
        Field(name="recency_days", dtype=Int64),
        Field(name="frequency", dtype=Int64),
        Field(name="monetary", dtype=Float64),
        Field(name="avg_order_value", dtype=Float64),
        Field(name="currency", dtype=String),
        Field(name="r_score", dtype=Int64),
        Field(name="f_score", dtype=Int64),
        Field(name="m_score", dtype=Int64),
        Field(name="segment", dtype=String),
    ],
    source=customer_features_source,
    online=True,
    tags={"layer": "gold", "owner": "data"},
)

customer_behaviour = FeatureView(
    name="customer_behaviour",
    entities=[customer],
    ttl=timedelta(days=3),
    schema=[
        Field(name="product_views_30d", dtype=Int64),
        Field(name="add_to_cart_30d", dtype=Int64),
        Field(name="searches_30d", dtype=Int64),
        Field(name="sessions_30d", dtype=Int64),
        Field(name="payment_failures_1h", dtype=Int64),
        Field(name="cancellations_24h", dtype=Int64),
    ],
    source=customer_features_source,
    online=True,
    tags={"layer": "gold", "owner": "data"},
)

customer_purchase_v1 = FeatureService(
    name="customer_purchase_v1",
    features=[customer_rfm, customer_behaviour],
    description="Đầu vào model dự đoán khả năng mua (customer-purchase-prediction).",
)
