# 0003. Transactional Outbox + Debezium, Protobuf + Schema Registry

- Trạng thái: Accepted

## Bối cảnh
Ghi DB rồi publish Kafka trong hai bước gây mất/nhân đôi event khi lỗi giữa chừng.

## Quyết định
- Use case ghi domain event vào bảng `outbox` **trong cùng transaction** với thay đổi dữ liệu (`libs/go/platform/outbox`).
- Debezium (Outbox Event Router) đọc WAL và đẩy sang topic `ecommerce.<aggregatetype>.events`, key = aggregate id (giữ thứ tự theo aggregate).
- Payload là `ecommerce.common.v1.EventEnvelope` (Protobuf) bọc event cụ thể trong `google.protobuf.Any`, mang `traceparent` để nối trace qua Kafka.
- Consumer idempotent nhờ bảng `processed_events` (ghi cùng transaction với xử lý). Lỗi lặp lại → DLQ `<topic>.dlq`.
- Schema đăng ký ở Apicurio với rule BACKWARD; CI chạy `buf breaking` để chặn thay đổi phá tương thích.

## Hệ quả
- Độ trễ event ~ vài trăm ms (WAL → Kafka). Chấp nhận được cho mọi luồng trừ flash sale claim (xem ADR 0007).
