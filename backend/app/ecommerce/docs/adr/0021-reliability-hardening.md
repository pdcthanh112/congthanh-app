# 0021. Gia cố độ tin cậy: saga checkout, consumer, outbox, idempotency, xác thực Go

- Trạng thái: Proposed
- Bổ sung cho 0003 (outbox), 0004 (saga Temporal), 0007/0018 (flash sale), 0010 (authz); không thay thế

## Bối cảnh
Review kiến trúc tìm ra các lỗ hổng làm mất event, bán lố hoặc trừ tiền mà đơn không xong:
- Saga commit kho rồi mới capture; capture hay confirm bị từ chối (không retry) nằm ngoài khối bù trừ, workflow fail
  với hàng đã xuất và tiền đã giữ. Hạn giữ hàng (15 phút) bằng đúng thời gian chờ thanh toán, nên hàng có thể tự nhả
  trước khi khách trả tiền xong.
- Consumer Go gọi `SetOffsets(nil)` khi lỗi: lượt poll sau đọc tiếp từ vị trí đã fetch, record lỗi bị bỏ qua và
  không có DLQ.
- `processed_events` của Go có khoá chỉ là `event_id`; Java/TS là `(event_id, consumer)`.
- Bảng `outbox`, `processed_events` không bao giờ được dọn.
- Idempotency-Key giữ `IN_PROGRESS` tới hết TTL 24h nếu pod chết giữa request.
- cart-service xoá giỏ khi nhận `OrderPlaced` (đơn còn PENDING), đơn bị huỷ thì khách mất giỏ.
- Flash sale nhả suất khi gửi Kafka lỗi, kể cả lỗi mơ hồ (event có thể đã lên broker) → bán lố.
- Service Go chạy không xác thực khi quên cấu hình JWT; mỗi service tự viết `requireAdmin`; route ghi của
  inventory-service không kiểm vai trò.

## Quyết định
1. **Saga**: authorize → capture → commit kho → confirm. Capture bị từ chối vẫn bù trừ được (refund lo cả void lẫn
   hoàn tiền). Commit kho retry lỗi tạm thời vô hạn, chỉ bù trừ khi inventory từ chối hẳn. Confirm bị từ chối sau
   commit thì hoàn tiền, huỷ đơn, ghi log lỗi cho vận hành. Thay đổi bọc `Workflow.getVersion("capture-before-commit")`.
   order-service gửi `expiry_time` = payment timeout + margin (mặc định 15 phút) khi giữ hàng.
2. **Consumer Go**: mỗi partition xử lý tuần tự, retry có backoff, quá số lần thì ghi `<topic>.dlq` (header `x-dlq-*`)
   rồi đi tiếp. Không ghi được DLQ thì tua đúng partition đó về record lỗi. Chỉ commit record đã xong.
3. **processed_events** khoá `(event_id, consumer)` ở mọi ngôn ngữ (migration goose mới cho service Go).
4. **Janitor** trong platform lib Go/Java/TS: xoá `outbox` sau 1 giờ, `processed_events` sau 14 ngày (gấp đôi
   retention topic). An toàn vì Debezium đọc WAL qua replication slot; connector outbox đặt
   `skipped.operations: d,t` để lệnh DELETE không thành event.
5. **Idempotency-Key**: thêm `locked_until` + `lock_token` (Liquibase `platform:004`). Hết lease (mặc định 60s) thì
   request gửi lại cùng payload tiếp quản; complete/release chỉ có tác dụng với token đang giữ.
6. **cart-service**: `OrderPlaced` chỉ ghi nhớ sản phẩm (`pending_order_clears`), `OrderConfirmed` mới xoá khỏi
   giỏ, `OrderCancelled` bỏ ghi nhớ. Không đổi proto.
7. **Flash sale**: `kafka.ErrNotDelivered` (broker từ chối, lỗi trước khi gửi) thì nhả suất; lỗi mơ hồ thì giữ claim
   và gửi lại ở nền tới gần hạn claim. checkout-service đã idempotent theo claim id.
8. **Xác thực Go fail-closed**: thiếu `JWT_JWKS_URL`/`JWT_SECRET` thì không khởi động, trừ `AUTH_DISABLED=true`.
   `auth.RequireRole` dùng chung; route ghi tồn kho/kho/flash sale của inventory-service cần `ADMIN` (saga gọi qua
   gRPC nội bộ).

## Hệ quả
- Workflow checkout đang chạy dở vẫn replay theo thứ tự cũ; workflow mới theo thứ tự mới.
- Confirm bị từ chối sau commit kho để lại hàng đã xuất cho đơn đã huỷ: cần người xử lý (log lỗi), hiếm vì order
  chỉ từ chối confirm khi trạng thái sai.
- Môi trường thiếu cấu hình JWT sẽ crash lúc khởi động thay vì chạy mở; local/test phải đặt `AUTH_DISABLED=true`.
- Event gửi lại từ flash sale có thể trùng; mọi consumer của `FlashSaleClaimed` phải idempotent theo claim id.
- fraud-service (Python) vẫn tự quản bảng outbox của nó, chưa có janitor.
