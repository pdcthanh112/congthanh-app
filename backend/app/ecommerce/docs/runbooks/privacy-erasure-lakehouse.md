# Xoá dữ liệu của khách trong lakehouse (task "lakehouse" của privacy-service)

Áp dụng khi một yêu cầu DELETE (CCPA/CPRA) còn chờ task `lakehouse`. Hạn chót 45 ngày tính từ lúc mở yêu cầu
(ADR 0024, 0027). Nên làm sau khi các service khác đã xoá xong, để CDC không chép dữ liệu cũ vào lại.

## 1. Lấy subject id

`GET /api/ecommerce/privacy/admin/requests?status=IN_PROGRESS` (role PRIVACY hoặc ADMIN): `subjectId` là Keycloak sub,
cũng là `customer_id` trong lakehouse.

## 2. Xoá ở các bảng nguồn (Trino, catalog `lakehouse`, tài khoản có quyền ghi)

Bảng bronze/ODS là bản chép nguyên từ Kafka và CDC; silver/gold dựng lại từ đó.

```sql
-- Clickstream thô (JSON từ trình duyệt)
DELETE FROM lakehouse.bronze.clickstream_events
WHERE json_extract_scalar(value, '$.customer_id') = :subject;

-- CDC thô (JSON Debezium; dòng của đơn và thanh toán mang customer_id trong 'after'/'before')
DELETE FROM lakehouse.bronze.cdc_changes
WHERE source_table IN ('order_service.orders', 'payment_service.payments')
  AND (json_extract_scalar(value, '$.after.customer_id') = :subject
       OR json_extract_scalar(value, '$.before.customer_id') = :subject);

-- ODS (bản chiếu CDC)
DELETE FROM lakehouse.ods.orders WHERE customer_id = :subject;
DELETE FROM lakehouse.ods.payments WHERE customer_id = :subject;

-- Silver dạng incremental (không tự dựng lại từ đầu)
DELETE FROM lakehouse.silver.clickstream_events WHERE customer_id = :subject;
```

`lakehouse.bronze.outbox_events` và `lakehouse.silver.order_events` chứa event protobuf; `customer_id` nằm trong
payload đã giải mã của silver:

```sql
DELETE FROM lakehouse.silver.order_events WHERE customer_id = :subject;
```

Bronze `outbox_events` giữ binary nguyên bản: xoá theo các order id của khách, lấy từ `ods.orders` TRƯỚC khi xoá ODS ở
trên (`SELECT id FROM lakehouse.ods.orders WHERE customer_id = :subject`), rồi
`DELETE FROM lakehouse.bronze.outbox_events WHERE key IN (<các order id>)`.

## 3. Dựng lại silver/gold dạng table

```bash
make lakehouse-batch
```

`silver.orders`, `silver.payments`, `silver.order_lines`, `gold.customer_features`, `gold.customer_risk_current`... là
model `table` nên chạy lại là sạch dữ liệu của khách. Feast (Redis) đọc lại từ gold ở lần materialize kế tiếp; muốn
ngay thì xoá key `customer_features:<subject>` trong Redis online store.

## 4. Xoá hẳn khỏi snapshot cũ

Iceberg giữ snapshot để time travel: nếu không expire, dữ liệu vẫn đọc được. Với mỗi bảng đã xoá ở bước 2:

```sql
ALTER TABLE lakehouse.bronze.clickstream_events EXECUTE expire_snapshots(retention_threshold => '0d');
ALTER TABLE lakehouse.bronze.clickstream_events EXECUTE remove_orphan_files(retention_threshold => '0d');
```

## 5. Xác nhận task

`POST /api/ecommerce/privacy/admin/requests/{id}/tasks/lakehouse/complete` với `{"retained": false}`.
Nếu phải giữ số liệu tổng hợp đã ẩn danh (ví dụ `gold.daily_sales`), gửi `{"retained": true, "reason": "..."}`.
