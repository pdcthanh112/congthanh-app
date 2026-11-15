# 0027. Nối các service mới vào luồng hiện có

- Trạng thái: Proposed

## Bối cảnh
ADR 0024–0026 thêm 9 service nhưng nhiều đường nối còn để ngỏ: checkout chưa trả được bằng store credit/điểm,
gia hạn subscription chưa tạo đơn, review/message chưa gọi moderation, chưa service nào xử lý yêu cầu xoá dữ liệu,
fraud chưa dùng kết quả chargeback, phí cổng chưa vào sổ cái.

## Quyết định
**Checkout trả bằng store credit và điểm thưởng.** `PlaceOrderRequest` thêm `store_credit`, `loyalty_points`; order lưu
vào đơn (cột `store_credit`, `loyalty_points`) và chuyển vào `CheckoutInput` (amount vẫn là TỔNG đơn). Saga (sau
`Workflow.getVersion("store-credit-and-loyalty")`): giữ điểm (`Loyalty.reserve`, redemption id = order id) → giữ store
credit (`StoredValue.hold`) → authorize phần còn lại; đủ trả cả đơn thì bỏ qua cổng thanh toán. Bù trừ trả lại cả hai;
sau commit kho thì trừ hẳn (`capture`/`commit`). Giá trị điểm do loyalty-service trả về lúc giữ, checkout không tự tính.

**Gia hạn subscription.** checkout-service mở `POST /internal/checkout/subscription-orders` (chỉ trong mesh, Istio chỉ mở
cho subscription-service): phiên checkout id suy ra từ khoá idempotency `subscription:kỳ:lượt`, địa chỉ lấy từ
customer-service (gRPC), chọn cách giao rẻ nhất, mã khuyến mãi Subscribe & Save tuỳ chọn, không chấm fraud lại.
payment-service nhận phương thức đã lưu dạng `cus_...:pm_...` và charge off-session; cần 3-D Secure thì coi là bị từ
chối. subscription-service nghe `OrderCancelled` của đơn gia hạn để quay lại kỳ đó và vào dunning. Bật bằng
`SUBSCRIPTIONS_RENEWALS_ENABLED` theo từng môi trường.

**Kiểm duyệt.** review-service và message-service gọi moderation-service đồng bộ (`ModerationClient` trong
`@ecommerce/platform`); lỗi thì giữ hành vi cũ (đăng/gửi) và ghi log. Review: REDACT thay nội dung, REJECT/ESCALATE ẩn
kèm lý do; nghe `ModerationDecided` của người duyệt. Chat chỉ che, không chặn.

**Xoá dữ liệu (privacy).** Mỗi processor nghe `ErasureRequested`, xoá/ẩn danh trong transaction và trả
`ErasureCompleted` qua outbox cùng transaction. Helper dùng chung: `libs/ts/platform/src/privacy`,
`libs/go/platform/privacy`; Java/Python viết trong từng service. Định danh thay thế chung công thức
`erased-` + 16 hex đầu của SHA-256. Phần giữ theo luật (đơn hàng, trả hàng: chứng từ thuế; fraud: ngoại lệ chống gian
lận) được ẩn danh và báo kèm lý do. privacy-service tự xoá tài khoản Keycloak ở bước cuối (client `privacy-service`,
quyền manage-users). chatbot-service ra khỏi registry (lịch sử chỉ ở Redis 24 giờ). Lakehouse làm tay theo
`docs/runbooks/privacy-erasure-lakehouse.md`.

**Khác.** fraud-service đọc `DisputeClosed` (luật PRIOR_FRAUD_CHARGEBACK, +60). ledger hạch toán phí cổng từ
`ReconciliationRunCompleted`; chạy lại cùng ngày chỉ ghi phần chênh. Compose local tự đăng ký connector outbox của
order, product, payment, promotion.

## Hệ quả
- order-service, customer-service, message-service có thêm consumer Kafka; recommendation-service có outbox + connector.
- Yêu cầu xem dữ liệu (ACCESS) chưa được processor nào xử lý: cần chọn bucket lưu file xuất trước.
- Frontend cần lưu thẻ bằng Stripe SetupIntent và gửi `cus_...:pm_...` khi đăng ký subscription.
