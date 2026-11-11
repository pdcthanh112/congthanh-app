# 0012. Tách checkout, pricing, fraud và recommendation khỏi order-service/product-service

- Trạng thái: Accepted

## Bối cảnh
order-service vừa giữ đơn hàng, vừa báo giá, vừa chạy saga checkout; giá lấy thẳng từ product-service nên
không có giá theo nhóm khách, lịch giảm giá hay lịch sử giá (luật "giá gốc thấp nhất 30 ngày" khi khuyến mãi).
Không có bước kiểm tra gian lận trước khi tạo đơn (COD bom hàng, thử thẻ). Storefront không có gợi ý sản phẩm.

## Quyết định
- **pricing-service (Go)** là nguồn sự thật về giá bán: bảng giá (mặc định, theo nhóm khách lấy từ vai trò
  Keycloak), lịch giảm giá theo số tiền hoặc phần trăm (scheduler `FOR UPDATE SKIP LOCKED`), lịch sử giá và
  giá thấp nhất 30 ngày. gRPC `GetPrices`/`QuoteLines` cho checkout, REST cho storefront và quản trị.
  Phát `PriceChanged` qua outbox (`ecommerce.pricing.events`); seed giá từ `ecommerce.product.events`.
- **checkout-service (Java)** sở hữu phiên checkout (JSONB, khoá lạc quan, hết hạn sau 30 phút): báo giá bằng
  pricing + promotion + shipping + tax, kiểm tra lại giá ngay trước khi đặt (`PRICE_CHANGED`), gọi
  fraud-service, rồi tạo đơn qua gRPC `OrderService.PlaceOrder` với giá đã chốt (order id sinh trước nên gọi lại
  không tạo đơn trùng). Workflow Temporal `CheckoutWorkflow` chuyển sang checkout-service (queue
  `checkout-workflow`, cùng workflow id `checkout-<orderId>`); activity đơn hàng vẫn ở order-service (queue
  `checkout`), thanh toán ở payment-service, nên payment-service không đổi.
- **fraud-service (Python/FastAPI)** chấm điểm theo luật có giải thích (velocity theo khách/thiết bị/IP/thẻ,
  thử thẻ, COD bị từ chối nhận, tài khoản mới đơn lớn, lệch quốc gia...). Điểm ≥ 50 giữ lại chờ duyệt tay,
  ≥ 80 từ chối. Lịch sử khách dựng từ event đơn/thanh toán/vận chuyển. Kết quả duyệt tay phát
  `FraudReviewDecided` (`ecommerce.fraud.events`), checkout-service nghe để đặt tiếp hoặc huỷ phiên. Gọi đồng bộ
  với timeout 300 ms; fraud-service sập thì mặc định cho qua (`FRAUD_FAIL_OPEN`).
- **recommendation-service (Python/FastAPI + pgvector)**: mua kèm (đồng xuất hiện trong đơn, chuẩn hoá cosine
  để bớt thiên vị hàng bán chạy), sản phẩm tương tự (embedding tên + danh mục; mặc định hashing không cần
  model, có thể chuyển sang Ollama `bge-m3`), xu hướng (tín hiệu có suy giảm theo thời gian) và "cho riêng bạn"
  (trộn ba nguồn, giới hạn số món mỗi danh mục). Học từ event Kafka, không gọi đồng bộ service khác.
- **webhook-service** mở thêm sự kiện công khai: `payment.*`, `price.changed`, `inventory.low_stock`,
  `shipment.created`, `checkout.review_requested/decided` (bỏ tên luật chấm điểm trước khi gửi ra ngoài).

## Hệ quả
- Tổng đơn = tạm tính − giảm giá + phí giao + thuế, khớp đúng số khách thấy ở bước checkout.
- `POST /api/ecommerce/checkout` cũ của order-service vẫn chạy (không qua fraud/pricing) để client cũ không
  gãy; storefront-bff đã chuyển sang checkout-service. Gỡ endpoint cũ sau khi frontend chuyển hết.
- GraphQL `CheckoutResult.orderId/orderCode` thành nullable (đơn chờ duyệt chưa có mã); frontend cần xử lý
  `status = PENDING_REVIEW` và poll `checkoutSession(id)`.
- Thêm hai topic Kafka, hai connector Debezium, bốn KafkaUser và bốn role Postgres.
