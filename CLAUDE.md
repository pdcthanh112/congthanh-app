# CLAUDE.md — CongThanhApp

Monorepo nhiều mảng. Trọng tâm là platform thương mại điện tử `backend/app/ecommerce` (khoảng 35 service Go, Java,
NestJS, Python) và storefront `frontend/ecommerce` (Next.js). Các thư mục khác (`ai/`, `machine-learning/`,
`data-analysis/`, `blockchain/`, `mobile/`, `desktop/`, `frontend/management`, `frontend/microfrontend`) là dự án
thử nghiệm độc lập, không có trong CI; khi làm ở đó thì theo README của thư mục đó. `Dockerfile` và
`docker-compose.yml` ở gốc là đồ cũ, không dùng. Tài liệu đầy đủ: `INSTRUCTION.md` (rất dài, tra theo mục lục,
đừng đọc hết). Trả lời người dùng bằng tiếng Việt.

## Rule code style
Viết code theo `.claude/rules/`: `common.md` (mọi ngôn ngữ), `java.md`, `go.md`, `typescript-backend.md`,
`frontend.md`, `python.md`, `sql.md` (migration, SQL), `api-contracts.md` (proto, event, REST, GraphQL, gRPC). Mỗi
file trừ `common.md` chỉ nạp khi làm việc với file khớp `paths` của nó. Formatter và linter
(Biome, Prettier, ESLint, gofmt, golangci-lint, Ruff) là luật cuối cùng cho định dạng; `.cursor/rules.md` đã cũ, không theo.

## Bố cục platform (`backend/app/ecommerce`)
- `api/` là nguồn sự thật cho contract: `proto/ecommerce/<miền>/v1` (gRPC + event), `openapi/`, `graphql/`.
- `libs/{go,java,ts,python}`: thư viện nền và code sinh từ proto. Generator id Snowflake, outbox, Kafka consumer,
  auth nằm ở đây; không viết lại trong từng service.
- `services/<tên>-service`: một service một thư mục. `platform/` là cấu hình hạ tầng khai báo (Kafka topic, Debezium,
  authz OpenFGA, mesh, GraphQL supergraph, Kargo). `deploy/` (local compose, Helm, ArgoCD), `infra/terraform`,
  `data/` (lakehouse, Flink), `tests/` (e2e Hurl, k6, chaos), `docs/adr`, `docs/runbooks`.

## Luật lục giác (mọi ngôn ngữ, ADR 0002)
- `domain` thuần: không import framework, DB, Kafka. `application` (Go: `app`) chỉ biết port (interface).
  `adapter` hiện thực port; adapter vào không gọi thẳng adapter ra. Service không import code của service khác,
  chỉ gọi qua gRPC/HTTP client hoặc event.
- Khung theo ngôn ngữ: Go `cmd/server` + `internal/{config,domain,app,adapter}` + `migrations/`; Java
  `domain/`, `application/{port/in,port/out,service}`, `adapter/{in,out}/...`, `config/`; NestJS
  `src/{domain,application,adapters/{in,out}}`; Python `app/{domain,ports.py,service.py,adapters}`. Theo đúng
  service mẫu: inventory (Go), checkout (Java), catalog (NestJS), fraud (Python).
- ArchUnit (Java) và dependency-cruiser (TS) kiểm luật này; Go và Python chỉ có review, nên tự giữ.

## Dữ liệu và migration
- Database per service: một Postgres, mỗi service một schema riêng (`DB_SCHEMA`; Go/Python dùng tên ngắn như
  `cart`, Java/Node dùng `<tên>_service`). Không join hay đọc chéo schema; cần dữ liệu service khác thì gọi API hoặc
  dựng bản sao từ event.
- Ràng buộc toàn vẹn (FK, UNIQUE, CHECK, NOT NULL) đặt ở DB trong migration, không chỉ ở code (ví dụ ADR 0030).
  Validate ở domain là thêm, không thay thế.
