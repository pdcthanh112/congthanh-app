{{ config(
    materialized='incremental',
    incremental_strategy='merge',
    unique_key='event_id',
    properties={"partitioning": "ARRAY['day(occurred_at)']"}
) }}

SELECT
    event_id,
    regexp_extract(event_type, '[^.]+$') AS event_name,
    source,
    json_extract_scalar(payload_json, '$.order_id') AS order_id,
    json_extract_scalar(payload_json, '$.customer_id') AS customer_id,
    json_extract_scalar(payload_json, '$.payment_id') AS payment_id,
    CASE payload_type
        WHEN 'ecommerce.order.v1.OrderPlaced' THEN {{ money('payload_json', '$.total') }}
        WHEN 'ecommerce.payment.v1.PaymentAuthorized' THEN {{ money('payload_json', '$.amount') }}
        WHEN 'ecommerce.payment.v1.PaymentRefunded' THEN {{ money('payload_json', '$.amount') }}
    END AS amount,
    coalesce(json_extract_scalar(payload_json, '$.total.currency_code'),
             json_extract_scalar(payload_json, '$.amount.currency_code')) AS currency,
    json_array_length(json_extract(payload_json, '$.lines')) AS line_count,
    json_extract_scalar(payload_json, '$.reason') AS reason,
    occurred_at,
    event_date,
    traceparent
FROM {{ source('decoded', 'domain_events') }}
WHERE payload_type IN (
        'ecommerce.order.v1.OrderPlaced', 'ecommerce.order.v1.OrderConfirmed', 'ecommerce.order.v1.OrderCancelled',
        'ecommerce.payment.v1.PaymentAuthorized', 'ecommerce.payment.v1.PaymentFailed',
        'ecommerce.payment.v1.PaymentRefunded')
  AND {{ lookback('kafka_ts') }}
