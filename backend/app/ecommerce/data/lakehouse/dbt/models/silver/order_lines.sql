{{ config(properties={"partitioning": "ARRAY['month(placed_at)']"}) }}

SELECT
    l.id AS order_line_id,
    l.order_id,
    l.product_id,
    l.sku,
    l.name AS product_name,
    l.quantity,
    l.unit_price,
    l.quantity * l.unit_price AS line_amount,
    o.customer_id,
    o.status,
    o.currency,
    o.placed_at,
    o.placed_date,
    o.is_confirmed
FROM {{ source('ods', 'order_lines') }} l
JOIN {{ ref('orders') }} o ON o.order_id = l.order_id
