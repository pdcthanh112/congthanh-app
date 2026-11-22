# 0030. Lưu product / attribute / variant có ràng buộc ở Postgres; lọc và facet ở Elasticsearch

- Trạng thái: Proposed

## Bối cảnh
product-service lưu thuộc tính kiểu EAV thuần, và variant là một dòng con trong `products`. Cách này dùng được, nhưng:
- `variant_option_combination.value` là chữ tự do, không FK tới `variant_option_value`. Một variant có thể có hai giá
  trị trên cùng một trục, hai variant có thể trùng tổ hợp, giá trị có thể không thuộc sản phẩm gốc. Variant còn có thể
  lồng nhiều cấp.
- `product_attribute_value` không unique theo (sản phẩm, thuộc tính), và mọi giá trị đều là chuỗi.
- SKU unique có phân biệt hoa thường. Slug unique cả với sản phẩm đã xoá mềm.
- Giá nằm hai chỗ (`products.price` và bảng `product_prices` không ai dùng).
- Một số query trỏ vào bảng không tồn tại (`category`, `subcategory`, `OrderDetail`, `ProductVariant`). Repository
  tổ hợp trả dữ liệu giả hard-code, nên read model và trang chi tiết hiện option giả.
- Không có chỗ nào lọc theo thuộc tính/option. search-service chỉ biết tên, SKU, một danh mục và giá.

## Quyết định
### 1. Postgres là write model chuẩn hoá, ràng buộc đặt ở DB (sửa thẳng `0001-products.sql`)
- **Variant** là một `products` có `parent_id`. FK `(parent_id, parent_marker) → products(id, root_marker)` (hai cột
  sinh tự động) bảo đảm cha luôn là sản phẩm gốc, nên variant chỉ có đúng một cấp.
- **Tổ hợp option**: `variant_option_combination(variant_id, parent_id, variant_option_id, variant_option_value_id)`.
  - `UNIQUE(variant_id, variant_option_id)`: mỗi trục một giá trị.
  - FK `(variant_id, parent_id) → products(id, parent_id)`: variant đúng là con của sản phẩm gốc đó.
  - FK `(value_id, option_id, parent_id) → variant_option_value(id, variant_option_id, product_id)`: giá trị thuộc
    đúng trục và đúng sản phẩm gốc.
- **`products.variant_key`** là id các giá trị đã chọn, sắp tăng dần, nối bằng `-`. Có unique
  `(parent_id, variant_key)` trên các sản phẩm chưa xoá: không thể có hai variant trùng tổ hợp. Service tính khoá này
  khi tạo variant, seed điền bằng `string_agg`.
- **Thuộc tính mô tả** vẫn là EAV, nhưng có kiểu: `product_attribute` có `code` (khoá ổn định, dùng trong URL lọc và
  chỉ mục), `data_type` (TEXT/NUMBER/BOOLEAN), `unit`, `is_filterable`, `is_searchable`. Thuộc tính lọc được bắt buộc có
  code. `product_attribute_value` unique theo (sản phẩm, thuộc tính) và có thêm `value_number` để lọc theo khoảng.
- **Unique**:
  - `lower(sku)`: không phân biệt hoa thường, kể cả sản phẩm đã xoá (đơn hàng cũ còn tham chiếu SKU).
  - `slug` và `gtin`: chỉ giữa các sản phẩm chưa xoá.
  - `lower(name)` của thuộc tính và trục.
  - `(product_id, variant_option_id, lower(value))` của giá trị trục.
  - `(product_id, category_id)`.
  - Mỗi sản phẩm tối đa một ảnh mặc định.
- **CHECK**: định dạng currency và GTIN, giá không âm, variant không `has_variant`, `variant_key` chỉ có ở variant,
  status nằm trong tập cho phép.
- **Giá**: `products.price` (numeric(19,4), kèm currency) là giá niêm yết gốc. Giá bán theo bảng giá, lịch sử giá và
  khuyến mãi thuộc pricing-service. Bỏ bảng `product_prices`.
- Thêm `version` (khoá lạc quan), `created_at`, `updated_at`. Index trigram trên `lower(name)` cho tra cứu ở back-office.

Vì sao không dùng JSONB cho thuộc tính: JSONB không có FK, không unique theo thuộc tính và không kiểm kiểu. Lọc theo
JSONB ở Postgres cũng không thay được facet của công cụ tìm kiếm. EAV có kiểu giữ được toàn vẹn ở phía ghi, còn phía đọc
đã phi chuẩn hoá sẵn ở Mongo/Elasticsearch.

### 2. Ghi
- `POST /api/ecommerce/products/{parentId}/variants` (SELLER/ADMIN):
  - Nhận SKU, giá, currency và một giá trị cho mỗi trục.
  - Giá trị chưa có trên sản phẩm gốc thì được khai báo thêm; khớp giá trị không phân biệt hoa thường.
  - Mọi variant của một sản phẩm phải dùng cùng bộ trục.
  - Trùng SKU hoặc trùng tổ hợp trả 409. Ràng buộc DB là chốt chặn cuối khi hai yêu cầu chạy song song.
