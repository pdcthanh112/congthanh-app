# 0016. Pay later (trả góp theo tháng): BNPL qua đối tác trước, chừa cổng cho tín dụng nội bộ

- Trạng thái: Proposed
- Liên quan: 0004 (saga Temporal), 0012 (fraud), 0015 (Stripe, PayPal, USD)

## Bối cảnh
Khách muốn mua trước, trả sau theo kỳ (kiểu Shopee PayLater). Thị trường chính là Mỹ (ADR 0015). Ở Mỹ, tự cấp
tín dụng trả góp cho người tiêu dùng là hoạt động cho vay: cần giấy phép cho vay/tín dụng tiêu dùng ở hầu hết các bang
(hoặc hợp tác với một ngân hàng phát hành), công bố điều khoản theo TILA/Regulation Z (APR, finance charge, lịch trả),
ECOA/Reg B (thông báo từ chối, không phân biệt đối xử), FCRA khi kéo điểm tín dụng, quy định thu nợ (FDCPA, luật
bang), và quy tắc quảng cáo của Reg Z: chỉ cần ghi "chỉ $25/tháng" là đã phải kèm APR và điều khoản. Ngoài ra phải
có vốn cho vay và tự gánh nợ xấu. Các đối tác BNPL (Affirm, Klarna, Afterpay) và PayPal Pay Later đã lo hết phần này
và trả đủ tiền cho shop ngay khi capture, đổi lại một khoản phí mỗi đơn.

Hệ thống hiện có: payment-service (Java, hexagonal) với port `PaymentProvider` (authorize → capture → void/refund),
`StripeProvider` (PaymentIntent capture thủ công, Payment Element gửi ConfirmationToken) và `PayPalProvider`
(Orders API v2, intent AUTHORIZE). Saga checkout authorize khi đặt đơn và capture sau khi kho commit.

## Quyết định
Đã chọn: **ra mắt bằng đối tác, thiết kế sẵn chỗ cắm tín dụng nội bộ sau** (không tự cho vay ở giai đoạn này).

### 1. Phương thức mới
| Method (enum) | Cổng | Kiểu trả | Ghi chú |
|---|---|---|---|
| `AFFIRM` | Stripe (`affirm`) | trả góp theo tháng, có thể 0% APR hoặc có lãi, do Affirm duyệt | hợp với đơn giá trị cao |
| `KLARNA` | Stripe (`klarna`) | Pay in 4 (2 tuần/kỳ) hoặc theo tháng tuỳ Klarna duyệt | |
| `AFTERPAY` | Stripe (`afterpay_clearpay`) | Pay in 4, không lãi | đơn nhỏ, cần địa chỉ giao hàng |
| `PAYPAL` (giữ nguyên) | PayPal | Pay in 4 / Pay Monthly là nguồn tiền bên trong luồng PayPal | không thêm enum |

- Thêm cùng một bộ giá trị vào enum ở payment-service, order-service, storefront-bff (`schema.graphql`, `types.ts`,
  `storefront.service.ts`) và chú thích `AuthorizePaymentRequest`, như ADR 0015 đã làm.
- `PaymentMethod` có thêm `category()`: `CARD`, `WALLET`, `PAYPAL`, `INSTALLMENT`. Code khác (fraud, báo cáo, lakehouse)
  hỏi category thay vì liệt kê từng partner.
- PayPal Pay Later không cần đổi backend: frontend bật `enable-funding=paylater` trong PayPal JS SDK, đơn vẫn là
  Orders API intent AUTHORIZE như hiện tại. Phí PayPal Pay Later bằng phí PayPal thường.

### 2. Stripe: BNPL là phương thức redirect, dùng lại luồng hiện có
- Payment Element tự hiện Affirm/Klarna/Afterpay khi bật trong Stripe Dashboard và PaymentIntent cho phép redirect.
  Với method thuộc `INSTALLMENT`, `returnUrl` là **bắt buộc** (thiếu thì 400 ở BFF, Declined ở provider), vì hiện
  `returnUrl == null` sẽ đặt `allow_redirects=never` và Stripe sẽ không cho BNPL.
- `StripeProvider.methods()` thêm 3 method. Khi nhận `ctoken_...`, provider đọc `payment_method_preview.type` của
  ConfirmationToken và từ chối nếu không khớp method khách chọn (tránh client gửi `CARD` nhưng trả bằng Klarna, làm
  sai fraud và báo cáo).
- Địa chỉ giao hàng: Afterpay bắt buộc, Affirm/Klarna duyệt tốt hơn. Frontend truyền `shipping` khi tạo
  ConfirmationToken (`stripe.createConfirmationToken({ elements, params: { shipping } })`), Stripe tự gắn vào
  PaymentIntent, nên contract saga không phải thêm trường. Provider từ chối AFTERPAY nếu token không có shipping.
- Luồng: authorize trả `ActionRequired(redirectUrl)` → khách duyệt ở trang đối tác → quay về `returnUrl` →
  webhook `payment_intent.amount_capturable_updated` (đã xử lý) → `AUTHORIZED` → saga capture như thẻ.
  Khách huỷ hoặc bị từ chối → `payment_intent.payment_failed` → `DECLINED`, frontend gợi ý chọn phương thức khác.
