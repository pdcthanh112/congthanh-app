# 0034. Gửi thông báo và email hàng loạt (broadcast) trong notification-service

- Trạng thái: Proposed

## Bối cảnh
Admin cần gửi email hoặc thông báo tới mọi người dùng (hoặc một nhóm role), ví dụ báo trước flash sale, có thể hẹn
giờ. notification-service trước đây chỉ gửi theo từng event miền hoặc cho một người (`sendNotification`):

- Không có thực thể chiến dịch: không hẹn giờ, không huỷ, không biết đã gửi tới đâu.
- Fan-out tới nhiều người chạy trong một transaction của Kafka handler; với hàng chục nghìn người nhận, transaction
  phình to, kẹt partition và timeout thì chạy lại cả khối.
- Hàng đợi `deliveries` chỉ sắp theo `next_attempt_at`: một đợt gửi lớn đẩy email đơn hàng ra sau hàng.
- Không có hạn mức theo nhà cung cấp (SMTP/SES, FCM, SMS); gửi dồn dập sẽ bị chặn hoặc tính phí vượt mức.
- `deliveries` không có UNIQUE `(notification_id, channel)`.

## Quyết định
- **Aggregate `Broadcast`** (bảng `broadcasts`): nội dung, nhóm (`PROMOTION` hoặc `SYSTEM`, không bao giờ là nhóm giao
  dịch), kênh, đối tượng (`ALL_USERS` hoặc `ROLE`), `scheduled_at`, trạng thái
  `SCHEDULED → EXPANDING → SENDING → COMPLETED`, huỷ được thành `CANCELLED` khi chưa xong. Ràng buộc đặt ở DB (CHECK
  nhóm, tiêu đề, kênh, cursor). Tạo qua GraphQL `createBroadcast` (role ADMIN) với `idempotencyKey`; UNIQUE
  `(created_by, idempotency_key)` nên bấm hai lần chỉ ra một chiến dịch.
- **Chia trang người nhận tách khỏi Kafka**: `BroadcastWorker` claim một chiến dịch đến hạn bằng `FOR UPDATE SKIP
  LOCKED` cộng lease 60 giây, lấy người nhận theo trang (`BROADCAST_PAGE_SIZE`, mặc định 500) từ Keycloak Admin API
  ngoài transaction. Mỗi trang là một transaction: khoá chiến dịch (dừng nếu đã huỷ hoặc pod khác đã đi tiếp), lọc theo
  tuỳ chọn người dùng (`channelsFor`), chèn thông báo + lượt gửi bằng một câu lệnh, dời `audience_cursor`. Chết giữa
  chừng thì pod khác tiếp tục từ cursor sau khi lease hết hạn. `dedup_key = bc:{broadcastId}:{userId}` nên chạy lại
  một trang không tạo trùng.
- **Ưu tiên trong hàng đợi**: thêm `deliveries.priority` (giao dịch 0, thường 5, hàng loạt 9) và `broadcast_id`.
  Worker claim theo `(channel, priority, next_attempt_at)` với index một phần `WHERE status = 'PENDING'`.
- **Hạn mức theo kênh**: port `SendRateLimiter`, hiện thực bằng cửa sổ cố định 1 giây trong Redis (script Lua nguyên
  tử, dùng chung mọi pod). Cấu hình `EMAIL_RATE_PER_SECOND`, `SMS_RATE_PER_SECOND`, `WEB_PUSH_RATE_PER_SECOND`,
  `FCM_RATE_PER_SECOND`; bỏ trống là không giới hạn. Mỗi vòng worker xin hạn mức rồi chỉ claim đúng số được cấp, trả
  lại phần không dùng. Còn việc thì chạy tiếp ngay, bị hạn mức thì đợi sang giây sau. Redis lỗi thì cho qua: hạn mức
  chỉ để tránh bị nhà cung cấp chặn, email giao dịch không được dừng vì Redis; nhà cung cấp trả lỗi thì vẫn có backoff.
- **Tiến độ**: `broadcast(id)` trả `recipients`, `audienceScanned` và số lượt gửi theo kênh × trạng thái (đếm trên
  `deliveries` theo `broadcast_id`, có index). Worker đánh dấu `COMPLETED` khi chiến dịch `SENDING` không còn lượt chờ.
- Thêm UNIQUE `(notification_id, channel)` cho `deliveries`. Email không giao dịch gắn header `List-Unsubscribe`.

## Hệ quả
- Đơn hàng và thanh toán không bị chậm vì chiến dịch; tốc độ gửi hàng loạt bằng hạn mức cấu hình, không phụ thuộc
  số pod.
- Keycloak phân trang theo `first/max`: người dùng thêm hoặc xoá trong lúc chia trang có thể làm lệch một vị trí; trùng
  thì `dedup_key` chặn, sót thì chấp nhận. Worker gửi vẫn tra Keycloak cho người không còn trong cache (10k người, 5
  phút). Về lâu dài nên dựng bản sao người nhận từ event đăng ký/cập nhật người dùng (proto `customer` chưa có event).
- Chưa làm: hoãn push trong giờ yên tĩnh tới cuối khung giờ (hiện vẫn bỏ kênh push như thông báo thường), giới hạn số
  thư tiếp thị mỗi người mỗi ngày, event `BroadcastCompleted` qua outbox, chuyển báo giảm giá/có hàng cho người theo
  dõi (`toWatchers`) sang cùng cơ chế chia trang, partition `notifications`/`deliveries` (ADR 0032).
- Cần reset hoặc migrate schema `notification_service` local: migration mới `0001_broadcasts.sql`.