- `GET/POST /api/ecommerce/variant-options` (ghi: ADMIN) quản lý trục dùng chung.
- `/api/ecommerce/product-attribute` (ghi: ADMIN) thêm `PUT /{id}`. Không cho đổi kiểu thuộc tính khi đã có giá trị.
- `/api/ecommerce/attribute-value` chuẩn hoá giá trị theo kiểu (NUMBER điền `value_number`, BOOLEAN thành
  `true`/`false`). Đã gắn `PUT /update` (trước đây thiếu mapping) và thêm `DELETE /{productId}/{attributeId}`.
- Bỏ `/sold/{id}` (đọc bảng đơn hàng không thuộc service này) và `/filter` (luôn trả null). Lọc thuộc về
  search-service. `/search` của product-service chỉ còn là tra cứu tên/SKU cho back-office.
- `/{id}/variants/attributes/detail` trả các trục và giá trị thật từ read model, thay dữ liệu hard-code.

### 3. Event `ProductUpserted` là bản chụp đầy đủ
- Thêm `parent_id`, `has_variant`, `brand_id`, `category_ids`, `attributes` (code, giá trị, số, cờ lọc/tìm),
  `variants` (với sản phẩm gốc: SKU, giá, trạng thái, option) và `options` (với variant).
- Mọi field mới đều là field thêm vào, nên consumer cũ (pricing, recommendation, notification, webhook) không cần sửa.
- `ProductEvents` dựng bản chụp trong transaction, rồi outbox ghi trước commit. Đổi một variant thì sản phẩm gốc phát
  lại, để chỉ mục luôn có danh sách variant mới nhất.

### 4. Đọc
- **Mongo** (trang chi tiết, ADR 0020): document có thêm `optionGroups` (trục và giá trị có variant đang bán), thuộc
  tính kèm code/số/đơn vị, option của variant kèm `valueId`. Không thêm index thuộc tính/giá/text vào Mongo: Mongo chỉ
  được đọc theo id/slug, còn lọc và tìm kiếm thuộc về Elasticsearch. Index không có query dùng chỉ tốn ghi.
- **Elasticsearch** (search-service):
  - Chỉ lập chỉ mục sản phẩm gốc. Event của variant (`parent_id` khác rỗng) chỉ xoá tài liệu cũ nếu có.
  - Tài liệu gom SKU mọi variant, khoảng giá `[price, priceMax]` của các variant đang bán (cùng currency, không quy
    đổi), `attributes` và `options` kiểu nested, và `searchText` từ các thuộc tính searchable.
  - `GET /api/ecommerce/search/products?brand=&attr.<code>=a,b&opt.<trục>=x&currency=&minPrice=&maxPrice=`. Các giá
    trị trong một bộ lọc là OR, các bộ lọc khác nhau là AND.
  - Lọc theo giá bắt buộc có `currency` (gRPC có thêm `SearchProductsRequest.currency`).
  - Facet: danh mục, thương hiệu, thuộc tính, option.
  - Mapping đổi nên tên chỉ mục mặc định là `products-v2`.

## Hệ quả
- Sửa thẳng changelog gốc như ADR 0028, nên DB local đã migrate phải xoá schema `product_service` rồi chạy lại
  migration (kèm context seed). Seed giữ nguyên dữ liệu: chỉ câu INSERT tổ hợp đổi sang `INSERT ... SELECT` để tra id
  giá trị, và thêm vài lệnh UPDATE điền `variant_key`, code và kiểu thuộc tính.
- Chỉ mục Elasticsearch mới (`products-v2`) cần dựng lại: gọi `POST /api/ecommerce/products/read-model/rebuild`
  không đủ, vì lệnh đó chỉ dựng Mongo. Cần phát lại `ProductUpserted` cho mọi sản phẩm (việc tiếp theo: endpoint phát
  lại event, hoặc replay topic từ đầu cho group của search-service).
- Facet option tính ở mức sản phẩm. "White + 128 GB" khớp sản phẩm có variant White và variant 128 GB, kể cả khi
  không có variant White/128 GB. Muốn khớp đúng từng variant thì phải lập chỉ mục variant dạng nested.
- Facet chưa theo kiểu multi-select: chọn một giá trị trong facet thì facet đó cũng bị thu hẹp. Muốn mỗi facet bỏ qua
  bộ lọc của chính nó thì dùng `post_filter` cho từng facet.
- storefront-bff vẫn gọi `/products/search` của product-service. Chuyển sang search-service để có facet là việc của
  BFF và frontend.
- Sửa kèm: cột `currency` character(3) thiếu `@JdbcTypeCode(CHAR)`, nên Hibernate `validate` từ chối khởi động.
- Có integration test (`./gradlew :services:product-service:integrationTest`, cần Docker) chạy changelog và seed thật
  trên Postgres 18, kiểm Hibernate validate, các query, luồng tạo variant và ràng buộc DB.

