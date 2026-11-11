# Runbook: vận hành một đợt flash sale

Thiết kế: ADR 0007 và 0018.

## Trước đợt sale (ít nhất 1 ngày)
- Thêm khung giờ vào `deploy/helm/events/events-prod.yaml`, bắt đầu trước giờ mở 15 tới 30 phút, rồi merge. Kiểm
  tra: `kubectl get scaledobject -n ecommerce` và số pod tăng đúng giờ.
- Tạo lịch giá sale ở pricing-service (`POST /api/ecommerce/pricing/schedules`) trùng giờ mở.
- Database: mỗi pod checkout dùng tối đa `DATABASE_POOL_SIZE` (20) kết nối qua PgBouncer
  (`platform/data/postgres-cnpg.yaml`, `max_client_conn` 5000, `default_pool_size` 50). Số pod × pool của mọi
  service phải dưới `max_client_conn`. Aurora không scale theo lịch: nâng instance class trước nếu đợt lớn.
- Redis: bộ đếm, claim và phòng chờ của một sale tốn dưới 200 byte mỗi khách; 1 triệu người xếp hàng khoảng
  200 MB. Kiểm tra dung lượng node ElastiCache; không `terraform apply` thay đổi cụm cache trong lúc có sale.
- Diễn tập trên staging: `k6 run tests/load/k6/flash-sale.js` với danh sách token của nhiều khách.

## Mở sale (role ADMIN)
```bash
curl -X POST "$API/api/ecommerce/flash-sales/$SALE/products/$PRODUCT/start" -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"quantity":500,"perCustomerLimit":1,"opensAt":"2026-11-27T05:00:00Z","endsAt":"2026-11-27T07:00:00Z","admitPerSecond":50}'
```
- Gọi trước giờ mở để khách xếp hàng sớm. `admitPerSecond` = số đơn/giây checkout chịu được (khoảng số pod
  checkout × 16 ÷ thời gian đặt đơn trung bình tính bằng giây); để 0 nếu không cần phòng chờ.
- Hàng của đợt sale bị trừ khỏi tồn kho bán thường ngay lúc gọi.

## Trong lúc sale
- Theo dõi: lag group `checkout-service` trên `ecommerce.inventory.events` (phiên checkout mở chậm), tỉ lệ
  `503 CHECKOUT_BUSY`, p95 route `flash-sale` và `checkout` ở gateway, CPU Redis.
- `CHECKOUT_BUSY` nhiều: giảm `admitPerSecond` cho đợt sau hoặc tăng `scheduledScaling.replicas` của checkout.
- Tắt khẩn: flag Unleash `inventory.flash-sale.claim` = off (khách đang checkout vẫn đặt được đơn).

## Đóng sale (role ADMIN)
```bash
curl -X POST "$API/api/ecommerce/flash-sales/$SALE/products/$PRODUCT/end" -H "Authorization: Bearer $ADMIN"
```
- Trả 409 khi còn khách đang checkout hoặc chờ thanh toán: đợi (tối đa hạn claim 10 phút + hạn thanh toán 15
  phút) rồi gọi lại. Kết quả `{initial, sold, returned}`: `sold` phải bằng số đơn flash sale đã thanh toán.
- Đơn flash sale bị huỷ sau khi đã đóng sale: nhập lại hàng bằng `POST /api/ecommerce/inventory/adjust`.
- Xoá khung giờ đã qua khỏi `events-prod.yaml`.
