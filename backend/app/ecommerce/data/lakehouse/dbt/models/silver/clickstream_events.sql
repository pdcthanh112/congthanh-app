{{ config(
    materialized='incremental',
    incremental_strategy='merge',
    unique_key='event_id',
    on_schema_change='append_new_columns',
    properties={"partitioning": "ARRAY['day(occurred_at)']"}
) }}

WITH parsed AS (
    SELECT
        json_extract_scalar(value, '$.event_id') AS event_id,
        lower(json_extract_scalar(value, '$.event_type')) AS event_type,
        try_cast(from_iso8601_timestamp(json_extract_scalar(value, '$.occurred_at')) AS timestamp(6) with time zone) AS occurred_at,
        json_extract_scalar(value, '$.session_id') AS session_id,
        json_extract_scalar(value, '$.anonymous_id') AS anonymous_id,
        nullif(json_extract_scalar(value, '$.customer_id'), '') AS customer_id,
        json_extract_scalar(value, '$.page_url') AS page_url,
        json_extract_scalar(value, '$.referrer') AS referrer,
        json_extract_scalar(value, '$.product_id') AS product_id,
        try_cast(json_extract_scalar(value, '$.quantity') AS integer) AS quantity,
        try_cast(json_extract_scalar(value, '$.price') AS decimal(19,4)) AS price,
        upper(json_extract_scalar(value, '$.currency')) AS currency,
        lower(trim(json_extract_scalar(value, '$.search_query'))) AS search_query,
        json_extract_scalar(value, '$.order_id') AS order_id,
        try_cast(from_iso8601_timestamp(json_extract_scalar(value, '$.received_at')) AS timestamp(6) with time zone) AS received_at,
        json_extract_scalar(value, '$.user_agent') AS user_agent,
        json_extract_scalar(value, '$.ip_hash') AS ip_hash,
        kafka_ts,
        "offset"
    FROM {{ source('bronze', 'clickstream_events') }}
    WHERE {{ lookback('kafka_ts') }}
), valid AS (
    SELECT *, row_number() OVER (PARTITION BY event_id ORDER BY kafka_ts, "offset") AS rn
    FROM parsed
    WHERE event_id IS NOT NULL AND session_id IS NOT NULL AND occurred_at IS NOT NULL
      AND event_type IN ('page_view', 'product_view', 'search', 'add_to_cart', 'remove_from_cart',
                         'begin_checkout', 'purchase')
      AND NOT regexp_like(coalesce(user_agent, ''), '(?i)(bot|crawler|spider|headless|lighthouse)')
      AND (received_at IS NULL OR occurred_at <= received_at + INTERVAL '5' MINUTE)
)
SELECT
    event_id, event_type, occurred_at, CAST(occurred_at AS date) AS event_date,
    session_id, anonymous_id, customer_id, page_url, referrer, product_id, quantity,
    price, currency, search_query, order_id, received_at, user_agent, ip_hash
FROM valid
WHERE rn = 1
