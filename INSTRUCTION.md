# INSTRUCTION — CongThanhApp

Hướng dẫn duy nhất của repo: cấu trúc, cách chạy và kiểm tra từng thành phần. Phần lớn nội dung là nền tảng
e-commerce ở `backend/app/ecommerce` (gọi tắt là **platform**). Mọi lệnh trong mục 2–9 chạy từ thư mục
`backend/app/ecommerce`, trừ khi ghi khác.

- Kiến trúc tổng quan: `backend/app/ecommerce/docs/architecture/overview.md`
- Quyết định kiến trúc (ADR): `backend/app/ecommerce/docs/adr/`

---

## 1. Cấu trúc platform

```
api/          contract: proto (gRPC + event), asyncapi, openapi, graphql — nguồn sự thật duy nhất
libs/
  go/         platform (otel, logging, db, outbox, kafka, featureflag, auth, server, grpcx) + proto (code sinh)
  java/       common-core, proto (code sinh), workflow-contracts (interface Temporal dùng chung)
  ts/         platform (NestJS), shared, clients (gRPC/REST tới service Java/Go), proto-gen (@ecommerce/proto)
  python/     ecommerce-platform (FastAPI: auth, health, lỗi), ecommerce-proto (protobuf event sinh sẵn)
services/     service nghiệp vụ phục vụ request, mỗi service một thư mục, kiến trúc hexagonal (domain / application / adapter)
data/         pipeline dữ liệu: ingestion (clickstream-collector), streaming (Flink stream-processor), lakehouse (Spark/Airflow)
ai/           thành phần cho AI agent: commerce-mcp-service (MCP server)
platform/     cấu hình nền: gateway (APISIX), identity (Keycloak), messaging (Kafka, Debezium, Schema Registry),
              mesh (Istio Ambient), observability (OTel, LGTM, Pyroscope, SLO), policy (Kyverno), authz (OpenFGA),
              workflow (Temporal), secrets (Vault, ESO), featureflags (Unleash), graphql (Cosmo Router), data,
              streaming (Flink), delivery (Argo Rollouts + Kargo), network (Cilium), security (Tetragon),
              inference (KServe + vLLM), chaos (Chaos Mesh định kỳ)
deploy/       helm (chart microservice + values theo service/môi trường), argocd, local (docker compose), legacy
infra/        terraform AWS: network, EKS + Karpenter, Aurora PG, Redis, MSK, Elastic Cloud, S3, Route 53 + ACM,
              WAF + CloudFront, AWS Backup, GuardDuty
tests/        load (k6), chaos (Chaos Mesh), contract, e2e (Hurl)
tools/        script sinh cấu hình, Backstage template, agent
```

Đăng nhập do Keycloak đảm nhận (auth-service, Eureka, config-service, common-service đã gỡ).

## 2. Danh sách service