- Capture thủ công được cả 3 partner hỗ trợ nhưng authorization hết hạn sớm hơn thẻ (Afterpay ngắn nhất, khoảng
  13 ngày). Saga capture ngay sau khi kho commit nên không ảnh hưởng; cột `authorization_expires_at`
  (= lúc authorize + `authorization-ttl` cấu hình) để theo dõi đơn treo. Job cảnh báo dựa trên cột này chưa làm.
- Refund toàn phần như cũ. Hoàn một phần (trả một món) là việc chung của payment-service, ngoài phạm vi ADR này;
  khi làm thì cả 3 partner đều nhận refund một phần và tự điều chỉnh lịch trả của khách.

### 3. Hiển thị "trả góp" ở trang sản phẩm, giỏ, checkout (tuân thủ quảng cáo Reg Z)
- Backend **không** tự tính hay hiển thị số tiền mỗi kỳ, APR hay "0% lãi". Frontend dùng **Stripe Payment Method
  Messaging Element** (Affirm/Klarna/Afterpay) và **PayPal Pay Later messages**: đối tác lo nội dung và điều khoản
  bắt buộc.
- payment-service thêm endpoint đọc công khai
  `GET /api/ecommerce/payment/installment-options?amount=129.99&currency=USD` trả danh sách partner đang bật và
  khoảng giá cho phép, để frontend ẩn lựa chọn không hợp lệ:
  `[{ "method": "AFFIRM", "minAmount": 50, "maxAmount": 30000 }, ...]`. Không trả lịch trả.
- Hạn mức đặt trong cấu hình (giá trị mặc định lấy theo tài liệu Stripe hiện hành, kiểm lại trước khi bật):
  ```yaml
  payment:
    installments:
      affirm:   { enabled: false, min-amount: 50.00, max-amount: 30000.00 }
      klarna:   { enabled: false, min-amount: 1.00,  max-amount: 10000.00 }
      afterpay: { enabled: false, min-amount: 1.00,  max-amount: 4000.00 }
      countries: [US]
      currencies: [USD]
  ```
  Partner tắt hoặc ngoài khoảng: `ProviderRegistry` từ chối method đó (400) dù client có gửi lên.

### 4. Fraud (checkout-service → fraud-service)
- `FraudHttpAdapter` map nhóm `INSTALLMENT` thành `BNPL` (thay vì rơi vào `default -> BANK_TRANSFER`).
- fraud-service thêm nhóm `BNPL`: rủi ro tín dụng và đa số rủi ro gian lận danh tính do đối tác chịu, nhưng vẫn giữ
  luật vận chuyển (địa chỉ giao khác billing, kiện hoàn về) vì hàng đã gửi đi. Điểm ban đầu như `PAYPAL`.

### 5. Dữ liệu (Liquibase, payment-service)
`0003-installments.sql`: thêm `authorization_expires_at TIMESTAMPTZ NULL`. Cột `method` (VARCHAR 32, không có CHECK)
không cần đổi. Không lưu điều khoản khoản vay (thuộc đối tác). Sự kiện `PaymentAuthorized` đã có `provider`; thêm
`method` để lakehouse báo cáo tỷ lệ dùng BNPL, giá trị đơn trung bình theo method và phí.

### 6. Cổng để cắm tín dụng nội bộ sau này
Tín dụng nội bộ cũng chỉ là một `PaymentProvider` khác, nên payment-service và saga không cần đổi kiến trúc:
- Method `STORE_INSTALLMENT` (category `INSTALLMENT`), provider `InHouseCreditProvider` gọi một service mới
  `credit-service` (Java, Liquibase, hexagonal) qua HTTP/gRPC:
  `authorize` = kiểm hạn mức và giữ chỗ, `capture` = giải ngân/tạo khoản vay, `void` = nhả hạn mức,
  `refund` = giảm gốc và tính lại lịch.
- `credit-service` giữ: hồ sơ tín dụng và KYC, hạn mức, khoản vay, lịch trả theo kỳ, sổ cái kép (ledger), thu tiền
  định kỳ (Stripe saved PaymentMethod/ACH), nhắc nợ qua notification-service, phí trễ hạn, báo cáo tín dụng.
- `installment-options` khi đó gộp thêm hạn mức nội bộ của khách đã đăng nhập.
- Điều kiện trước khi làm (ngoài phạm vi kỹ thuật): giấy phép từng bang hoặc mô hình hợp tác ngân hàng phát hành,
  tư vấn pháp lý về TILA/Reg Z, ECOA, FCRA, vốn cho vay. Khi đó viết ADR mới.

## Không làm trong ADR này
- Tự cho vay, chấm điểm tín dụng, thu nợ.
- Hoàn tiền một phần, Venmo, các thị trường ngoài Mỹ (Klarna/Afterpay có ở nước khác nhưng cấu hình `countries: [US]`).

## Hệ quả
- Ra mắt nhanh, không cần giấy phép, không gánh nợ xấu; shop nhận đủ tiền khi capture.
- Phí cao hơn thẻ (BNPL qua Stripe khoảng 6% + 30¢ mỗi giao dịch, kiểm lại bảng giá Stripe), bù lại thường tăng tỷ lệ
  chốt đơn và giá trị đơn.
- BNPL luôn đi qua redirect, nên frontend phải truyền `returnUrl` và xử lý trang kết quả cho cả trường hợp bị từ chối.
- Phải bật từng phương thức trong Stripe Dashboard (và đáp ứng điều kiện ngành hàng của từng partner), bật Pay Later
  trong tài khoản PayPal.
