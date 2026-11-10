# 0011. Service NestJS, GraphQL Federation v2 và Cosmo Router

- Trạng thái: Accepted, triển khai ở đợt 3

## Bối cảnh
Frontend (storefront, backoffice) cần dữ liệu ghép từ nhiều service Go/Java: một trang sản phẩm cần giá
(product), tồn kho (inventory), điểm đánh giá (review), trạng thái wishlist (customer). Gọi thẳng từng REST
API làm frontend phải biết topology backend và gây N+1 request qua mạng di động. ADR 0001 đã chọn NestJS cho
BFF và service CRUD; ADR này chốt cách làm.

## Quyết định
- **5 service NestJS có DB** (catalog, customer, review, notification, message) và **2 BFF không DB**
  (storefront-bff, backoffice-bff). Tất cả là subgraph **Apollo Federation v2**, code-first
  (`@nestjs/graphql` + `ApolloFederationDriver`).
- **Router: WunderGraph Cosmo Router** (Go, Apache-2.0), không dùng Apollo Router. Hai supergraph:
  `storefront` (:4000, sau APISIX `/graphql`) và `backoffice` (:4001, sau APISIX `/admin/graphql`).
  Supergraph compose bằng `wgc router compose` từ `platform/graphql/<graph>/graph.yaml`, không cần control plane.
- **Product thuộc BFF**: `Product @key(id)` được storefront-bff/backoffice-bff phân giải từ product-service
  (gRPC `GetProductsByIds` theo lô cho giá/tên, REST cho chi tiết); catalog/customer/review/message chỉ
  góp thêm trường (`tags`, `inWishlist`, `ratingSummary`, `reviews`). Mọi Product trong một truy vấn được gom
  bằng DataLoader thành một lần gọi gRPC mỗi service.
- **Xác thực ở cả router và subgraph**: router kiểm chữ ký JWT (Keycloak JWKS) và chuyển tiếp `Authorization`;
  subgraph kiểm lại (`PrincipalMiddleware` chạy cho mọi request, kể cả `_entities` mà guard Nest không bọc)
  và quyết định theo `@Public()` / `@Roles()`. BFF chuyển tiếp token người dùng xuống service phía sau
  (token relay) nên quyền sở hữu giỏ/đơn vẫn do cart-service/order-service kiểm.
- **Subscription** (thông báo, chat): graphql-ws qua router; subgraph phát sự kiện qua Valkey pub/sub nên
  scale ngang nhiều pod. Trình duyệt gửi token trong `connection_init`, router chuyển thành header.
- **Persistence**: Drizzle ORM, mỗi service một `pgSchema`, migration SQL sinh bằng drizzle-kit và chạy ở
  init container (advisory lock). Service Java vẫn dùng Liquibase.
- **Event**: cùng hợp đồng với Go/Java: bảng `outbox` + Debezium, `processed_events` cho consumer idempotent,
  payload Protobuf từ `api/proto`.
- **Kiến trúc lục giác** kiểm bằng dependency-cruiser (`domain` thuần, `application` chỉ biết port,
  không import chéo service).

## Vì sao Cosmo thay Apollo Router
- Apollo Router miễn phí (Elastic License v2) không có `@authenticated`/`@requiresScopes`, subscription
  qua callback, persisted queries và giới hạn độ phức tạp: các tính năng đó cần GraphOS Enterprise.
- Cosmo Router là Apache-2.0, có sẵn JWT, subscription WebSocket (graphql-ws), giới hạn độ sâu/số trường,
  retry cho query, OTel và Prometheus; compose offline bằng `wgc`, nên CI không phụ thuộc dịch vụ SaaS.
- Subgraph vẫn là Federation v2 chuẩn: đổi router về sau không phải sửa service.

## Hệ quả
- CI (`ci-node.yml`) kiểm: typecheck, Biome, dependency-cruiser, test (có Postgres thật), migration không bị
  quên, `schema.graphql` của subgraph đã cập nhật, supergraph compose được và khớp `supergraph.graphql` đã
  commit, router config hợp lệ theo JSON schema chính thức của Cosmo Router.
- Thêm trường vào Product ở subgraph khác chỉ cần `@ResolveField` trên `ProductRef`; không cần sửa BFF.
- BFF không có trạng thái nên scale theo CPU; khi product-service/inventory lỗi, trường liên quan trả `null`
  hoặc lỗi `UNAVAILABLE` cho đúng trường đó, phần còn lại của truy vấn vẫn trả về.
- Đổi tên hay xoá trường public là breaking change: phải đánh dấu `@deprecated` trước, kiểm bằng diff
  `supergraph.graphql` khi review.
