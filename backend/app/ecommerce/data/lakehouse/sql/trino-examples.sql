SHOW SCHEMAS FROM lakehouse;                         -- bronze, ods, silver, gold
SHOW TABLES FROM lakehouse.gold;

-- Doanh thu 7 ngày gần nhất
SELECT order_date, currency, orders_confirmed, net_revenue, avg_order_value
FROM lakehouse.gold.daily_sales
WHERE order_date >= current_date - INTERVAL '7' DAY
ORDER BY order_date DESC;

-- Top 10 sản phẩm theo doanh thu
SELECT product_id, product_name, sum(units_sold) AS units, sum(revenue) AS revenue
FROM lakehouse.gold.product_daily_sales
GROUP BY 1, 2 ORDER BY revenue DESC LIMIT 10;

-- Phễu chuyển đổi
SELECT * FROM lakehouse.gold.funnel_daily ORDER BY event_date DESC;

-- Phân khúc khách hàng (RFM)
SELECT segment, count(*) AS customers, round(avg(monetary), 0) AS avg_monetary
FROM lakehouse.gold.customer_features GROUP BY 1 ORDER BY customers DESC;

-- ODS: trạng thái đơn hàng hiện tại (trễ vài chục giây so với Postgres)
SELECT status, count(*) FROM lakehouse.ods.orders GROUP BY 1;

-- Bronze: lịch sử mọi thay đổi của một đơn (ODS chỉ giữ bản mới nhất)
SELECT kafka_ts, json_extract_scalar(value, '$.status') AS status, json_extract_scalar(value, '$.__op') AS op
FROM lakehouse.bronze.cdc_changes
WHERE source_table = 'order_service.orders' AND json_extract_scalar(key, '$.id') = 'o-1'
ORDER BY kafka_ts;

-- Time travel: danh sách snapshot và đọc bảng tại một snapshot cũ
SELECT snapshot_id, committed_at, operation, summary['added-records'] AS added
FROM lakehouse.ods."orders$snapshots" ORDER BY committed_at DESC LIMIT 5;
-- SELECT count(*) FROM lakehouse.ods.orders FOR VERSION AS OF <snapshot_id>;
-- SELECT count(*) FROM lakehouse.ods.orders FOR TIMESTAMP AS OF TIMESTAMP '2026-11-10 10:00:00 UTC';

-- File nhỏ do streaming (job maintenance sẽ gộp lại)
SELECT count(*) AS files, round(avg(file_size_in_bytes) / 1024) AS avg_kb FROM lakehouse.bronze."clickstream_events$files";
