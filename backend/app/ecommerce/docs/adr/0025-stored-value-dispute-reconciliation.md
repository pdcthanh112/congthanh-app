# 0025. Thêm stored-value-service, dispute-service, reconciliation-service

- Trạng thái: Proposed

## Bối cảnh
Sau ADR 0024 đã có sổ cái và hoàn tiền một phần, nhưng còn ba lỗ hổng quanh dòng tiền:
- Hoàn hàng chỉ về được phương thức gốc; chưa có store credit (khách giữ tiền ở shop, giảm chi phí cổng).
- Chargeback không ai xử lý: Stripe rút tiền + thu phí, hạn nộp bằng chứng 7–21 ngày, quá hạn là thua.
  Visa/Mastercard phạt khi tỷ lệ chargeback vượt khoảng 0,9–1%.
- Không ai kiểm tiền thật cổng báo có khớp với những gì payment-service ghi nhận.

## Quyết định
**Contract:** `PaymentCaptured.provider_ref`, `PaymentRefunded.provider_ref` (mã PaymentIntent/PayPal order) để
ghép với dữ liệu cổng. Event mới `ecommerce.storedvalue.v1.*`, `ecommerce.dispute.v1.*`, `ecommerce.reconciliation.v1.*`.
Code sinh Python được tạo lại (gồm cả event của ADR 0024).

**stored-value-service (Java, :5034):** một tài khoản STORE_CREDIT mỗi (khách, tiền tệ). Bất biến 0 ≤ held ≤ balance
ở domain và CHECK DB. Cộng tiền idempotent theo `reference`; giữ chỗ/trừ/trả theo `holdId` (giữ chỗ tự trả sau 2 giờ).
Lịch sử `movements` chỉ ghi thêm. Activity Temporal `StoredValue.credit/hold/capture/release` (queue `stored-value`,
`libs/java/workflow-contracts`); lỗi nghiệp vụ là `StoredValueRejected` không retry. returns-service cho khách chọn
`refundMethod = STORE_CREDIT`: workflow hoàn tiền gọi `StoredValue.credit` thay vì payment. Tài khoản bị khoá vẫn nhận
tiền nhưng không tiêu được. Gift card dùng chung mô hình ở bước sau (cần thêm phát hành mã, chống dò mã, escheatment).

**dispute-service (Java, :5035):** nhận webhook `charge.dispute.*` của Stripe trên endpoint riêng (secret riêng, route
APISIX không cần token, service kiểm chữ ký), hoặc nhập tay (PayPal, sandbox). Dựng dữ kiện đơn từ event order/payment/
shipping/returns; `EvidenceAssembler` gom bằng chứng theo lý do và khuyến nghị FIGHT/ACCEPT/REVIEW (đã hoàn đủ → FIGHT;
không nhận hàng + có xác nhận giao → FIGHT; gian lận hoặc không nhận hàng mà không có bằng chứng giao → ACCEPT).
Nộp qua Stripe API (thiếu khoá thì chỉ ghi log). Không cho nộp quá hạn; job hằng giờ log ERROR cho tranh chấp còn mở
sắp tới hạn (3 ngày). Kết quả WON/LOST từ webhook; ACCEPTED khi mình không chống.

**reconciliation-service (Python, :5036):** sổ nội bộ dựng từ `PaymentCaptured/PaymentRefunded`. Giao dịch cổng lấy từ
Stripe Balance Transactions API (tự chạy 6h UTC cho ngày hôm trước) hoặc CSV chung (PayPal, BNPL). Thu tiền ghép 1-1 theo
`provider_ref`; hoàn tiền ghép theo tổng trên mỗi `provider_ref`. Chênh lệch: MISSING_INTERNAL, MISSING_PROVIDER,
AMOUNT_MISMATCH, CURRENCY_MISMATCH vào hàng chờ, xử lý phải có ghi chú. Chạy lại cùng ngày thay kết quả cũ (cổng bổ
sung giao dịch trễ), lần cũ giữ làm lịch sử.

**ledger-service, luật v2:** store credit cộng: Nợ `contra_revenue:refunds` (hoàn hàng) / `expense:goodwill` /
`expense:referral_rewards` / `expense:stored_value_adjustments`, Có `liability:stored_value:<kind>`; tiêu: Nợ liability,
Có `revenue:gross_sales`. Tranh chấp mở: Nợ `asset:disputed_funds:<cổng>` + `expense:dispute_fees`, Có
`asset:psp_clearing:<cổng>`; thắng: tiền về `psp_clearing`; thua/chấp nhận: `expense:chargeback_losses`.

## Hệ quả
- Ba topic mới (+DLQ), ba connector, hai KafkaUser mới (dispute, reconciliation) và ACL đọc thêm cho ledger, ba role
  Postgres, bốn route APISIX (một route webhook công khai), ba AuthorizationPolicy, Helm values, CI.
- checkout chưa dùng store credit để thanh toán; activity `hold/capture/release` đã sẵn để nối vào saga ở bước sau.
- fraud-service chưa đọc `DisputeClosed` làm nhãn; phí cổng từ đối soát chưa được hạch toán (cần event
  `PaymentFeeAssessed` hoặc ledger đọc `ReconciliationRunCompleted`).
- PayPal dispute chưa có webhook: nhập tay và xử lý trên trang PayPal.
