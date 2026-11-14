# 0026. Thêm loyalty-service, subscription-service, moderation-service

- Trạng thái: Proposed

## Bối cảnh
Đợt 3 của kế hoạch mở rộng: giữ chân khách (điểm thưởng, mua định kỳ) và một nơi kiểm duyệt nội dung dùng chung
thay cho việc review-service tự kiểm duyệt.

## Quyết định
**loyalty-service (Java, :5037):** điểm theo **lô** mỗi đơn. Tích floor(tổng đơn USD × % theo hạng) khi `OrderConfirmed`
(chỉ đơn USD), lô PENDING; thành AVAILABLE sau ngày giao + 30 ngày (khớp hạn trả hàng), không có ngày giao thì sau 45
ngày; hết hạn 12 tháng sau khi khả dụng. Trả hàng (`RefundIssued`) đảo điểm theo tỷ lệ tiền hoàn trên đúng lô của đơn;
huỷ đơn đảo hết. Đổi điểm giữ chỗ FIFO lô sắp hết hạn, qua activity Temporal `Loyalty.reserve/commit/release` (queue
`loyalty`) để saga checkout dùng như redeem/cancel của promotion. Hạng MEMBER/SILVER (≥ 500 USD)/GOLD (≥ 1.500 USD) theo
chi tiêu 12 tháng của đơn đã qua hạn trả hàng; job hằng giờ xác nhận lô, hết hạn lô, tính lại hạng. 1 điểm = 0,01 USD.

**subscription-service (Java, :5038):** gói 7–90 ngày, bỏ qua kỳ, tạm dừng (có hoặc không có ngày kết thúc), huỷ ngay
lập tức online (FTC click-to-cancel, California ARL). Nhắc khách 3 ngày trước kỳ giao (một lần mỗi kỳ, event cho
notification). Tạo đơn 2 ngày trước kỳ giao qua cổng `RenewalOrders` với khoá idempotency `id:cycle:attempt`; thất bại
thì PAST_DUE và thử lại sau 1, 2, 2, 2 ngày, hết lượt thì huỷ. **Tạo đơn tự động đang tắt** (`DisabledRenewalOrders`):
cần checkout mở API đặt đơn không có phiên tương tác và payment charge off-session bằng phương thức đã lưu; trong lúc
chờ, service chỉ gửi nhắc và không charge ai.

**moderation-service (Python, :5039):** `POST /internal/moderation/items` (chỉ nội bộ) cho review/message/product/supplier
gửi nội dung, trả quyết định đồng bộ: APPROVE, REDACT (che SĐT/email/link, chat luôn che thay vì chặn), REJECT (spam kèm
link), ESCALATE (hàng cấm, từ cấm, listing kéo khách ra ngoài). Nội dung luật không chắc (đánh giá/listing dài) được
Claude phân loại theo chính sách; LLM chỉ được làm quyết định chặt hơn, và REJECT của LLM thành ESCALATE cho người duyệt.
Chat không chờ LLM. Theo FTC, không bao giờ ẩn đánh giá chỉ vì tiêu cực (luật không xét cảm xúc; prompt cấm). Nội dung
giống hệt dùng lại quyết định cũ. Khiếu nại một lần, người duyệt khiếu nại phải khác người ra quyết định. Bị REJECT 3 lần
trong 30 ngày phát `UserContentStrikeIssued`. moderation-service giữ nội dung nên được thêm vào registry của privacy.

**ledger, luật v3:** điểm khả dụng: Nợ `expense:loyalty_program` / Có `liability:loyalty_points`; tiêu: Nợ liability /
Có `revenue:gross_sales`; đảo (điểm đã khả dụng) hoặc hết hạn: Nợ liability / Có `expense:loyalty_program`.

## Hệ quả
- Ba topic mới (+DLQ), ba connector, KafkaUser loyalty-service, ledger đọc thêm `ecommerce.loyalty.events`, ba role
  Postgres, ba route APISIX, ba AuthorizationPolicy (moderation mở cho review/message/product/supplier), Helm values, CI.
- Chưa nối: checkout dùng điểm và store credit trong saga; review-service/message-service gọi moderation và nghe
  `ModerationDecided` (review-service vẫn tự kiểm duyệt tới lúc đó); tạo đơn gia hạn.
