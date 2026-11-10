# review-service (NestJS)

Đánh giá sản phẩm có xác minh đã mua, điểm tổng hợp, phản hồi của seller, kiểm duyệt.

| Cổng                   | Dùng cho                                                                                                                                                              |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| HTTP `5010` `/graphql` | Subgraph: góp `Product.ratingSummary`, `Product.reviews(...)`; `myPendingReviews`, `myReviews`, `adminReviews`; mutation viết/sửa/xoá, "hữu ích", trả lời, kiểm duyệt |
| Kafka vào              | `ecom.order.events`: `OrderPlaced` → `OrderConfirmed`/`OrderCancelled` dựng bảng "đã mua" (idempotent qua `processed_events`)                                         |
| Kafka ra (outbox)      | `ecom.review.events`: `ReviewSubmitted`, `ReviewModerated`, `ReviewReplied`, `ProductRatingChanged`                                                                   |

Quy tắc:

- Chỉ đánh giá được sản phẩm trong đơn **đã thanh toán**; mỗi (khách, đơn, sản phẩm) một đánh giá. Huỷ đơn thì không đánh giá được nữa.
- Sửa trong 30 ngày; đánh giá bị ẩn không sửa được. Ẩn phải ghi lý do; đánh giá ẩn không tính vào điểm.
- Ảnh/video phải là URL do file-service cấp (`MEDIA_URL_PREFIX`), tối đa 6.
- Seller chỉ trả lời đánh giá sản phẩm của mình (hỏi product-service qua gRPC `GetProductsByIds`), một lần.
- Điểm tổng hợp (`rating_summaries`) cập nhật trong cùng transaction, khoá hàng theo sản phẩm nên đánh giá đồng thời không mất điểm.
- Người khác chỉ thấy tên tác giả đã che (`N***n`).

Test: `npx vitest run services/review-service` (+ `TEST_DATABASE_URL` cho test tích hợp, gồm cả kịch bản đồng thời).

So với bản Spring cũ: bỏ REST và các enum không dùng (cart/order/payment), `order_id` giờ được kiểm tra thật thay vì tin client.
