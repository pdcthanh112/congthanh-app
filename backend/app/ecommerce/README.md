# CongThanhApp — nền tảng e-commerce

Monorepo backend của e-commerce: service polyglot (Go, Java, NestJS), event-driven trên Kafka, chạy trên Kubernetes bằng GitOps.
Kiến trúc tổng quan: [docs/architecture/overview.md](docs/architecture/overview.md) · Quyết định: [docs/adr](docs/adr).

## Cấu trúc

```
api/          contract: proto (gRPC + event), asyncapi, openapi, graphql — nguồn sự thật duy nhất
libs/
  go/         platform (otel, logging, db, outbox, kafka, featureflag, auth, server, grpcx) + proto (code sinh)
  java/       common-core, proto (code sinh)
  ts/         platform (NestJS: auth, GraphQL subgraph, Drizzle, outbox, Kafka, gRPC, Valkey), shared, clients, proto-gen
  python/     thư viện chung cho service Python
services/     mỗi service một thư mục, kiến trúc hexagonal (domain / app / adapter / cmd)
platform/     cấu hình nền: gateway (APISIX), identity (Keycloak), messaging (Kafka, Debezium, Schema Registry),
              mesh (Istio Ambient), observability (OTel, LGTM, Pyroscope, SLO), policy (Kyverno), authz (OpenFGA),
              workflow (Temporal), secrets (Vault, ESO), featureflags (Unleash), graphql (Cosmo Router), data
deploy/       helm (chart microservice + values theo service/môi trường), argocd, local (docker compose), legacy
infra/        terraform AWS: network, EKS + Karpenter, Aurora PG, Valkey, MSK, OpenSearch, S3 + CloudFront
tests/        load (k6), chaos (Chaos Mesh), contract, e2e (Hurl)
tools/        script sinh cấu hình, Backstage template, agent
```

## Service

| Service | Ngôn ngữ | HTTP | gRPC | Ghi chú |
|---|---|---|---|---|
| inventory-service | Go 1.26 | 5014 | 9092 | tồn kho, kho, giữ chỗ saga, flash sale (Valkey Lua) |
| cart-service | Go 1.26 | 5002 | 19091 | giỏ hàng; xoá sản phẩm đã đặt khi nhận `OrderPlaced` |
| tax-service | Go 1.26 | 5022 | 9096 | thuế suất theo vùng, tính thuế từng dòng |
| search-service | Go 1.26 | 5013 | 9098 | tìm kiếm, gợi ý sản phẩm (OpenSearch), cập nhật từ `ecom.product.events` |
| shipping-service | Go 1.26 | 5016 | 9097 | phí giao hàng, vận đơn, hành trình; tạo vận đơn khi nhận `OrderPlaced` |
| media-service | Go 1.26 | 5007 | | upload S3/R2 bằng URL ký sẵn (thay file-service) |
| webhook-service | Go 1.26 | 5023 | | webhook bên thứ ba: HMAC, thử lại có backoff, chặn SSRF |
| product-service | Java 25 / Spring Boot 4 | 5008 | 29090 | |
| order-service | Java 25 / Spring Boot 4 | 5006 | 19090 | saga checkout → Temporal (đợt 2) |
| payment-service | Java 25 / Spring Boot 4 | 5018 | 9090 | |
| promotion-service | Java 25 / Spring Boot 4 | 5009 | 9090 | |
| catalog-service | NestJS 12 / TS 6 | 5003 | 19092 | danh mục, thương hiệu, tag, quốc gia |
| customer-service | NestJS 12 / TS 6 | 5004 | 19094 | địa chỉ giao hàng, wishlist, lịch sử tìm kiếm |
| review-service | NestJS 12 / TS 6 | 5010 | | đánh giá (chỉ khách đã mua), điểm trung bình |
| notification-service | NestJS 12 / TS 6 | 5011 | | in-app, email, SMS, web push, FCM; subscription |
| message-service | NestJS 12 / TS 6 | 5012 | | chat khách – người bán/CSKH; subscription |
| storefront-bff | NestJS 12 / TS 6 | 5020 | | Product, giỏ hàng, đặt hàng cho web/app |
| backoffice-bff | NestJS 12 / TS 6 | 5021 | | sản phẩm + tồn kho, điều chỉnh kho, hỗ trợ khách |
| graphql-router / -backoffice | Cosmo Router | 4000 / 4001 | | supergraph storefront / backoffice |

