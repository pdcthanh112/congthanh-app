WITH paid AS (
    SELECT order_id, sum(CASE WHEN is_successful THEN amount ELSE 0 END) AS paid_amount
    FROM {{ ref('payments') }}
    GROUP BY order_id
)
SELECT
    o.placed_date AS order_date,
    o.currency,
    count(*) AS orders_placed,
    count_if(o.status = 'CONFIRMED') AS orders_confirmed,
    count_if(o.status = 'CANCELLED') AS orders_cancelled,
    sum(CASE WHEN o.is_confirmed THEN o.subtotal ELSE 0 END) AS gross_revenue,
    sum(CASE WHEN o.is_confirmed THEN o.discount ELSE 0 END) AS discount,
    sum(CASE WHEN o.is_confirmed THEN o.total ELSE 0 END) AS net_revenue,
    round(sum(CASE WHEN o.is_confirmed THEN o.total END) / nullif(count_if(o.is_confirmed), 0), 2) AS avg_order_value,
    coalesce(sum(p.paid_amount), 0) AS paid_amount,
    count(DISTINCT o.customer_id) AS unique_customers,
    current_timestamp AS computed_at
FROM {{ ref('orders') }} o
LEFT JOIN paid p ON p.order_id = o.order_id
GROUP BY o.placed_date, o.currency
