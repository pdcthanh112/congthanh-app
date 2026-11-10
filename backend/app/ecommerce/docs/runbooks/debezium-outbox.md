# Runbook: Debezium/outbox ngừng đẩy event

1. `kubectl get kafkaconnector -n messaging` → connector nào FAILED. `kubectl describe` xem lỗi.
2. Replication slot phình (WAL không được dọn) là nguy hiểm cho DB: `SELECT slot_name, active, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) FROM pg_replication_slots;`
3. Restart task: annotate `strimzi.io/restart-task=0` lên KafkaConnector.
4. Bảng outbox có thể dọn định kỳ các dòng > 7 ngày (Debezium chỉ đọc WAL, không cần dữ liệu cũ trong bảng).
