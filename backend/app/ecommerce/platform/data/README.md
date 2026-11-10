# Data platform

| Kho | Dùng cho | Triển khai |
|---|---|---|
| PostgreSQL 18 + pgvector | dữ liệu giao dịch của mọi service (schema riêng), embedding sản phẩm | RDS (AWS) / CloudNativePG (`postgres-cnpg.yaml`) |
| Valkey | cache, rate limit gateway, flash sale (Lua) | ElastiCache / `valkey.yaml` |
| Kafka | event giữa service (outbox + Debezium) | MSK / Strimzi (`../messaging`) |
| ClickHouse | phân tích, dashboard kinh doanh | `clickhouse.yaml` |
| ScyllaDB | lịch sử chat message-service | `scylla.yaml` |
| Iceberg trên S3 | data lake, lưu toàn bộ event để replay/ML | Kafka Connect Iceberg sink (`../messaging/debezium/connectors/iceberg-sink.yaml`) |
| OpenSearch | full-text search (search-service) | OpenSearch Service (`infra/terraform/modules/opensearch`) |
