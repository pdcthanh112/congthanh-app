# notification-service (NestJS)

Biến event miền thành thông báo đa kênh: **in-app** (realtime qua GraphQL subscription), **email** (SMTP),
**SMS** (webhook nhà cung cấp), **web push** (VAPID), **FCM** (app di động).

| Cổng                              | Dùng cho                                                                                                                                                       |
| --------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| HTTP `5011` `/graphql`            | `myNotifications`, `unreadNotificationCount`, `myNotificationPreferences`; đánh dấu đã đọc, đổi tuỳ chọn, đăng ký thiết bị; `sendNotification` (ADMIN/SUPPORT) |
| WS `5011` `/graphql` (graphql-ws) | `subscription notificationAdded`: router mở WebSocket tới đây, token trong `connectionParams.Authorization`                                                    |
| Kafka vào                         | `ecom.{order,payment,review,customer,product,inventory}.events`                                                                                                |

## Luồng

```
event Kafka ──► EventHandlers (1 transaction: processed_events + bản sao cục bộ + notifications + deliveries)
                 └─ sau commit: publish Valkey  ──► pod giữ WebSocket của người nhận ──► subscription
DeliveryWorker (mỗi 2s, FOR UPDATE SKIP LOCKED) ──► email / SMS / web push / FCM, lỗi tạm thời thử lại 30s→2h (5 lần)
```

- Không gọi đồng bộ sang service khác để biết gửi cho ai: `order_customers` (từ `OrderPlaced`), `product_watchers`
  (từ `WishlistItemAdded/Removed`), `product_states` (giá/tồn mới nhất) đều dựng từ event.
- Email/số điện thoại lấy từ Keycloak Admin API (service account `notification-service`, role `view-users`), cache 5 phút.
- Kênh thiếu cấu hình thì lượt gửi bị `SKIPPED` (thấy trong bảng `deliveries`), không làm hỏng luồng.

## Quy tắc tuỳ chọn

- Nhóm: ORDER, PAYMENT (giao dịch), REVIEW, WISHLIST, PROMOTION, SYSTEM, INVENTORY × 5 kênh.
- Nhóm giao dịch luôn có in-app + email (không tắt được). SMS mặc định chỉ bật cho nhóm giao dịch.
- Giờ yên tĩnh (theo múi giờ người dùng, qua đêm được): chặn SMS/push cho nhóm không giao dịch.
- Giảm giá chỉ báo khi giảm ≥ 5%; có hàng lại khi tồn từ 0 lên > 0.

## Cấu hình kênh

| Biến                                    | Ghi chú                                                                  |
| --------------------------------------- | ------------------------------------------------------------------------ |
| `SMTP_URL`, `MAIL_FROM`                 | local: Mailpit `smtp://localhost:1025` (xem thư ở http://localhost:8025) |
| `SMS_WEBHOOK_URL`, `SMS_API_KEY`        | trống = chỉ ghi log (che số điện thoại)                                  |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` | sinh bằng `npx web-push generate-vapid-keys`                             |
| `FCM_SERVICE_ACCOUNT_JSON`              | nội dung JSON service account Firebase (từ secret)                       |
| `KEYCLOAK_CLIENT_SECRET`                | secret của client `notification-service`                                 |

Test: `npx vitest run services/notification-service` (+ `TEST_DATABASE_URL` cho test tích hợp: chống trùng, thử lại,
SKIPPED, giảm giá/có hàng lại, tuỳ chọn, cảnh báo tồn kho).

So với bản Spring cũ: bỏ RabbitMQ/Mongo/MySQL, bỏ REST; template viết bằng code (tiếng Việt, escape HTML) thay cho bảng
`notification_template`; thêm hàng đợi gửi bền vững có thử lại thay vì gửi trực tiếp trong listener.