Service NestJS chỉ có API GraphQL (qua router) và gRPC nội bộ; xem [ADR 0011](docs/adr/0011-nestjs-graphql-federation.md).

Service Python (FastAPI/Django, quản lý bằng uv, image chung `services/python.Dockerfile`):

| Service | Công nghệ | HTTP | Ghi chú |
|---|---|---|---|
| translate-service | FastAPI + Claude API | 5017 | dịch sản phẩm/đánh giá, cache Valkey |
| chatbot-service | FastAPI + Claude API / Ollama | 5024 | trợ lý mua sắm, SSE, ngữ cảnh từ search-service |
| supplier-service | Django 6 + DRF | 5019 | gian hàng người bán, duyệt bởi admin |

Đăng nhập do Keycloak đảm nhận; auth-service, service-discovery (Eureka), config-service và common-service
(đã chuyển vào `libs/java/common-core`) đã được gỡ.

## Chạy local

```bash
make up                                   # Postgres+pgvector, Valkey, Kafka, Debezium, Apicurio, Keycloak, APISIX, Unleash, OpenFGA, OTel + app Go
make up PROFILES="observability workflow" # thêm Grafana/Tempo/Loki/Prometheus/Pyroscope, Temporal
make up PROFILES="apps-java apps-node"   # thêm service Java, NestJS, 2 router GraphQL, Mailpit
make token USER=customer@congthanh.vn PASS=customer
curl -H "Authorization: Bearer $(make -s token USER=customer@congthanh.vn PASS=customer)" localhost:9080/api/ecommerce/carts/customer/<sub>
```

Gateway: http://localhost:9080 (GraphQL: /graphql, /admin/graphql) · Mailpit: http://localhost:8025 · Keycloak: http://localhost:8180 (admin/admin) · Grafana: http://localhost:3000 · Unleash: http://localhost:4242 · Temporal UI: http://localhost:8233

## Node / GraphQL

```bash
npm ci && npm run build                     # proto → shared → platform → clients → service (thứ tự workspace)
npm test                                    # Vitest; đặt TEST_DATABASE_URL để chạy cả test với Postgres thật
npm run lint                                # Biome + dependency-cruiser (luật lục giác)
npm run schema:emit --workspaces --if-present && npm run supergraph   # SDL subgraph → supergraph Cosmo
./tools/scripts/new-node-service.sh <tên> "<mô tả>"                   # tạo service NestJS mới từ khuôn
```

## Quy ước

- **Hexagonal**: `internal/domain` không import hạ tầng (CI kiểm tra); use case trong `internal/app` chỉ biết port.
- **Event**: ghi vào bảng `outbox` cùng transaction, Debezium đẩy sang `ecom.<miền>.events`; consumer idempotent (`processed_events`).
- **Contract trước code**: đổi proto → `make proto`; `buf breaking` chặn thay đổi phá tương thích.
- **Cấu hình sinh tự động** (`make gen`): route APISIX từ `platform/gateway/routes.yaml`, AsyncAPI từ proto event.
- **Không dùng `:latest`**, image ký bằng Cosign; deploy chỉ qua Git (ArgoCD).

## Lộ trình

- [x] Giai đoạn 1: 4 service Java lên Spring Boot 4 / Java 25 / Gradle
- [x] Giai đoạn 2: cart, inventory sang Go
- [x] Đợt 1 (tái cấu trúc): cây thư mục mới, hexagonal cho Go, nền tảng platform/deploy/infra/CI
- [x] Đợt 2: Java sang hexagonal, bỏ Axon → Temporal saga, outbox, Liquibase, Resilience4j, ArchUnit, Jib
- [x] Đợt 3: 5 service NestJS + 2 BFF, GraphQL Federation v2, Cosmo Router, subscription qua Valkey
