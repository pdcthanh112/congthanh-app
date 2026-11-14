-- Tồn kho khả dụng so với tốc độ bán 24 giờ, bản ghi mới nhất mỗi sản phẩm từ Flink.
WITH latest AS (
    SELECT
        aggregate_id AS product_id,
        try_cast(json_extract_scalar(payload_json, '$.available') AS integer) AS available,
        try_cast(json_extract_scalar(payload_json, '$.units_sold_24h') AS integer) AS units_sold_24h,
        try_cast(json_extract_scalar(payload_json, '$.hours_of_cover') AS double) AS hours_of_cover,
        coalesce(try_cast(json_extract_scalar(payload_json, '$.at_risk') AS boolean), false) AS at_risk,
        occurred_at AS computed_at_source,
        row_number() OVER (PARTITION BY aggregate_id ORDER BY occurred_at DESC, kafka_offset DESC) AS rn
    FROM {{ source('decoded', 'stream_signals') }}
    WHERE kafka_topic = 'ecommerce.stream.stock-cover'
)
SELECT
    s.product_id,
    p.product_name,
    s.available,
    s.units_sold_24h,
    -- Âm nghĩa là chưa đủ dữ liệu bán để ước lượng.
    CASE WHEN s.hours_of_cover >= 0 THEN s.hours_of_cover END AS hours_of_cover,
    s.at_risk,
    s.computed_at_source AS signal_at,
    current_timestamp AS computed_at
FROM latest s
LEFT JOIN {{ ref('products') }} p ON p.product_id = s.product_id
WHERE s.rn = 1
