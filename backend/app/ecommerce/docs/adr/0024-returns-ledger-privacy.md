# 0024. Thêm returns-service, ledger-service, privacy-service; hoàn tiền một phần

- Trạng thái: Proposed

## Bối cảnh
Nền tảng phủ luồng mua hàng tới lúc giao nhưng thiếu ba mảng:
- **Đổi trả**: không ai sở hữu vòng đời trả hàng. payment-service chỉ hoàn toàn bộ (bù trừ saga), không hoàn một phần.
- **Sổ cái**: payment lưu giao dịch theo góc nhìn cổng thanh toán; không trả lời được "công ty đang nợ ai bao nhiêu".
  Gift card, loyalty, chargeback, tín dụng nội bộ (ADR 0016) về sau đều cần một sổ cái chung.
- **Quyền riêng tư**: thị trường Mỹ (ADR 0015) chịu CCPA/CPRA và luật các bang: tôn trọng Global Privacy Control,
  yêu cầu xem/xoá dữ liệu phải xử lý trong 45 ngày trên mọi service giữ dữ liệu cá nhân.

## Quyết định
**Contract (thêm trường, không phá tương thích):**
- `PaymentCaptured` (mới) phát khi thu tiền; `PaymentRefunded` thêm `refund_id`, `reason`, `provider`, `fully_refunded`
  (một payment có thể có nhiều lần hoàn, `amount` là số của lần đó).
- `OrderConfirmed` thêm `customer_id`, `total`.
- Event mới: `ecommerce.returns.v1.*`, `ecommerce.ledger.v1.*`, `ecommerce.privacy.v1.*`.

**payment-service:** hoàn một phần qua activity Temporal `Payment.refundPartial` (queue `payment`), idempotent theo
`refund_id` (bảng `payment_refunds`, key idempotency gửi Stripe/PayPal là `refund-<refundId>`); tổng đã hoàn không vượt
số đã thu (domain + CHECK ở DB). Vượt hoặc chưa capture: `ApplicationFailure` không retry `RefundRejected`.

**returns-service (Java, :5032):** RMA `REQUESTED → APPROVED → RECEIVED → REFUND_PENDING → REFUNDED`
(nhánh `REJECTED`, `CANCELLED`, `REFUND_FAILED`). Chính sách: hạn 30 ngày từ lúc giao, phí restocking theo % chỉ khi lỗi
không thuộc người bán, tự duyệt. Bản chiếu đơn dựng từ `OrderPlaced/Confirmed/Cancelled` và `ShipmentStatusChanged`
(DELIVERED, lấy `occurred_at` của envelope) nên không gọi đồng bộ order-service. Kho ghi kết quả kiểm từng dòng, service
tính số hoàn (không vượt phần còn lại của đơn) rồi chạy `ReturnRefundWorkflow` (queue `returns`, workflow id
`return-refund-<returnId>`) gọi payment; job 5 phút khởi chạy lại workflow cho yêu cầu kẹt `REFUND_PENDING`.

**ledger-service (Java, :5031):** sổ cái kép, `journal_entries`/`postings` chỉ ghi thêm (trigger chặn UPDATE/DELETE),
bút toán cân theo từng tiền tệ, sai thì đảo. Luật v1: capture Nợ `asset:psp_clearing:<cổng>` / Có `revenue:gross_sales`;
hoàn Nợ `contra_revenue:refunds` / Có `asset:psp_clearing:<cổng>`. Ngày hạch toán theo America/New_York. Idempotent theo
`source_event_id`. Event không hạch toán được vào `unposted_events`. Bút toán tay/đảo qua maker/checker (hai người khác nhau).
API chỉ cho role `FINANCE`/`ADMIN`.

**privacy-service (Go, :5033):** consent chỉ ghi thêm (mặc định opt-out cho chia sẻ/quảng cáo/phân tích, opt-in cho
marketing email/SMS), header `Sec-GPC: 1` tự ghi opt-out. Yêu cầu `ACCESS`/`DELETE` tạo một task cho mỗi processor trong
registry (`PRIVACY_PROCESSORS`), phát `ErasureRequested`/`AccessExportRequested`; processor trả lời bằng
`ErasureCompleted`/`AccessExportReady` trên `ecommerce.privacy.events`. Hạn 45 ngày, gia hạn một lần có lý do; job hằng
giờ phát lại yêu cầu cho processor chưa trả lời sau 7 ngày và log lỗi khi quá hạn. `OPT_OUT` ghi consent và xong ngay.

## Hệ quả
- Ba topic mới (+DLQ), ba connector Debezium, ba KafkaUser, ba role Postgres, ba route APISIX, ba AuthorizationPolicy.
- Mỗi service có dữ liệu cá nhân **phải** thêm consumer `ErasureRequested`/`AccessExportRequested` và trả lời; chưa
  service nào làm, nên yêu cầu xoá/xem sẽ ở `IN_PROGRESS` tới khi làm xong (nhân viên có thể xác nhận tay từng task).
- Ledger v1 ghi doanh thu gộp cả thuế và phí ship vì event thanh toán chưa tách; v2 tách `liability:sales_tax_payable:<bang>`
  khi order/tax phát phần thuế. Phí cổng (`PaymentFeeAssessed`) và đối soát làm ở reconciliation-service (đợt 2).
- Returns v1 hoàn theo đơn giá dòng (chưa phân bổ giảm giá của mã khuyến mãi theo dòng) nhưng luôn chặn ở phần còn lại
  của tổng đơn; chỉ hoàn về phương thức gốc (store credit chờ stored-value-service).
- Code sinh Python (`libs/python/ecommerce-proto`) cần chạy lại `make proto-python` cho event mới.
