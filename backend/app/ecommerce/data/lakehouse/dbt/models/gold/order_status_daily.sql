SELECT
    event_date,
    count_if(event_name = 'OrderPlaced') AS orders_placed,
    count_if(event_name = 'OrderConfirmed') AS orders_confirmed,
    count_if(event_name = 'OrderCancelled') AS orders_cancelled,
    count_if(event_name = 'PaymentAuthorized') AS payments_authorized,
    count_if(event_name = 'PaymentFailed') AS payments_failed,
    sum(CASE WHEN event_name = 'OrderPlaced' THEN amount END) AS placed_amount,
    {{ safe_ratio("count_if(event_name = 'OrderCancelled')", "count_if(event_name = 'OrderPlaced')") }} AS cancel_rate,
    current_timestamp AS computed_at
FROM {{ ref('order_events') }}
GROUP BY event_date
