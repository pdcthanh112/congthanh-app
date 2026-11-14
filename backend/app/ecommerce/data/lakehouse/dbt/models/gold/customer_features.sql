WITH orders AS (
    SELECT
        customer_id,
        currency,
        min(placed_at) AS first_order_at,
        max(placed_at) AS last_order_at,
        date_diff('day', max(placed_date), current_date) AS recency_days,
        count(*) AS frequency,
        sum(total) AS monetary,
        round(avg(total), 2) AS avg_order_value
    FROM {{ ref('orders') }}
    WHERE is_confirmed
    GROUP BY customer_id, currency
), behaviour AS (
    SELECT
        customer_id,
        count_if(event_type = 'product_view') AS product_views_30d,
        count_if(event_type = 'add_to_cart') AS add_to_cart_30d,
        count_if(event_type = 'search') AS searches_30d,
        count(DISTINCT session_id) AS sessions_30d
    FROM {{ ref('clickstream_events') }}
    WHERE customer_id IS NOT NULL AND event_date >= current_date - INTERVAL '30' DAY
    GROUP BY customer_id
), scored AS (
    SELECT
        o.*,
        ntile(5) OVER (PARTITION BY currency ORDER BY recency_days DESC) AS r_score,
        ntile(5) OVER (PARTITION BY currency ORDER BY frequency) AS f_score,
        ntile(5) OVER (PARTITION BY currency ORDER BY monetary) AS m_score
    FROM orders o
)
SELECT
    s.*,
    CASE
        WHEN r_score >= 4 AND f_score >= 4 THEN 'champion'
        WHEN f_score >= 4 THEN 'loyal'
        WHEN r_score <= 2 AND f_score >= 3 THEN 'at_risk'
        WHEN frequency = 1 AND r_score >= 4 THEN 'new'
        WHEN r_score <= 2 THEN 'hibernating'
        ELSE 'regular'
    END AS segment,
    coalesce(b.product_views_30d, 0) AS product_views_30d,
    coalesce(b.add_to_cart_30d, 0) AS add_to_cart_30d,
    coalesce(b.searches_30d, 0) AS searches_30d,
    coalesce(b.sessions_30d, 0) AS sessions_30d,
    coalesce(r.payment_failures_1h, 0) AS payment_failures_1h,
    coalesce(r.cancellations_24h, 0) AS cancellations_24h,
    current_timestamp AS computed_at
FROM scored s
LEFT JOIN behaviour b ON b.customer_id = s.customer_id
LEFT JOIN {{ ref('customer_risk_current') }} r ON r.customer_id = s.customer_id
