WITH latest AS (
    SELECT
        aggregate_id AS product_id,
        try_cast(json_extract_scalar(payload_json, '$.score') AS double) AS score,
        try_cast(json_extract_scalar(payload_json, '$.cart_adds') AS integer) AS cart_adds,
        try_cast(json_extract_scalar(payload_json, '$.orders') AS integer) AS orders,
        try_cast(json_extract_scalar(payload_json, '$.units') AS integer) AS units,
        try_cast(json_extract_scalar(payload_json, '$.wishlist_adds') AS integer) AS wishlist_adds,
        from_iso8601_timestamp(json_extract_scalar(payload_json, '$.window_start')) AS window_start,
        from_iso8601_timestamp(json_extract_scalar(payload_json, '$.window_end')) AS window_end,
        row_number() OVER (PARTITION BY aggregate_id ORDER BY occurred_at DESC, kafka_offset DESC) AS rn
    FROM {{ source('decoded', 'stream_signals') }}
    WHERE kafka_topic = 'ecommerce.stream.product-trending'
)
SELECT
    t.product_id,
    p.product_name,
    t.score,
    t.cart_adds,
    t.orders,
    t.units,
    t.wishlist_adds,
    t.window_start,
    t.window_end,
    t.window_end >= current_timestamp - INTERVAL '15' MINUTE AS is_hot,
    current_timestamp AS computed_at
FROM latest t
LEFT JOIN {{ ref('products') }} p ON p.product_id = t.product_id
WHERE t.rn = 1
