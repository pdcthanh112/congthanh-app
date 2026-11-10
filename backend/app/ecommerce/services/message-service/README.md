# message-service (NestJS)

Chat giữa khách và người bán (hỏi về sản phẩm) hoặc bộ phận hỗ trợ (hỏi về đơn hàng).

| Cổng                   | Dùng cho                                                                                                                                                                                   |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| HTTP `5012` `/graphql` | `myConversations`, `conversation`, `messages(beforeSeq)`, `unreadMessageCount`; `contactSeller(productId)`, `contactSupport(orderId)`, `sendMessage`, `markConversationRead`, `sendTyping` |
| WS `5012` `/graphql`   | `subscription chatEvents`: tin mới + "đang gõ" của mọi hội thoại của người đang đăng nhập                                                                                                  |
| Kafka ra (outbox)      | `ecom.message.events`: `MessageSent` → notification-service báo push (gom 10 phút/hội thoại)                                                                                               |

- Người bán được xác định từ product-service (`GetProductsByIds`), không nhận `sellerId` từ client.
- Mỗi cặp khách–người bán một hội thoại; mỗi khách một hội thoại hỗ trợ (nhân viên SUPPORT/ADMIN cùng xem hàng chờ).
- `seq` tăng liên tục trong hội thoại (khoá hàng khi cấp) để phân trang và tính chưa đọc; `clientMessageId` chống gửi trùng khi client thử lại.
- Giới hạn 30 tin/phút/người (Valkey); nội dung bỏ ký tự điều khiển, tối đa 4000 ký tự, đính kèm phải là file từ file-service.
- Realtime chạy nhiều pod: publish qua Valkey kênh `chat:user:<id>` và `chat:support`.

Test: `npx vitest run services/message-service` (+ `TEST_DATABASE_URL`: dùng lại hội thoại, chưa đọc, chống trùng, quyền, hạn mức, gửi đồng thời).

Bản Spring cũ chỉ là khung copy từ catalog (document brand/category), không có logic chat; service này viết mới.