| Service | Ngôn ngữ | HTTP | gRPC | Vai trò |
|---|---|---|---|---|
| inventory-service | Go 1.26 | 5014 | 9092 | tồn kho, kho, giữ chỗ saga, flash sale (Redis Lua: giành suất, claim có hạn, phòng chờ) |
| cart-service | Go 1.26 | 5002 | 19091 | giỏ hàng; xoá sản phẩm đã đặt khi nhận `OrderPlaced` |
| tax-service | Go 1.26 | 5022 | 9096 | thuế suất theo vùng, tính thuế từng dòng |
| search-service | Go 1.26 | 5013 | 9098 | tìm kiếm, gợi ý từ khoá (Elasticsearch), cập nhật từ `ecommerce.product.events` |
| shipping-service | Go 1.26 | 5016 | 9097 | phí giao hàng, vận đơn, hành trình |
| media-service | Go 1.26 | 5007 | | upload S3/R2 bằng URL ký sẵn |
| webhook-service | Go 1.26 | 5023 | | webhook bên thứ ba: HMAC, thử lại có backoff, chặn SSRF |
| pricing-service | Go 1.26 | 5025 | 9099 | bảng giá theo nhóm khách, lịch giảm giá, lịch sử giá (giá thấp nhất 30 ngày) |
| clickstream-collector | Go 1.26 | 5029 | | thu thập view/search/add-to-cart từ web/app vào Kafka cho data lakehouse (mục 12) |
| product-service | Java 25 / Spring Boot 4 | 5008 | 29090 | sản phẩm |
| checkout-service | Java 25 / Spring Boot 4 | 5027 | 19095 | phiên checkout, báo giá, kiểm tra gian lận, chạy saga Temporal (queue `checkout-workflow`) |
| order-service | Java 25 / Spring Boot 4 | 5006 | 19090 | đơn hàng, activity của saga, gRPC `PlaceOrder` |
| payment-service | Java 25 / Spring Boot 4 | 5018 | 9090 | thanh toán: thẻ, Apple Pay, Google Pay (Stripe), PayPal, sandbox |
| promotion-service | Java 25 / Spring Boot 4 | 5009 | 9095 | mã giảm giá |
| stream-processor | Flink 2.2 (Java 21) | 8091 (UI local) | | feature thời gian thực: rủi ro khách 24h, xu hướng 1h, số giờ còn hàng (mục 13) |
| catalog-service | NestJS 12 / TS 6 | 5003 | 19092 | danh mục, thương hiệu, tag, quốc gia |
| customer-service | NestJS 12 / TS 6 | 5004 | 19094 | địa chỉ giao hàng, wishlist, lịch sử tìm kiếm |
| review-service | NestJS 12 / TS 6 | 5010 | | đánh giá (chỉ khách đã mua), điểm trung bình |
| notification-service | NestJS 12 / TS 6 | 5011 | | in-app, email, SMS, web push, FCM |
| message-service | NestJS 12 / TS 6 | 5012 | | chat khách – người bán/CSKH |
| storefront-bff | NestJS 12 / TS 6 | 5020 | | GraphQL cho web/app khách: Product, giỏ, đặt hàng, gợi ý |
| backoffice-bff | NestJS 12 / TS 6 | 5021 | | GraphQL quản trị: sản phẩm, tồn kho, hỗ trợ khách |
| graphql-router / -backoffice | Cosmo Router | 4000 / 4001 | | supergraph storefront / backoffice |
| translate-service | FastAPI + Claude API | 5017 | | dịch sản phẩm/đánh giá, cache Redis |
| chatbot-service | FastAPI + Claude / Ollama / vLLM | 5024 | | trợ lý mua sắm, SSE |
| commerce-mcp-service | MCP (Python SDK `mcp` 2.x) | 5030 | | AI agent của khách tìm sản phẩm, quản lý giỏ, xem/huỷ đơn (mục 13) |
| supplier-service | Django 6 + DRF | 5019 | | gian hàng người bán, duyệt bởi admin |
| fraud-service | FastAPI + asyncpg + Redis | 5026 | | chấm điểm rủi ro, hàng chờ duyệt tay, danh sách chặn |
| recommendation-service | FastAPI + pgvector | 5028 | | mua kèm, tương tự, xu hướng, gợi ý cho riêng khách |

Thư mục: clickstream-collector ở `data/ingestion/`, stream-processor ở `data/streaming/`, commerce-mcp-service ở `ai/`;
service còn lại ở `services/`. Service NestJS chỉ có GraphQL (qua router) và gRPC nội bộ. Mỗi service có `.env.example` (nếu cần cấu hình);
copy thành `.env` khi chạy ngoài Docker.

## 3. Chạy toàn bộ stack local (Docker Compose)

Yêu cầu: Docker, `make`. File compose: `deploy/local/docker-compose.yaml`.

```bash
make up                                                     # lõi: Postgres+pgvector, Redis, Kafka, Debezium, Apicurio,
                                                            #      Keycloak, APISIX, Unleash, OpenFGA, OTel + cart, inventory
make up PROFILES="observability workflow"                   # + Grafana/Tempo/Loki/Prometheus/Pyroscope, Temporal
make up PROFILES="apps-go"                                  # + tax, shipping, webhook, search, media, pricing
make up PROFILES="workflow apps-java"                       # + service Java (cần Temporal)
make up PROFILES="apps-node"                                # + NestJS, 2 router GraphQL, Mailpit (cần build supergraph trước, mục 6)
make up PROFILES="apps-python"                              # + translate, chatbot, supplier, fraud, recommendation, commerce-mcp
make up PROFILES="streaming"                                # + Flink stream-processor (UI http://localhost:8091)
make up PROFILES="workflow apps-go apps-java apps-python"   # luồng checkout đầy đủ
make up PROFILES="ollama"                                   # model tự host (chatbot/recommendation không cần Claude API)
make logs | make ps | make down
```

- apps-python cần `ANTHROPIC_API_KEY` trong môi trường (hoặc `CHATBOT_LLM_PROVIDER=ollama` + profile `ollama`,
  lần đầu: `docker compose exec ollama ollama pull mistral`).
- Connector Debezium (outbox) tự đăng ký bởi các container `connect-init*`.

Địa chỉ: Gateway http://localhost:9080 (GraphQL `/graphql`, `/admin/graphql`) · Keycloak http://localhost:8180
(admin/admin) · Mailpit http://localhost:8025 · Grafana http://localhost:3000 · Unleash http://localhost:4242 ·
Temporal UI http://localhost:8233 · MinIO console http://localhost:9001.

Lấy token và gọi API:

```bash
make token USER=customer@congthanh.vn PASS=customer
curl -H "Authorization: Bearer $(make -s token USER=customer@congthanh.vn PASS=customer)" \
  localhost:9080/api/ecommerce/carts/customer/<sub>
```

