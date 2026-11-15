# 0028. Id thực thể dùng Snowflake

- Trạng thái: Proposed

## Bối cảnh
Id đang lẫn ba kiểu, và mỗi service lại tự khai một class sinh id:
- UUIDv7: các service mới (ADR 0024–0027) và khoảng mười service có sẵn.
- Snowflake BIGINT: cart, inventory, một số bảng của product.
- UUID ngẫu nhiên: product, order, payment.

Quyết định dưới đây đổi tất cả sang Snowflake.

## Quyết định
- Id thực thể là Snowflake 64 bit, lưu `BIGINT`: 41 bit mili-giây, 10 bit node, 12 bit sequence.
- Generator dùng chung, không service nào tự viết lại:
  - Java: `com.congthanh.commonservice.id.Snowflake` (common-core). Service trỏ port `Ids` của mình tới
    `Snowflake::nextString`.
  - Go: `libs/go/platform/idgen`, lấy node bằng `idgen.NodeFromEnv()`.
  - Python: `ecommerce_platform.ids.snowflake()`.
- Java và Python dùng epoch 2021-01-01, giống product-service. Go giữ epoch mặc định của bwmarrin vì cart-service đã
  có dữ liệu sinh theo epoch đó. Id chỉ cần duy nhất trong từng bảng, nên lệch epoch giữa các service không gây trùng.
- Node lấy từ `NODE_ID` (0..1023). Nếu không có, lấy hash của `HOSTNAME` (tên pod). Compose local cấp sẵn mỗi service
  một giá trị. Ở prod, service chạy nhiều replica nên cấp `NODE_ID` riêng cho từng pod, ví dụ chuyển sang StatefulSet
  rồi lấy số thứ tự pod; dùng hash thì vẫn có xác suất nhỏ hai pod trùng node.
- Domain giữ id dạng chuỗi số, vì JavaScript mất chính xác với số lớn hơn 2^53. SQL ép kiểu khi ghi và đọc:
  Java dùng `:id::bigint`, Python và Go dùng `$1::text::bigint`.
- Những thứ giữ nguyên:
  - Id event/outbox vẫn là UUID, vì `OutboxWriter` và `EventEnvelope.id` yêu cầu UUID. Python sinh bằng
    `ecommerce_platform.ids.event_id()`.
  - Id suy ra từ khoá idempotency (`nameUUIDFromBytes`) trong checkout.
  - Id thay thế sau khi xoá dữ liệu: dùng `ErasedIds.of` / `erased_id`.

## Cách đổi schema
- Sửa thẳng kiểu cột id (và khoá ngoại trỏ tới nó) sang `BIGINT` trong changelog/migration gốc của từng service. Không
  viết migration chuyển dữ liệu.
- Seed có id cố định thì đổi luôn giá trị trong file seed. Ví dụ, id danh mục trong seed của catalog-service đổi từ UUID
  sang số, và `product_categories.category_id` trong seed của product-service đổi theo cho khớp.
- Môi trường nào đã chạy changeset cũ thì phải xoá schema của service rồi migrate lại, vì checksum của
  Liquibase/drizzle/goose đã đổi.

## Hệ quả
- Các service đã đổi cột id từ UUID sang BIGINT (sửa thẳng changeset 0001, vì các service này chưa lên môi trường nào):
  ledger, returns, stored-value, dispute, loyalty, subscription, privacy, reconciliation, moderation.
- checkout-service sinh id phiên bằng Snowflake. Cột vẫn là `VARCHAR(36)` để giữ dữ liệu cũ.
- Các service có sẵn cũng đổi theo cách trên: catalog, customer, message, review, notification, media, pricing, shipping,
  webhook, fraud, supplier. Claim flash sale của inventory cũng sinh bằng Snowflake.
- product, order, payment cũng sinh id bằng Snowflake:
  - Cột id và khoá ngoại trong schema của ba service này là `BIGINT`.
  - Entity JPA vẫn giữ `String`, map sang BIGINT bằng `@JdbcTypeCode(SqlTypes.BIGINT)`.
  - Id sản phẩm trong seed đổi sang số.
- Các cột chỉ chép id của service khác (`product_id`, `order_id`, `payment_id` ở service khác) vẫn để kiểu chữ và giữ
  chuỗi số.
