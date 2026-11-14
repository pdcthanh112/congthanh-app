SELECT customer_id, orders_1h, order_value_1h, currency, payment_failures_1h, cancellations_24h,
       distinct_addresses_24h, signal_at, current_timestamp AS computed_at
FROM (
    SELECT
        aggregate_id AS customer_id,
        try_cast(json_extract_scalar(payload_json, '$.orders_1h') AS integer) AS orders_1h,
        try_cast(json_extract_scalar(payload_json, '$.order_value_1h') AS bigint) AS order_value_1h,
        json_extract_scalar(payload_json, '$.currency') AS currency,
        coalesce(try_cast(json_extract_scalar(payload_json, '$.payment_failures_1h') AS integer), 0) AS payment_failures_1h,
        coalesce(try_cast(json_extract_scalar(payload_json, '$.cancellations_24h') AS integer), 0) AS cancellations_24h,
        try_cast(json_extract_scalar(payload_json, '$.distinct_addresses_24h') AS integer) AS distinct_addresses_24h,
        occurred_at AS signal_at,
        row_number() OVER (PARTITION BY aggregate_id ORDER BY occurred_at DESC, kafka_offset DESC) AS rn
    FROM {{ source('decoded', 'stream_signals') }}
    WHERE kafka_topic = 'ecommerce.stream.customer-risk'
)
WHERE rn = 1