## 4. Service Go

Go 1.26, workspace `go.work`. Test tích hợp cần Postgres/Redis (`make up`).

```bash
cd services/<svc> && go run ./cmd/server          # cấu hình qua biến môi trường (.env.example)
make test-go                                      # vet + test mọi module trong GO_MODULES
make lint                                         # golangci-lint + Biome/dependency-cruiser
TEST_DATABASE_URL=postgres://postgres:postgres@localhost:5432/ecommerce?sslmode=disable \
TEST_REDIS_URL=redis://localhost:6379/15 go test ./...
```

Image: `ko` (CI) hoặc `docker build -f services/<svc>/Dockerfile .`.
`domain` không được import hạ tầng; use case trong `app` chỉ biết port.

## 5. Service Java

Java 25, Spring Boot 4, Gradle (Liquibase cho migration, Temporal cho saga, ArchUnit kiểm luật lục giác).

```bash
./gradlew :services:<svc>:bootRun                 # cấu hình qua biến môi trường trong application.yaml
./gradlew test                                    # hoặc make test-java
./gradlew :services:<svc>:jibDockerBuild          # image local không cần Dockerfile (CI dùng :jib để push)
```

- checkout-service chạy worker workflow `CheckoutWorkflow`; activity đơn hàng chạy ở order-service, thanh toán ở
  payment-service. Interface dùng chung nằm trong `libs/java/workflow-contracts`.
- Chạy service Java ngoài K8s có thể gắn OTel Java agent ở `tools/agents/` (file .jar không commit).
- Thị trường mặc định là Mỹ: tiền USD, giờ America/New_York, thuế bán hàng theo bang, phí ship tính bằng cent
  (ADR 0015). Phương thức thanh toán: `CARD`, `APPLE_PAY`, `GOOGLE_PAY` (Stripe Payment Element, frontend gửi
  ConfirmationToken `ctoken_...`), `PAYPAL` (bắt buộc `returnUrl`). payment-service bật
  cổng nào có khoá: `STRIPE_API_KEY` + `STRIPE_WEBHOOK_SECRET`, `PAYPAL_CLIENT_ID` + `PAYPAL_CLIENT_SECRET`
  (mặc định sandbox PayPal). Không có khoá thì đặt `PAYMENT_SANDBOX_ENABLED=true`: token `tok_decline` bị từ chối,
  PayPal hoặc `tok_redirect` đi qua trang sandbox. Stripe webhook local: `stripe listen --forward-to
  localhost:9080/api/ecommerce/payment/webhooks/stripe`.

## 6. Service Node (NestJS) và GraphQL

Node 24, npm workspaces.

```bash
npm ci && npm run build                     # proto → shared → platform → clients → service
npm test                                    # Vitest; đặt TEST_DATABASE_URL để chạy cả test tích hợp Postgres
npm run lint                                # Biome + dependency-cruiser (luật lục giác)
npx vitest run services/<svc>               # test một service
./tools/scripts/new-node-service.sh <tên> "<mô tả>"   # tạo service NestJS mới từ khuôn
```

Chạy một service (ví dụ catalog):

```bash
npm run build -w @ecommerce/proto -w @ecommerce/shared -w @ecommerce/platform -w @ecommerce/catalog-service
MIGRATE_ON_START=true DATABASE_URL=postgres://postgres:postgres@localhost:5432/ecommerce \
  LOG_FORMAT=pretty npm start -w @ecommerce/catalog-service
```

Đổi schema DB (Drizzle): sửa `src/adapters/out/persistence/schema.ts` → `npm run db:generate -w @ecommerce/<svc>`
→ commit cả `drizzle/`. Production chạy `node dist/migrate.js` ở init container, không bật `MIGRATE_ON_START`.

**GraphQL supergraph.** Mỗi service NestJS là subgraph Federation v2 và tự ghi SDL ra `services/<svc>/schema.graphql`.

| Graph | Subgraph | Router | Qua APISIX |
|---|---|---|---|
| storefront | storefront-bff, catalog, customer, review, notification, message | graphql-router :4000 | `/graphql` |
| backoffice | backoffice-bff, catalog, review, notification, message | graphql-router-backoffice :4001 | `/admin/graphql` |

```bash
npm run schema:emit --workspaces --if-present && npm run supergraph   # → platform/graphql/<graph>/config.json + supergraph.graphql
npm run supergraph:check                                              # CI: compose được và supergraph.graphql khớp
```

Review diff `supergraph.graphql` để phát hiện thay đổi phá tương thích; muốn bỏ trường thì `@deprecated` trước.

**Cấu hình kênh notification-service:** `SMTP_URL`/`MAIL_FROM` (local: Mailpit `smtp://localhost:1025`),
`SMS_WEBHOOK_URL`/`SMS_API_KEY` (trống = chỉ ghi log), `VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY`
(`npx web-push generate-vapid-keys`), `FCM_SERVICE_ACCOUNT_JSON`, `KEYCLOAK_CLIENT_SECRET`.
Kênh thiếu cấu hình thì lượt gửi bị `SKIPPED`.

