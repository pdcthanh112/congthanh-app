# 0015. Thị trường Mỹ (USD) và thanh toán quốc tế: Stripe (thẻ, Apple Pay, Google Pay), PayPal

- Trạng thái: Proposed

## Bối cảnh
Hệ thống được viết cho thị trường Việt Nam: tiền tệ mặc định VND, VNPay (redirect "sale"), COD, ví MoMo
(chỉ có trong enum), VAT 10% của Việt Nam, bảng phí ship nội địa (HCM/HN), thông báo tiếng Việt, giờ yên tĩnh
mặc định Asia/Ho_Chi_Minh, MCP/chatbot giới thiệu shop "ở Việt Nam". Sản phẩm giờ nhắm khách quốc tế, thị trường
chính là Mỹ, nên các phương thức chỉ có ở Việt Nam là gánh nặng (thêm nhánh `capturesImmediately`, IPN riêng,
luật fraud riêng cho COD) mà không có khách dùng.

## Quyết định
1. **Phương thức thanh toán** (payment-service, order-service, storefront-bff, cùng một enum):
   `CARD`, `APPLE_PAY`, `GOOGLE_PAY` qua Stripe; `PAYPAL` qua PayPal Orders API v2. Bỏ `CASH_ON_DELIVERY`,
   `VN_PAY`, `MOMO`, `STRIPE`, `CREDIT_CARD`. Xoá `VnPayProvider`, `CashOnDeliveryProvider`, endpoint IPN VNPay,
   `PaymentProvider.capturesImmediately()` và `Payment.paidImmediately()`: mọi cổng còn lại đều authorize rồi capture.
2. **Stripe**: frontend dùng Payment Element, gửi ConfirmationToken (`ctoken_...`); `pm_...` vẫn nhận. PaymentIntent
   bật `automatic_payment_methods`, capture thủ công như cũ. Không có `returnUrl` thì `allow_redirects=never`
   (thẻ, Apple Pay, Google Pay xử lý 3-D Secure ngay trên trang); có `returnUrl` thì cho phép phương thức redirect.
3. **PayPal**: `payment_source.paypal` với return/cancel URL, `returnUrl` bắt buộc. Venmo không hỗ trợ (chỉ khách
   Mỹ, cần nút riêng của PayPal JS SDK): khi cần thì thêm `payment_source.venmo` vào cùng provider.
4. **Fraud**: nhóm phương thức `CARD`, `WALLET` (Apple/Google Pay, trừ 5 điểm vì đã xác thực thiết bị), `PAYPAL`,
   `BANK_TRANSFER`. Luật COD đổi thành `RETURNED_SHIPMENTS` (kiện hoàn về, mọi phương thức); cột
   `cod_refused` đổi tên `shipment_returned`.
5. **Mặc định USD, en-US, America/New_York** cho pricing, product, order, checkout, BFF, notification (nội dung
   tiếng Anh), MCP và chatbot. Danh sách tiền tệ không có phần lẻ (JPY, KRW, VND) giữ nguyên vì là quy tắc ISO 4217.
6. **Thuế**: bỏ VAT Việt Nam, nạp thuế bán hàng cấp bang (51 dòng, số liệu 2025) cho lớp Standard; Reduced/Exempt 0%.
   ZIP+4 rút về ZIP 5 số. Thuế quận/thành phố chưa có: production dùng Stripe Tax/Avalara/TaxJar hoặc nạp theo ZIP.
7. **Ship**: bảng giá Mỹ bằng cent (Standard $5.99 + $1.50/kg, miễn phí từ $50; bờ Đông NJ/NY rẻ và nhanh hơn;
   AK/HI riêng; Express $14.99 + $2.50/kg). Adapter hãng thật (USPS/UPS/FedEx qua EasyPost/Shippo) để sau.
8. Dữ liệu cũ: migration Liquibase của payment-service đổi `STRIPE`/`CREDIT_CARD` thành `CARD`; payment VNPay/MoMo/COD
   chưa kết thúc chuyển `VOIDED`, provider `legacy`. pricing đổi bảng giá `default` sang USD (số tiền cũ không quy đổi).

## Không đổi
- Proto giữ nguyên trường (`ward`, `district`, `province_code`...) để không phá tương thích; chỉ sửa chú thích.
  Với địa chỉ Mỹ, `city` của `OrderAddress` vẫn mang mã bang như trước (checkout chuyển sang tax/shipping/fraud).
- Hạ tầng và vận hành của team (region AWS ap-southeast-1, tên miền congthanh.vn, timezone Renovate, lịch backup)
  không thuộc phạm vi ADR này. Chuyển region sang Mỹ là việc riêng vì phải dựng lại state Terraform và dữ liệu.

## Hệ quả
- Ít nhánh đặc biệt hơn trong payment (một luồng authorize → capture → void/refund).
- Frontend phải đổi sang enum mới và Stripe Payment Element; client cũ gửi `CASH_ON_DELIVERY`/`VN_PAY` nhận lỗi 400.
- Bí mật VNPay trong Vault/External Secrets (`vnpay_tmn_code`, `vnpay_hash_secret`) không còn được đọc, nên xoá.