- Công cụ migration: Go goose (`migrations/NNNNN_*.sql`, phải có `-- +goose Up` và `-- +goose Down`), Java Liquibase
  (`src/main/resources/db/changelog/changes/NNNN-*.sql`), NestJS Drizzle (`drizzle/NNNN_*.sql`), Python SQL đánh số
  trong `app/migrations/`, supplier là Django.
- Mặc định thêm migration mới, không sửa migration cũ. Ngoại lệ: chưa có môi trường nào giữ dữ liệu thật, và người
  dùng đã chọn sửa thẳng file gốc ở ADR 0028 và 0030. Chỉ sửa file gốc khi người dùng bảo, rồi nhắc họ reset schema
  local. Không xoá hay viết lại file seed (`*seed*`) khi chưa hỏi.
- Id thực thể là Snowflake BIGINT (ADR 0028). Mọi khoản tiền mang currency của giao dịch, không bao giờ mặc định USD;
  thiếu currency thì báo lỗi (ADR 0029).

## Event
- Ghi vào bảng `outbox` trong cùng transaction với thay đổi dữ liệu; Debezium đẩy sang topic
  `ecommerce.<miền>.events`, DLQ là `<topic>.dlq`. Không publish Kafka trực tiếp từ use case.
- Payload là Protobuf trong `api/proto/ecommerce/<miền>/v1/*_events.proto`; chỉ thêm trường, bỏ trường thì
  `reserved`/deprecated trước (`buf breaking` chặn).
- Consumer phải idempotent qua `processed_events` (khoá `(event_id, consumer)`). Topic mới khai trong
  `platform/messaging/kafka/topics.yaml`, kèm topic DLQ.
- Saga checkout (Temporal): đổi `CheckoutWorkflowImpl` phải bọc `Workflow.getVersion`; activity idempotent theo `orderId`.

## File sinh: không sửa tay
`libs/go/proto`, `libs/python/ecommerce-proto/src`, `libs/ts/proto-gen/src/gen`, `api/asyncapi`,
`services/api-gateway/{apisix/apisix.yaml,k8s/routes.yaml}`, `platform/graphql/*/{config.json,supergraph.graphql}`,
`platform/delivery/kargo/pipeline.yaml`, `services/*/schema.graphql`. Đổi nguồn rồi chạy lệnh sinh (bảng dưới).

## ADR
Quyết định kiến trúc mới thì viết `docs/adr/NNNN-tieu-de.md` (Bối cảnh, Quyết định, Hệ quả) và thêm dòng vào
`docs/adr/README.md`. Không sửa ADR đã Accepted; viết ADR mới và đánh dấu cũ là Superseded. Trước khi đổi một miền,
đọc ADR liên quan.

## Trước khi báo xong
Chạy trong `backend/app/ecommerce`, chỉ cho phần mình đổi. Lệnh cần Docker (integration test, `make up`) thì chỉ chạy
khi người dùng bảo; nếu không chạy được thì nói rõ đã chạy gì và bỏ qua gì.

| Đổi | Chạy |
|---|---|
| `api/proto/**` | `make proto` rồi `make gen` (macOS: `generate.sh` của Python hỏng vì `mapfile`, chạy protoc tay) |
| route gateway, Helm values, GraphQL | `make gen`, rồi `make check` |
| Go service | `go vet ./... && go test ./...` và `golangci-lint run ./...` trong thư mục service |
| Java service | `./gradlew :services:<svc>:test` (gồm ArchUnit); `:integrationTest` cần Docker |
| NestJS/BFF/`libs/ts` | `npm run typecheck && npm run lint && npx vitest run services/<svc>` |
| Python service | `uv run ruff check . && uv run ruff format --check . && uv run pytest -q` |
| `platform/authz` | `make fga-test` |
| `frontend/ecommerce` | `npm run check-types && npm run lint && npm test` |

Không commit, không push, không chạy stack local trừ khi người dùng yêu cầu.