**Code TypeScript từ proto** (`@ecommerce/proto`, Protobuf-ES v2): không commit, sinh bằng
`npm run build -w @ecommerce/proto` vào `libs/ts/proto-gen/src/gen/`.

## 7. Service Python

Python 3.13, quản lý bằng `uv`; image chung `services/python.Dockerfile` (`--build-arg SERVICE=<svc>`).

```bash
cd services/<svc>
uv sync --all-groups                        # tạo .venv theo uv.lock
uv run python -m app                        # FastAPI (cổng lấy từ HTTP_PORT); supplier: uv run python manage.py runserver
uv run ruff check . && uv run ruff format --check .
TEST_DATABASE_URL=postgresql://postgres:postgres@localhost:5432/ecommerce \
TEST_REDIS_URL=redis://localhost:6379/15 uv run pytest
```

- fraud-service và recommendation-service tự chạy migration SQL khi khởi động (schema `DB_SCHEMA`).
- recommendation-service cần extension pgvector (`VECTOR_ENABLED=false` để tắt); embedding mặc định `hashing`,
  đổi sang `EMBEDDINGS_PROVIDER=ollama` + `EMBEDDING_MODEL=bge-m3` để có embedding ngữ nghĩa.
- Code protobuf Python (`libs/python/ecommerce-proto`) được commit; sinh lại bằng `make proto-python`.
- commerce-mcp-service bắt buộc có `JWT_JWKS_URL` và `OPENFGA_STORE_ID` (local: `make fga-init` in ra store id và
  model id, đặt `OPENFGA_STORE_ID`/`OPENFGA_MODEL_ID` vào `deploy/local/.env`).
- chatbot-service: `LLM_PROVIDER=vllm` gọi model tự host trên KServe (`VLLM_URL`, `VLLM_PATH`, `VLLM_MODEL`).

## 8. Contract, code sinh và kiểm tra trước khi push

```bash
make proto      # buf lint + buf generate (Go) + sinh Python
make gen        # route APISIX (từ platform/gateway/routes.yaml), AsyncAPI (từ proto event), pipeline Kargo, supergraph
make check      # như CI: buf lint, file sinh còn khớp, supergraph:check
make fga-test   # test model OpenFGA (platform/authz/openfga/tests.fga.yaml), CI chạy ở workflow contracts
```

- Đổi proto → `make proto`; `buf breaking` trong CI chặn thay đổi phá tương thích. Schema Registry (Apicurio, rule
  BACKWARD) chặn ở runtime.
- OpenAPI (`api/openapi/*.yaml`) lint bằng Spectral và so breaking bằng `oasdiff`.
- Event: ghi vào bảng `outbox` cùng transaction, Debezium đẩy sang topic `ecommerce.<miền>.events`; consumer
  idempotent qua `processed_events`. Topic khai báo bằng Strimzi `KafkaTopic` trong `platform/messaging/kafka/`.

## 9. Test ngoài service

| Thư mục | Công cụ | Khi nào chạy |
|---|---|---|
| `tests/load/k6` | k6 | trước đợt sale lớn, hằng tuần trên staging |
| `tests/chaos` | Chaos Mesh | game day thủ công hằng tháng trên staging |
| `platform/chaos` | Chaos Mesh `Schedule` | tự chạy hằng tuần trên staging, kỳ vọng ghi trong annotation |
| `tests/contract` | buf breaking, Spectral, oasdiff | mỗi PR (CI) |
| `tests/e2e` | Hurl | sau mỗi lần deploy (ArgoCD PostSync hook) |

## 10. Hạ tầng và triển khai

**AWS (Terraform)** — `infra/terraform`: modules network, eks (+Karpenter, Pod Identity), rds-postgres (Aurora,
logical replication), elasticache (Redis), msk, elastic-cloud (Elasticsearch qua PrivateLink), s3-cdn, dns
(Route 53 + ACM), waf, edge (CloudFront trước API + Shield Advanced tuỳ chọn), backup (AWS Backup), guardduty;
envs dev, test (rẻ), staging, prod (multi-AZ).

```bash
cd infra/terraform/envs/dev && terraform init && terraform plan
```

Sau khi có cluster: cài ArgoCD rồi `kubectl apply -f deploy/argocd/bootstrap.yaml`; phần còn lại do GitOps
(ApplicationSet đọc mỗi thư mục `deploy/helm/values/<svc>` × mỗi cluster). Thêm service mới = thêm thư mục values.
Trên AWS dùng dịch vụ quản lý; manifest Strimzi/CNPG trong `platform/` dành cho cluster không phải AWS.

**Kho dữ liệu**

