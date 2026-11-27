# 0032. Partition theo tháng cho bảng nhật ký ghi thêm lớn

- Trạng thái: Proposed

## Bối cảnh
Một vài bảng chỉ ghi thêm và tăng theo lưu lượng (lượt xem, clickstream, biến động tồn kho). Để nguyên một bảng thì
index phình theo toàn bộ lịch sử, vacuum chạy trên cả bảng, và dọn dữ liệu cũ phải `DELETE` hàng loạt gây bloat.

Postgres partition có ràng buộc: PK và mọi UNIQUE phải chứa cột partition, FK trỏ tới bảng partition phải trỏ tới cả
khoá đó. Vì vậy chỉ bảng không cần duy nhất toàn cục (ngoài id) và không bị bảng khác tham chiếu mới hợp.

Đã rà toàn bộ schema các service. Partition:

| Bảng | Khoá | Lý do |
|---|---|---|
| `inventory.movements` | RANGE `created_at`, tháng | Nhật ký mỗi lần giữ/nhả/trừ/chỉnh, tăng theo đơn, đọc theo `(product_id, created_at)` |
| `recommendation.interactions` | RANGE `at`, tháng | Clickstream lớn nhất; mọi truy vấn lọc theo cửa sổ thời gian (14, 90, 180 ngày) |
| `product_service.product_view` | RANGE `viewed_at`, tháng | Một hàng mỗi lượt xem, không ai sửa hay tham chiếu |

Không partition, kèm lý do:
- `outbox`, `processed_events` (mọi service): janitor trong `libs/*` đã xoá theo lô (outbox giữ 1 giờ, processed 14
  ngày) nên bảng nhỏ; outbox còn dính publication Debezium. `processed_events` cần UNIQUE `(event_id, consumer)` toàn
  cục để idempotent, partition theo thời gian sẽ phá điều đó.
- `orders`, `order_lines`, `payments`, `payment_refunds`: UNIQUE toàn cục (`order_code`, `payments.order_id`) và FK
  trỏ vào; tra theo id. Phân tích lịch sử đi qua lakehouse (CDC), không cần partition OLTP.
- `ledger.journal_entries`, `postings`: FK từ `postings`, `manual_entries`; chỉ ghi thêm và giữ vĩnh viễn theo kế toán.
- `stored_value.movements`: UNIQUE `(type, reference)` là khoá idempotency.
- `webhook.deliveries`: UNIQUE `(webhook_id, event_id)`. `notification.notifications`: UNIQUE `dedup_key`, FK từ
  `deliveries`. `message.messages`: UNIQUE theo hội thoại, đọc theo hội thoại.
- `shipment_events`, `price_history`, `search_history`, fraud, moderation: khối lượng tỉ lệ với đơn hoặc nhỏ, chưa đáng.

## Quyết định
- Partition RANGE theo tháng (ranh giới 00:00 UTC ngày 1), tên `<bảng>_pYYYYMM`, kèm partition `DEFAULT` làm lưới an
  toàn. PK đổi thành `(id, <cột thời gian>)`; id vẫn lấy từ sequence/identity cũ nên vẫn không trùng.
- Mỗi schema có hai hàm do migration tạo: `ensure_monthly_partitions(parent, months_ahead)` (generic: tạo partition từ
  tháng cũ nhất đang nằm trong DEFAULT tới N tháng sau, chuyển hàng từ DEFAULT, tạo bằng `LIKE` + `ATTACH` để không
  khoá ACCESS EXCLUSIVE bảng cha, khoá advisory cho nhiều replica) và `maintain_partitions()` (liệt kê bảng của service).
- Job tạo partition tương lai chạy trong service, không dùng pg_partman/pg_cron: image local (pgvector) và
  Testcontainers không có extension đó. Janitor của `libs/go` (`outbox.Janitor`, 5 phút) và `libs/java`
  (`OutboxJanitor`) gọi `maintain_partitions()` nếu schema có hàm; service Python gọi trong batch định kỳ
  (recommendation: `rebuild`). Giữ sẵn 3 tháng tới.
- Bỏ dữ liệu cũ là thao tác vận hành có chủ đích (`ALTER TABLE ... DETACH PARTITION ... CONCURRENTLY` rồi `DROP`), hàm
  không tự xoá.

## Hệ quả
- Truy vấn theo khoảng thời gian chỉ quét vài partition; index và vacuum theo tháng.
- Tra theo id không có cột thời gian phải dò index của mọi partition (chấp nhận được vì các bảng này không tra theo id).
- `interactions` giữ hàng `PURCHASE` vĩnh viễn nên chưa DROP được partition cũ; muốn vậy phải chuyển PURCHASE sang
  `product_orders` trước. `prune` vẫn `DELETE` như cũ.
- Hàng nằm trong `DEFAULT` là dấu hiệu job không chạy; nên cảnh báo khi `DEFAULT` có hàng.
- Thêm bảng partition mới ở service khác: chép `ensure_monthly_partitions` vào migration của service đó (DB per
  service) và tạo `maintain_partitions()`; service NestJS cần thêm lời gọi vào janitor của `libs/ts`.
