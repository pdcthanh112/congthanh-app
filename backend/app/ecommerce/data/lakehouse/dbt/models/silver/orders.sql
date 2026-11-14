{{ config(properties={"partitioning": "ARRAY['month(placed_at)']"}) }}
SELECT
    id AS order_id,
    order_code,
    customer_id,
    upper(status) AS status,
    upper(currency) AS currency,
    subtotal,
    discount,
    total,
    promotion_code,
    upper(payment_method) AS payment_method,
    {{ initcap('ship_city') }} AS ship_city,
    upper(trim(ship_country)) AS ship_country,
    placed_at,
    updated_at,
    CAST(placed_at AS date) AS placed_date,
    upper(status) = 'CONFIRMED' AS is_confirmed
FROM {{ source('ods', 'orders') }}
WHERE id IS NOT NULL AND placed_at IS NOT NULL