| Kho | Dùng cho | Triển khai |
|---|---|---|
| PostgreSQL 18 + pgvector | dữ liệu giao dịch (mỗi service một schema), embedding | RDS / CloudNativePG (`platform/data/postgres-cnpg.yaml`) |
| Redis | cache, rate limit gateway, flash sale | ElastiCache / `platform/data/redis.yaml` |
| Kafka | event giữa service | MSK / Strimzi (`platform/messaging`) |
| ClickHouse | phân tích | `platform/data/clickhouse.yaml` |
| ScyllaDB | lịch sử chat | `platform/data/scylla.yaml` |
| Iceberg trên S3 | data lake, replay/ML | Kafka Connect Iceberg sink; local: Spark + Polaris (mục 12) |
| Elasticsearch 9 | full-text search | Elastic Cloud (PrivateLink) |

**Biên, bảo mật, sao lưu trên AWS** (ADR 0019)

Đường đi của request: Route 53 → CloudFront (WAF, Shield) → VPC origin → NLB nội bộ của APISIX → service.
Ảnh/media đi CloudFront → S3 (`media.<domain>`). Elasticsearch chạy trên Elastic Cloud, nối vào VPC qua PrivateLink.

Lần đầu dựng một env:

1. `export EC_API_KEY=...` (API key của tổ chức trên Elastic Cloud, cho provider `elastic/ec`).
2. Điền `elastic_privatelink_service_name` trong `terraform.tfvars` (tên VPC endpoint service của Elastic cho
   region, lấy trong tài liệu PrivateLink của Elastic Cloud).
3. `terraform apply` với `api_origin_lb_arn = ""`: tạo zone, chứng chỉ, WAF, CDN media, Elastic... nhưng chưa tạo
   distribution cho API. Uỷ quyền NS: copy output `name_servers` vào chỗ quản lý tên miền (env con như
   `dev.congthanhapp.com` thì thêm bản ghi NS ở zone `congthanhapp.com`). ACM chờ DNS xác thực xong mới cấp.
4. Đồng bộ ArgoCD để APISIX tạo NLB nội bộ, lấy ARN (`aws elbv2 describe-load-balancers`), điền vào
   `api_origin_lb_arn`, `terraform apply` lần nữa để tạo distribution `api.<domain>`.
5. Trong Kibana (output `kibana_url`, user `elastic`), tạo API key chỉ có quyền trên index `products`, rồi lưu vào
   Vault: `vault kv put secret/ecommerce/search-service elasticsearch_url=<output elasticsearch_url> elasticsearch_api_key=<key>`.

Lưu ý:
- Shield Standard có sẵn. Shield Advanced ($3000/tháng, cam kết 1 năm) phải tự đăng ký trong console rồi mới
  bật `shield_advanced = true` (chỉ prod).
- WAF giới hạn 3000 request/5 phút mỗi IP, riêng `/flash-sales/` và `/checkout/` là 600. Bot Control tính phí
  theo request, tắt ở dev/test. Log WAF (chỉ request bị chặn/đếm) ở CloudWatch `aws-waf-logs-<name>`.
- AWS Backup: staging giữ 7 ngày; prod giữ 35 ngày, bản tháng 365 ngày, vault lock, copy sang us-east-1.
- GuardDuty chỉ bật ở prod (theo account + region). Email trong `security_alert_emails` phải bấm xác nhận
  từ thư của SNS.

**Istio Ambient** (mTLS, zero trust, retry/timeout không cần sidecar):

```bash
istioctl install -f platform/mesh/istio/istio-ambient.yaml -y
kubectl apply -k platform/mesh/istio
```

`authorization-policies.yaml` mặc định từ chối, chỉ mở đường gọi có trong kiến trúc — thêm đường gọi mới thì sửa file này.

**Profiling (Pyroscope):** Go bật qua `PYROSCOPE_SERVER_ADDRESS`; Java dùng agent trong `tools/agents`; trên K8s
Grafana Alloy (eBPF) thu profile mọi pod, không cần sửa code.

**Quy ước:** không dùng image `:latest`, image ký bằng Cosign, deploy chỉ qua Git (ArgoCD).

## 11. Frontend ecommerce

Next.js ở `frontend/ecommerce`, gọi GraphQL qua gateway.

```bash
cd frontend/ecommerce
npm install
npm run dev        # http://localhost:3000
```

## 12. Data lakehouse

Kafka → Spark → Iceberg (MinIO) ← Trino, điều phối bằng Airflow. Kiến trúc và lý do chọn:
`backend/app/ecommerce/docs/adr/0013-data-lakehouse.md`. Lệnh chạy từ `backend/app/ecommerce`.

