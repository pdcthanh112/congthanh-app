WITH s AS (
    SELECT
        event_date,
        session_id,
        bool_or(event_type = 'product_view') AS viewed,
        bool_or(event_type = 'add_to_cart') AS carted,
        bool_or(event_type = 'begin_checkout') AS checked_out,
        bool_or(event_type = 'purchase') AS purchased
    FROM {{ ref('clickstream_events') }}
    GROUP BY event_date, session_id
)
SELECT
    event_date,
    count(*) AS sessions,
    count_if(viewed) AS sessions_product_view,
    count_if(carted) AS sessions_add_to_cart,
    count_if(checked_out) AS sessions_begin_checkout,
    count_if(purchased) AS sessions_purchase,
    {{ safe_ratio('count_if(carted)', 'count_if(viewed)') }} AS view_to_cart_rate,
    {{ safe_ratio('count_if(purchased)', 'count_if(carted)') }} AS cart_to_purchase_rate,
    {{ safe_ratio('count_if(purchased)', 'count(*)') }} AS conversion_rate,
    current_timestamp AS computed_at
FROM s
GROUP BY event_date
