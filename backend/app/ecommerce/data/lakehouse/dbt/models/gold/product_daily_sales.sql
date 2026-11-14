{{ config(properties={"partitioning": "ARRAY['month(order_date)']"}) }}
SELECT
    l.placed_date AS order_date,
    l.product_id,
    coalesce(max(p.product_name), max(l.product_name)) AS product_name,
    l.currency,
    sum(l.quantity) AS units_sold,
    sum(l.line_amount) AS revenue,
    count(DISTINCT l.order_id) AS orders,
    current_timestamp AS computed_at
FROM {{ ref('order_lines') }} l
LEFT JOIN {{ ref('products') }} p ON p.product_id = l.product_id
WHERE l.is_confirmed
GROUP BY l.placed_date, l.product_id, l.currency