```
nguồn                         Kafka topic                    Iceberg (catalog "lakehouse" trong Polaris)
outbox (Debezium)        →  ecommerce.<miền>.events  ─┐
CDC bảng nghiệp vụ       →  ods.<schema>.<bảng>      ─┼─ lake-stream (Spark, 30s) → bronze.*  (thô, append)
(connector ods-cdc)                                   │                           → ods.*     (MERGE, trạng thái hiện tại)
clickstream-collector    →  clickstream.events       ─┘
                                                      Airflow hằng giờ: silver.* → kiểm tra → gold.*
```

| Tầng | Bảng | Cập nhật |
|---|---|---|
| bronze | `outbox_events`, `cdc_changes`, `clickstream_events` | streaming, chỉ append, giữ mọi bản ghi |
| ods | `orders`, `order_lines`, `payments`, `products` | streaming MERGE theo khoá + LSN, trễ ~30 giây so với Postgres |
| silver | `orders`, `order_lines`, `payments`, `products`, `outbox_events`, `clickstream_events` | batch: chuẩn kiểu, khử trùng lặp, bỏ PII, lọc bot |
| gold | `daily_sales`, `product_daily_sales`, `funnel_daily`, `customer_features` | batch sau silver |

**Chạy** (file compose `deploy/local/docker-compose.lakehouse.yaml` ghép vào compose chính):

```bash
make lakehouse-up                                        # lõi + MinIO, Polaris, Spark, Trino, clickstream-collector
make lakehouse-up PROFILES="workflow apps-java"          # + service Java để có dữ liệu ODS (orders, payments, products)
make lakehouse-up LAKEHOUSE_PROFILES="lakehouse airflow" # + Airflow (thêm ~1.5 GB RAM)
make lakehouse-simulate SESSIONS=300                     # gửi clickstream giả qua collector
make lakehouse-batch                                     # chạy silver → kiểm tra → gold ngay (không cần Airflow)
make lakehouse-sql                                       # Trino CLI: SELECT * FROM gold.daily_sales;
make lakehouse-cdc                                       # đăng ký lại connector ods-cdc sau khi bật apps-java
make lakehouse-stop                                      # dừng riêng phần lakehouse
```

Địa chỉ: Trino http://localhost:8088 (JDBC `jdbc:trino://localhost:8088/lakehouse`, user bất kỳ) · Spark UI streaming
http://localhost:4041, Spark Connect `sc://localhost:15002` (UI http://localhost:4042) · Polaris http://localhost:8181 (client
`root` / `s3cr3t`, chỉ local) · Airflow http://localhost:8090 · MinIO console http://localhost:9001 (bucket `lakehouse`) ·
clickstream-collector http://localhost:5029.

| Thư mục / file | Nội dung |
|---|---|
| `data/lakehouse/jobs/stream_ingest.py` | job streaming Kafka → bronze + ods (container `lake-stream`) |
| `data/lakehouse/jobs/ods_tables.py` | bảng ODS: nguồn, khoá, kiểu cột — thêm bảng mới ở đây và trong `ods-cdc.json` |
| `data/lakehouse/jobs/silver.py`, `checks.py`, `gold.py`, `maintenance.py` | job batch (chạy qua Spark Connect hoặc spark-submit) |
| `data/lakehouse/airflow/dags/lakehouse.py` | DAG `lakehouse_silver_gold` (hằng giờ), `lakehouse_maintenance` (hằng ngày) |
| `data/lakehouse/sql/trino-examples.sql` | truy vấn mẫu, time travel, xem file nhỏ |
| `data/lakehouse/tools/simulate_clickstream.py` | sinh clickstream giả |
| `deploy/local/lakehouse/` | Dockerfile Spark/Airflow, cấu hình Trino, script tạo catalog Polaris và đăng ký connector |
| `platform/messaging/debezium/local/ods-cdc.json` | connector CDC cho ODS (đã loại cột nhạy cảm) |
| `api/jsonschema/clickstream/v1/clickstream-event.json` | schema event clickstream (đăng ký ở Apicurio, group `clickstream`) |
| `data/ingestion/clickstream-collector` | service Go nhận `POST /v1/events` (tối đa 100 event/lô) |

Gửi event từ frontend (`occurred_at` phải lệch không quá 24 giờ so với giờ server, `event_id` là UUID để khử trùng lặp):

```bash
curl -X POST localhost:5029/v1/events -H 'Content-Type: application/json' -d '{"events":[{
  "event_id":"'"$(uuidgen)"'","event_type":"add_to_cart","occurred_at":"'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'",
  "session_id":"s-1","anonymous_id":"a-1","product_id":"p-1","quantity":1,"price":"19.99","currency":"USD"}]}'
```

Lưu ý:
- ODS cần bảng nguồn do service Java tạo (Liquibase). Khi chưa bật `apps-java`, connector `ods-cdc` chưa chạy được;
  bật xong thì `make lakehouse-cdc`.