## Bổ sung (2026-11-22): hoàn thiện product-service
- **Tạo/sửa sản phẩm**:
  - `POST /api/ecommerce/products` nhận JSON (`CreateProductRequest`): sku, gtin, brandId, featured, slug (bỏ trống thì
    sinh từ tên, trùng thì thêm hậu tố), danh mục, ảnh là media id của media-service (không nhận file), thuộc tính có
    kiểu và variant, tất cả trong một transaction.
  - `PUT /api/ecommerce/products` sửa từng phần, kèm `version` để khoá lạc quan (lệch thì 409).
  - `PATCH /{id}/status` bật/tắt bán: bật cần có giá, hoặc có variant đang bán có giá.
  - Xoá sản phẩm gốc thì xoá mềm luôn các variant.
- **Danh mục**: `POST/DELETE /{id}/categories/{categoryId}` phát event. Id dùng `Snowflake` của `libs/java`; đã bỏ
  generator riêng của service.
- **Idempotency-Key** (`platform.idempotency.paths`) cho `POST /products` và `POST /products/*/variants`.
- **gRPC**: `ProductDetail` thêm `original_price_money`/`selling_price_money` (`Money`). Hai trường `double` cũ đánh
  dấu `deprecated`, vẫn điền cho client cũ. order-service và `libs/ts` (BFF) đã đọc trường Money.
- **Phát lại event**: `POST /api/ecommerce/products/read-model/replay-events` (ADMIN) phát `ProductUpserted` cho
  mọi sản phẩm qua outbox, dùng để dựng chỉ mục `products-v2` của search-service.
- **storefront-bff**:
  - `searchProducts` chuyển sang search-service.
  - Thêm query `searchCatalog(input: ProductSearchInput)`, trả sản phẩm kèm facet danh mục, thương hiệu, thuộc tính và
    option.
  - `ProductClient.search` không còn trong port của BFF; `/products/search` của product-service chỉ dùng cho
    back-office.
- **Bỏ**: GraphQL của product-service (không có trong supergraph, trả thẳng entity JPA), ModelMapper (đổi sang map
  tay), các repository custom hỏng.
- **Gateway và mesh**: route APISIX thêm các path `product-attribute`, `attribute-value`, `product-image`,
  `product-view`, `variant-options` của product-service. Mesh thêm `allow-search-service` (trước đây chưa có policy
  nào cho search-service).

## Bổ sung (2026-11-22): product-service theo DDD
Trước đây `domain/model` là entity JPA (Lombok `@Setter`, Hibernate), không có hành vi; logic nằm rải ở service.
- **Domain thuần** (ArchUnit `HexagonalArchitecture.strict`, không Spring/JPA/Jackson):
  - Aggregate `Product` giữ mọi bất biến: slug/GTIN đúng định dạng, có ít nhất một danh mục, ảnh đại diện phải là ảnh
    của sản phẩm, bật bán cần giá hoặc có variant bán được, variant chỉ một cấp và dùng cùng bộ trục, giá trị trục
    khai báo trên sản phẩm gốc, xoá mềm chặn mọi thay đổi sau đó.
  - Mỗi thay đổi ghi `ProductEvent` (`ProductChanged`, `ProductDeleted`).
  - Value object: `Money`, `ProductImage`, `CategoryAssignment`, `AttributeValue`, `OptionValue`, `OptionSelection`.
  - Aggregate `ProductAttribute` tự chuẩn hoá giá trị theo kiểu và không cho đổi kiểu khi đã có giá trị.
  - `VariantOption` là record.
- **Application** chỉ biết port:
  - `ProductRepository` (aggregate), `ProductQueries` (DTO cho back-office), `ProductSnapshots` (bản chụp cho read
    model), `ProductAttributeRepository`, `VariantOptionRepository`, `ProductViews`.
  - Kiểm trùng slug/SKU/GTIN/tổ hợp ở use case trước khi gọi aggregate; DB là chốt chặn cuối.
- **Adapter persistence** (`adapter/out/persistence`): entity JPA package-private, `XxxJpaRepository`, map
  entity ↔ aggregate. `save` ghi aggregate, rồi đổi event thành bản chụp `ProductUpserted`/`ProductDeleted` và ghi outbox
  cùng transaction (đổi variant thì phát lại cả sản phẩm gốc). Read model Mongo và event dùng chung `ProductSnapshot`, nên
  hai nơi luôn khớp nhau. Bỏ `ProductEvents` và `ProductOutboxListener` (event Spring + listener `BEFORE_COMMIT`).
- **Test**:
  - Unit test cho aggregate (`ProductTest`).
  - Read model test dùng port giả viết tay, không Mockito.
  - Integration test của adapter và use case chạy trên Postgres 18 thật (`:integrationTest`, cần Docker).
