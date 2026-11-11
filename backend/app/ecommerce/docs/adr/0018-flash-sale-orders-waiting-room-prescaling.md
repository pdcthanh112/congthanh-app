# 0018. Flash sale: từ suất đã giành tới đơn hàng, phòng chờ ảo, scale trước theo lịch

- Trạng thái: Proposed
- Bổ sung ADR 0007 (cơ chế giành hàng bằng Lua trên Redis giữ nguyên)

## Bối cảnh
ADR 0007 dừng ở chỗ giành được suất: `FlashSaleClaimed` được đẩy lên Kafka nhưng không service nào đọc, nên suất
đã giành không thành đơn, cũng không bao giờ được trả lại nếu khách bỏ đi. Gateway chỉ giới hạn tốc độ, ai bấm
nhanh thì được (bot thắng). Autoscale theo CPU phản ứng sau 1 tới 3 phút trong khi tải flash sale dồn về trong
vài giây. Luồng đặt đơn thường không có giới hạn đồng thời nào ngoài rate limit theo IP.

## Quyết định
### 1. Suất đã giành là một claim có hạn, và nó thành đơn qua checkout bình thường
- `claim.lua` tạo claim `PENDING` (hạn `FLASH_CLAIM_TTL`, mặc định 10 phút) cùng lúc với việc trừ bộ đếm.
- checkout-service đọc `FlashSaleClaimed` và mở sẵn phiên checkout: **id phiên = order id = claim id**, chỉ chứa
  đúng sản phẩm và số lượng đã giành (không sửa được), hết hạn cùng lúc với claim. Khách thêm địa chỉ, chọn
  thanh toán rồi `place` như mọi đơn khác; fraud check, thuế, phí ship, voucher không có nhánh riêng.
- Saga đặt hàng không đổi. Khi order-service gọi `ReserveInventory`/`CommitReservation`/`ReleaseInventory` với
  reservation id `checkout-<orderId>`, inventory-service tra `<orderId>` trong chỉ mục claim trên Redis: là claim
  thì xác nhận (`CONFIRMED`, không còn bị thu hồi), chốt (`COMMITTED`) hoặc trả suất, **không giữ thêm hàng
  trong Postgres** vì số hàng của đợt sale đã được giữ từ lúc mở. Không phải claim thì đi đường cũ.
- Sweeper (5 giây, mọi pod, Lua nguyên tử) trả suất của claim `PENDING` quá hạn cho người đang chờ.
- Đóng sale bị từ chối (409) khi còn claim chưa chốt, để số "đã bán" chốt vào Postgres luôn khớp số đơn.

Phương án đã cân nhắc: order-service tự đọc event và tạo đơn (thiếu địa chỉ, phương thức thanh toán, giá);
thêm trường flash sale vào `PlaceOrderRequest` và nhánh riêng trong activity của order-service (đúng nghĩa hơn
nhưng đổi contract của ba service; nhận diện qua order id cho cùng kết quả mà order-service không cần biết).

### 2. Phòng chờ ảo ở inventory-service
- Mở sale với `admitPerSecond > 0` thì phải xếp hàng (`POST .../queue`) mới được giành. Thứ tự là thứ tự đến;
  số người được vào = `admitBurst + admitPerSecond × số giây từ opensAt`, tính trong Lua từ đồng hồ nên mọi pod
  cho cùng kết quả mà không cần tiến trình điều phối. `claim.lua` tự kiểm tra lượt, không có token để làm giả.
- Client hỏi lại `GET .../queue` theo `retryAfterSeconds` (2 tới 30 giây) cho tới khi `canClaim = true`.
- Chọn `admitPerSecond` theo sức chịu của checkout (số pod × `checkout.place.max-concurrent`), không theo sức
  chịu của Redis.

### 3. Scale trước theo lịch
- Lịch sự kiện khai một lần ở `deploy/helm/events/events-<env>.yaml`; service khai `scheduledScaling.replicas`.
  Chart render KEDA `ScaledObject` với trigger `cron` (giữ sẵn số pod trong khung giờ) cạnh trigger CPU và
  Kafka lag. Service không tham gia vẫn dùng HPA như cũ.
- Scale lên không còn cửa sổ ổn định (gấp đôi mỗi 15 giây), scale xuống giữ 5 phút.
- checkout-service scale thêm theo lag của `ecommerce.inventory.events`; trần prod nâng từ 10 lên 30 pod.

### 4. Chống quá tải cho đặt đơn
- checkout-service giới hạn số lượt `place` đồng thời mỗi pod (`checkout.place.max-concurrent`, mặc định 16, nhỏ
  hơn pool DB 20). Quá ngưỡng trả `503 CHECKOUT_BUSY` + `Retry-After: 2`; client gửi lại với cùng
  `Idempotency-Key`.
- Gateway: route checkout giới hạn 30 lượt/10 giây mỗi người và 1500 kết nối đồng thời; route flash sale nâng
  lên 10 lượt/10 giây để đủ cho việc hỏi vị trí trong phòng chờ.
- Mở/đóng flash sale giờ cần role `ADMIN` (trước đây khách đăng nhập nào cũng gọi được).

## Hệ quả
- Phiên checkout của một claim xuất hiện trễ vài trăm mili giây (qua Kafka); client gặp 404 thì hỏi lại. Lúc cao
  điểm độ trễ này tăng chứ luồng giành không nghẽn, đây chính là bộ đệm.
- Redis lỗi khi **đang có sale**: giữ/chốt/trả hàng của mọi đơn được saga thử lại cho tới khi Redis về (không
  đoán đơn nào là flash sale). Không có sale nào đang chạy thì đơn thường không phụ thuộc Redis.
- Đơn flash sale bị huỷ **sau khi sale đã đóng** không trả hàng về kho tự động (số đã bán đã chốt); cần nhập lại
  bằng điều chỉnh tồn kho.
- Phiên mở từ event không có nhóm khách hàng, nên giá flash sale là giá chung (lịch giảm giá ở pricing-service),
  không áp bảng giá riêng theo nhóm.
- Xếp hàng trước giờ mở vẫn theo thứ tự đến; chưa xáo trộn ngẫu nhiên nhóm đến sớm và chưa có CAPTCHA/chấm
  điểm bot ở edge. Giới hạn mỗi khách và rate limit theo tài khoản là lớp chặn hiện có.
- Database và Redis không scale theo lịch: xem `docs/runbooks/flash-sale.md` để chuẩn bị trước đợt sale.
- Cần KEDA trong cluster (đã cài) cho mọi service có `scheduledScaling`; frontend chưa có màn phòng chờ.