- Checkpoint streaming nằm ở volume `lake-checkpoints`. Muốn nạp lại từ đầu Kafka: dừng `lake-stream`, xoá volume rồi chạy lại.
- Image Spark tải sẵn jar Iceberg/Kafka lúc build; đổi phiên bản trong `deploy/local/lakehouse/spark/Dockerfile`.

## 13. Streaming, giao hàng theo SLO, bảo mật runtime, agentic commerce

Quyết định và lý do: `backend/app/ecommerce/docs/adr/0014-streaming-delivery-runtime.md`. Lệnh chạy từ `backend/app/ecommerce`.

**Flink stream-processor** (`data/streaming/stream-processor`, proto `api/proto/ecommerce/stream/v1`):

| Pipeline | Đọc | Ghi (topic compacted) | Ai dùng |
|---|---|---|---|
| customer-risk | order/payment events | `ecommerce.stream.customer-risk` | fraud-service (luật `STREAM_*`, Redis `fraud:stream:cust:*`) |
| product-trending | order, cart, customer events | `ecommerce.stream.product-trending` | recommendation-service (live 15 phút, rồi về batch) |
| stock-cover | inventory, order events | `ecommerce.stream.stock-cover` | webhook-service (`inventory.stockout_risk`) |

```bash
./gradlew :data:streaming:stream-processor:test   # unit + MiniCluster
make up PROFILES="streaming"                       # chạy local (standalone job), PIPELINES=all
```

Trên K8s: `FlinkDeployment` ở `platform/streaming/flink/overlays/<env>` (Flink Kubernetes Operator, checkpoint
`s3://congthanhapp-<env>-flink-state`, Terraform module `flink_state`). Nâng job: đổi image, operator tự lấy savepoint.

**Giao hàng**: chart `microservice` dùng Argo Rollouts + Gateway API (`rollout.trafficRouting: gatewayAPI`) và phân
tích nền `slo-burn-rate` (`platform/delivery/argo-rollouts`). Kargo (`platform/delivery/kargo`, sinh bằng
`python3 tools/scripts/gen-kargo.py`) theo dõi image `<sha12>` của mọi service: dev và staging tự promote, prod mở PR.
Thêm service mới chỉ cần thư mục `deploy/helm/values/<svc>` rồi `make gen`.

**Supply chain và runtime**: CI gắn provenance SLSA cho mọi image; Kyverno (`platform/policy/kyverno`) chặn image thiếu
chữ ký, SBOM hoặc provenance. Renovate (`.github/renovate.json5`) lo manifest Argo CD/Helm/Kustomize. Tetragon
(`platform/security/tetragon`) và Cilium + Hubble (`platform/network/cilium`) đang ở chế độ audit: xem
`hubble observe -n ecommerce --verdict AUDIT` trước khi bật enforce. Alloy đo RED bằng eBPF (Beyla) cho service
chưa có OTel.

**MCP cho AI agent** (`ai/commerce-mcp-service`): endpoint `https://api.congthanh.vn/api/ecommerce/mcp`,
metadata OAuth ở `/.well-known/oauth-protected-resource/api/ecommerce/mcp`, client Keycloak `commerce-mcp-agent`
(public, PKCE, có consent). Tool: `search_products`, `get_product`, `view_cart`, `add_to_cart`, `update_cart_item`,
`list_orders`, `get_order`, `cancel_order` (cần `confirm=true`). Thử local:

```bash
make fga-init                                     # tạo store OpenFGA, đặt id vào deploy/local/.env
make up PROFILES="apps-go workflow apps-java apps-python"
TOKEN=$(curl -s -d client_id=dev-cli -d grant_type=password -d username=customer@congthanh.vn -d password=customer \
  -d 'scope=commerce:read commerce:cart' localhost:8180/realms/ecommerce/protocol/openid-connect/token | jq -r .access_token)
npx @modelcontextprotocol/inspector             # transport Streamable HTTP, URL http://localhost:5030/api/ecommerce/mcp, header Bearer $TOKEN
```

**Model tự host**: KServe (`deploy/argocd/platform/operators.yaml`) + `platform/inference` (InferenceService
`shop-assistant-llm`, Qwen3-8B qua vLLM, node GPU g6 do Karpenter cấp) chỉ ở staging/prod.

**GraalVM native** (5 service Spring, convention `congthanh.spring-native`):

```bash
./gradlew processAot                              # kiểm Spring AOT, không cần GraalVM (CI chạy mỗi PR)
./gradlew :services:<svc>:bootBuildImage          # image <tag>-native qua Paketo (cần Docker)
```

Dùng image native: trong values đặt `runtime: java-native` và `image.tag: <sha12>-native`.

