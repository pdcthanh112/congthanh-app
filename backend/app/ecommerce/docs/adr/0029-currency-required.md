# 0029. Mọi khoản tiền phải mang đơn vị tiền của giao dịch

- Trạng thái: Proposed

## Bối cảnh
Hệ thống phục vụ nhiều quốc gia, nhưng nhiều chỗ tự gán USD khi dữ liệu thiếu đơn vị tiền, và giá sản phẩm chỉ là một
con số nên order, checkout và BFF phải lấy đơn vị tiền từ cấu hình (`*_CURRENCY`, mặc định USD).

## Quyết định
- **Không có đơn vị tiền mặc định ở bất kỳ đâu.** Thiếu currency thì từ chối request, hoặc bỏ qua event và ghi log lỗi.
  Không đoán.
- **Giá sản phẩm đi kèm currency.**
  - Bảng `products` có cột `currency`, bắt buộc khi có `price` (ràng buộc CHECK).
  - gRPC `ProductDetail.currency` và `ProductResponse.currency`, REST (`ProductDetailVm`, `ProductVariantVm`), event
    `ProductUpserted.price` và read model đều lấy từ cột này.
  - Bỏ cấu hình `product.currency`, `order.currency`, `checkout.currency` và biến `CURRENCY` của hai BFF.
- **Nơi nhận request phải được truyền currency:**
  - checkout: `currency` bắt buộc khi mở phiên. Phiên flash sale dùng currency của sản phẩm.
  - subscription: gói lưu `currency` chọn lúc đăng ký; đơn gia hạn gửi kèm currency đó.
  - pricing: mọi truy vấn giá phải nêu currency.
  - payment `installment-options`: tham số `currency` bắt buộc.
- **storefront-bff:** tạm tính của giỏ dùng currency của giá sản phẩm trong giỏ. `Cart.subtotal` là `null` khi giỏ chưa
  có dòng nào có giá. Giỏ lẫn nhiều currency thì báo lỗi.
- **reconciliation và ledger:** mỗi currency một lượt đối soát; phí cổng hạch toán riêng theo currency.
- **loyalty:** chương trình điểm gắn với một currency cấu hình bắt buộc (`LOYALTY_CURRENCY`, `LOYALTY_POINT_VALUE`,
  không có mặc định trong code). Đơn khác currency không tích điểm; đổi điểm cho đơn khác currency bị từ chối.

## Hệ quả
- Sản phẩm có giá mà thiếu currency không ghi được vào DB. Order và giỏ hàng coi sản phẩm không có currency là không mua
  được.
- Client mở phiên checkout và đăng ký subscription phải gửi `currency`.
- loyalty vẫn chỉ phục vụ một currency cho mỗi môi trường. Hỗ trợ nhiều currency cùng lúc cần tỷ lệ tích điểm và giá trị
  điểm cho từng loại (quyết định kinh doanh).
- Giá thật sự theo từng thị trường vẫn nằm ở bảng giá của pricing-service; `products.price` là giá gốc kèm currency.
