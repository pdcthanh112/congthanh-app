SELECT
    id AS payment_id,
    order_id,
    customer_id,
    amount,
    upper(currency) AS currency,
    upper(method) AS method,
    upper(provider) AS provider,
    upper(status) AS status,
    failure_reason,
    created_at,
    updated_at,
    upper(status) IN ('AUTHORIZED', 'CAPTURED') AS is_successful
FROM {{ source('ods', 'payments') }}
