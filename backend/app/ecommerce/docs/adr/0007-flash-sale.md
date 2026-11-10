# 0007. Flash sale

- Trạng thái: Accepted

## Bối cảnh
Hàng nghìn người bấm mua cùng lúc vài chục sản phẩm. Khoá dòng tồn kho trên Postgres cho từng lượt sẽ thành nút cổ chai.

## Quyết định
1. **Mở sale**: inventory-service giữ số lượng bán trong Postgres bằng một reservation `flashsale:<sale>:<product>` (hàng không thể bị bán ở kênh khác) rồi nạp số đó vào Valkey.
2. **Giành hàng**: Lua script nguyên tử trên Valkey kiểm tra còn hàng và giới hạn mỗi khách, trừ số lượng (`services/inventory-service/internal/adapter/repository/redis/lua`). Thành công → publish `FlashSaleClaimed` thẳng lên Kafka (không qua outbox vì không có transaction DB); publish lỗi → trả lại lượt.
3. **Đóng sale**: rút trạng thái khỏi Valkey, commit số đã bán vào reservation, phần dư trả về kho.
4. Gateway giới hạn 5 lượt/10 giây mỗi người và tối đa 2000 kết nối đồng thời cho route flash sale; kill switch `inventory.flash-sale.claim`.

## Hệ quả
- Test `TestFlashSaleNeverOversells`: 200 lượt tranh 20 suất → đúng 20, không ai vượt giới hạn.
- Phòng chờ ảo (virtual waiting room) phía storefront là việc tiếp theo; gateway hiện chỉ giới hạn tốc độ.
