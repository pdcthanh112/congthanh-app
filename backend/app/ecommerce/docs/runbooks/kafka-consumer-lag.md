# Runbook: Kafka consumer lag tăng

- KEDA tự scale consumer theo lag (`keda.kafka.lagThreshold` trong values Helm). Kiểm tra `kubectl get scaledobject -n ecommerce`.
- Lag không giảm dù đã scale: xem log consumer, event lỗi lặp lại sẽ vào `<topic>.dlq`.
- Xử lý lại DLQ: sửa nguyên nhân, dùng `kcat` đẩy lại event từ DLQ về topic gốc (consumer idempotent nhờ `processed_events`).
