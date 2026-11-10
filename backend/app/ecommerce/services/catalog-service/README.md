# catalog-service (NestJS)

Danh mục (cây ≤ 5 cấp), thương hiệu, tag sản phẩm, quốc gia.

| Cổng                            | Dùng cho                                                                          |
| ------------------------------- | --------------------------------------------------------------------------------- |
| HTTP `5003` `/graphql`          | Subgraph Federation v2: `Category`, `Brand`, `Tag`, `Country`, góp `Product.tags` |
| gRPC `19092`                    | `CategoryService`, `BrandService`, `TagService` cho product-service               |
| `/health/live`, `/health/ready` | Probe Kubernetes                                                                  |

## Cấu trúc (lục giác)

```
src/domain/          Category (chặn vòng, giới hạn độ sâu, khoá lạc quan), slug, tag, brand: không framework
src/application/     CatalogService (use case) + ports
src/adapters/in/     graphql/ (resolver, DataLoader), grpc/ (Connect)
src/adapters/out/    persistence/ (Drizzle, outbox), product-directory.grpc.ts (gọi product-service)
drizzle/             migration SQL (0000 schema, 0001 seed dữ liệu tham chiếu từ Liquibase cũ)
```

Luật phụ thuộc kiểm bằng `.dependency-cruiser.cjs` ở gốc (`npm run lint`).

## Event (outbox → Debezium → Kafka `ecom.catalog.events`)

`CategoryUpserted`, `BrandUpserted`, `ProductTagsChanged` (`api/proto/ecommerce/catalog/v1/catalog_events.proto`).
search-service dùng để cập nhật index, product-service dùng để cache tên danh mục.

## Chạy local

```bash
npm install                                   # ở backend/app/ecommerce
npm run build -w @ecom/proto -w @ecom/shared -w @ecom/platform -w @ecom/catalog-service
MIGRATE_ON_START=true DATABASE_URL=postgres://postgres:postgres@localhost:5432/ecommerce \
  LOG_FORMAT=pretty npm start -w @ecom/catalog-service
```

Test: `npx vitest run services/catalog-service` (thêm `TEST_DATABASE_URL=...` để chạy test tích hợp Postgres).

Đổi schema: sửa `src/adapters/out/persistence/schema.ts` → `npm run db:generate -w @ecom/catalog-service` → commit cả `drizzle/`.
Production chạy `node dist/migrate.js` ở init container (Helm), không bật `MIGRATE_ON_START`.

## Thay đổi so với bản Spring cũ

- REST `/api/v1/category/**` được thay bằng GraphQL qua router; gRPC giữ nguyên tên method để product-service không phải sửa.
- Bỏ `created_by/updated_by` (audit lấy từ trace + event), thêm `version` cho khoá lạc quan và `sort_order`.
- Slug trùng trong seed cũ (`economics`, `behavioral-economics`, ...) được thêm hậu tố `-2`.
- Brand/country dùng id identity mới (id cũ 18 chữ số vượt `Int` của GraphQL).