**Backup, chaos, SLO**: Velero (`deploy/argocd/applicationsets/backup.yaml`) backup cả cluster hằng ngày và
namespace có PV mỗi 6 giờ vào `s3://congthanhapp-<env>-velero`; khôi phục: `velero restore create --from-backup <tên>`.
Chaos định kỳ ở `platform/chaos` (staging). SLO khai báo ở `platform/observability/slo/slos.yaml` (Pyrra, UI
`pyrra-api.observability:9099`); chạy local vẫn dùng `platform/observability/prometheus/slo-rules.yaml`.

## 14. Pay later (trả góp)

Quyết định và lý do: `backend/app/ecommerce/docs/adr/0016-pay-later-installments.md` (Proposed; code đã có, mặc định tắt).
Trả góp đi qua đối tác: Affirm, Klarna, Afterpay qua Stripe (method `AFFIRM`, `KLARNA`, `AFTERPAY`) và Pay Later bên
trong luồng `PAYPAL`. Shop không tự cho vay; tín dụng nội bộ sau này là một `PaymentProvider` mới gọi `credit-service`.

- Bật từng partner trong Stripe Dashboard (test mode trước) rồi đặt biến môi trường của payment-service
  `PAYMENT_AFFIRM_ENABLED`, `PAYMENT_KLARNA_ENABLED`, `PAYMENT_AFTERPAY_ENABLED=true`; hạn mức số tiền và
  `authorization-ttl` ở `payment.installments.<partner>` trong `application.yaml`.
- Với sandbox (`PAYMENT_SANDBOX_ENABLED=true`), AFFIRM/KLARNA/AFTERPAY đi qua trang sandbox như PayPal (vẫn phải bật
  partner như trên). Afterpay cần `shipping` trong ConfirmationToken.
- Checkout bằng BNPL bắt buộc có `returnUrl` (khách được redirect sang trang đối tác rồi quay về).
- Frontend hiển thị câu "trả góp" bằng Stripe Payment Method Messaging Element và PayPal Pay Later messages, không tự
  ghi số tiền mỗi kỳ hay APR (quy định quảng cáo Reg Z). `GET /api/ecommerce/payment/installment-options?amount=&currency=`
  cho biết partner nào hợp lệ với số tiền.

## 15. Flash sale

Thiết kế ở ADR 0007 và 0018; cách vận hành một đợt sale ở `docs/runbooks/flash-sale.md`.

Luồng của khách (mọi route qua APISIX, cần đăng nhập):

1. `POST /api/ecommerce/flash-sales/{sale}/products/{product}/queue` xếp hàng vào phòng chờ, rồi
   `GET .../queue` theo `retryAfterSeconds` tới khi `canClaim = true`. Sale không bật phòng chờ thì `canClaim`
   đúng ngay khi tới giờ mở.
2. `POST .../claim` với `{"quantity": 1}` trả `claimId`, `checkoutSessionId` (cùng giá trị) và `expiresAt`
   (mặc định 10 phút, `FLASH_CLAIM_TTL` ở inventory-service).
3. `GET /api/ecommerce/checkout/sessions/{checkoutSessionId}`: phiên được checkout-service mở sẵn khi nhận
   `FlashSaleClaimed` qua Kafka, nên có thể 404 trong vài trăm mili giây đầu. Phiên chỉ chứa sản phẩm đã giành
   (đổi sản phẩm trả `409 FLASH_SALE_ITEMS_LOCKED`); thêm địa chỉ bằng `PATCH`, đặt đơn bằng `POST .../place` như
   đơn thường. Quá `expiresAt` mà chưa đặt thì suất được trả cho người khác.

Việc cần biết khi sửa code:

- Order id của đơn flash sale chính là claim id. inventory-service dựa vào đó để biến
  `ReserveInventory`/`CommitReservation`/`ReleaseInventory` của saga thành xác nhận/chốt/trả claim trên Redis
  (`internal/app/flashsale.go`); order-service không có nhánh riêng cho flash sale.
- Mọi thay đổi trạng thái sale là Lua trong `services/inventory-service/internal/adapter/repository/redis/lua/`
  (`claim`, `transition`, `release`, `sweep`, `queue`, `drain`); các script dùng chung thứ tự KEYS.
- Mở/đóng sale (`.../start`, `.../end`) cần role `ADMIN`. `start` nhận thêm `opensAt`, `admitPerSecond`,
  `admitBurst`; `end` trả 409 khi còn claim chưa chốt.
- Đặt đơn quá tải trả `503 CHECKOUT_BUSY` + `Retry-After`; client gửi lại với cùng `Idempotency-Key`
  (`CHECKOUT_PLACE_MAX_CONCURRENT`, mặc định 16 mỗi pod).
- Scale trước theo lịch: thêm khung giờ vào `deploy/helm/events/events-<env>.yaml`, số pod mỗi service ở
  `scheduledScaling.replicas` trong `values-<env>.yaml`.
- Test: `go test ./internal/integration/...` ở inventory-service (cần `TEST_DATABASE_URL` và `TEST_REDIS_URL`),
  load test `tests/load/k6/flash-sale.js`.
