# 0023. Lakehouse giai đoạn 2: dbt, data quality, Superset, giải mã protobuf, OpenLineage/Marquez, Feast, OpenMetadata, RBAC Polaris

- Trạng thái: Proposed
- Mở rộng: ADR 0013 (data lakehouse). Giữ nguyên bốn tầng bronze/ODS/silver/gold, Kafka → Spark → Iceberg ← Trino.

## Bối cảnh
- Silver/gold viết bằng PySpark (`silver.py`, `gold.py`) với kiểm tra chất lượng tự viết (`checks.py`): khó review,
  không có test khai báo, không có tài liệu cột, không biết dữ liệu nguồn ngừng đổ về.
- Payload outbox là protobuf `EventEnvelope` nằm nguyên trong bronze; chưa ai đọc được event nghiệp vụ bằng SQL.
- Đầu ra Flink stream-processor (`ecommerce.stream.*`: trending, tồn kho, rủi ro) chưa vào lakehouse.
- Chưa có BI, lineage, feature store cho ML, data catalog; mọi job dùng principal `root` của Polaris.

## Quyết định
- **dbt-trino sở hữu silver và gold** (`data/lakehouse/dbt`). Spark vẫn lo phần cần streaming/ghi Iceberg nặng
  (bronze, ODS MERGE, giải mã protobuf). Lý do: SQL thuần dễ review, test khai báo, docs và lineage cột có sẵn;
  Trino đã có để đọc nên không cần thêm engine.
  - Bảng trạng thái (`orders`, `payments`...) materialize `table` với `on_table_exists: replace` (CREATE OR REPLACE,
    vẫn time travel). Bảng event (`clickstream_events`, `order_events`) incremental MERGE theo `event_id`, chỉ quét
    lại cửa sổ `lookback_hours`.
  - 60 test: unique/not_null/relationships/accepted_values + test chung `expression_is_true`, `not_negative`,
    `recent_rows`. Test lỗi thì `dbt build` dừng, gold không bị ghi đè bằng dữ liệu sai.
  - **Source freshness** trên ods (`_ods_updated_at`), bronze (`ingested_at`), event đã giải mã (`kafka_ts`):
    cảnh báo sau 6 giờ, lỗi sau 48 giờ. Chạy song song, không chặn build.
  - Bỏ `silver.py`, `gold.py`, `checks.py`; `silver.outbox_events` (payload binary) được thay bằng `silver.domain_events`.
- **Giải mã protobuf bằng Spark** (`jobs/decode_events.py`): `from_protobuf` với descriptor set build từ `api/proto`
  bằng `buf build` ngay trong Dockerfile Spark (không commit file sinh). Trường `google.protobuf.Any` đổi sang JSON
  (`convert.any.fields.to.json`) nên một bảng chứa được mọi kiểu event; dbt tách cột theo `payload_type`.
  Dòng không giải mã được bị bỏ và đếm vào log.
  - Không dùng Schema Registry protobuf cho outbox vì event đã có envelope + `type`, và Debezium Outbox Router đẩy
    bytes nguyên vẹn; descriptor từ chính `api/proto` là nguồn sự thật (buf breaking đã kiểm tra tương thích).
- **Flink → lakehouse**: `lake-stream` thêm query `ecommerce\.stream\..+` → `bronze.stream_signals`; giải mã như outbox
  vào `silver.stream_signals`; dbt dựng `gold.product_trending_current`, `stock_cover_current`, `customer_risk_current`.
- **RBAC Polaris theo nguyên tắc tối thiểu**: `etl` (Spark, dbt) có `CATALOG_MANAGE_CONTENT`; `analyst` chỉ đọc
  namespace silver và gold. Trino có hai catalog: `lakehouse` (etl) và `lakehouse_ro` (analyst) cho Superset, Feast.
  `root` chỉ còn để quản trị (script init).
- **BI: Apache Superset 6** (profile `bi`) đọc `lakehouse_ro`. Dashboard khai báo trong code
  (`deploy/local/lakehouse/superset/bootstrap_dashboards.py` sinh bundle YAML rồi `superset import-dashboards`), UUID cố
  định nên chạy lại là cập nhật. So với Metabase: Superset mạnh hơn với Trino/Iceberg, phân quyền theo dataset.
- **Lineage: OpenLineage + Marquez** (profile `lineage`): listener `openlineage-spark` (bật qua `spark.extraListeners`),
  `dbt-ol` thay `dbt`, provider OpenLineage có sẵn trong Airflow. Tắt mặc định để không có log lỗi khi không chạy Marquez.
- **Feature store: Feast** (profile `ml`): offline store Trino (`lakehouse_ro`), online store Redis có sẵn (db 3).
  Feature view từ `gold.customer_features`; Airflow gọi `/materialize-incremental` sau mỗi lần dbt build.
- **Data catalog: OpenMetadata 2.0** (profile `catalog`, ~3 GB RAM): nạp schema từ Trino và mô tả/test/lineage từ
  artefact dbt bằng container `openmetadata-ingest` (không dùng Airflow riêng của OpenMetadata). Chọn OpenMetadata thay
  DataHub vì nhẹ hơn (không cần Kafka/Neo4j riêng), connector Trino và dbt có sẵn, UI có data quality.

## Hệ quả
- Thêm image phải build: Spark (thêm descriptor, jar protobuf và OpenLineage), Airflow (venv dbt), dbt, Superset,
  Feast, ingestion OpenMetadata. Profile mới đều tuỳ chọn; profile `lakehouse` mặc định không nặng hơn trước.
- Polaris bật `DROP_WITH_PURGE_ENABLED` vì Trino/dbt xoá bảng kèm purge.
- Spark và dbt đặt tên dataset OpenLineage khác nhau (namespace Polaris so với `trino://`), nên trong Marquez lineage
  Spark và dbt chưa nối thành một đồ thị; OpenMetadata nối được từ ods tới gold nhờ artefact dbt.
- Trên AWS: thay key MinIO bằng IAM role + credential vending của Polaris, mật khẩu principal lấy từ Secrets Manager,
  Superset/Marquez/OpenMetadata dùng RDS thay Postgres chung.
