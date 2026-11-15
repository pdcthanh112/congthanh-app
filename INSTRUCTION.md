# INSTRUCTION — CongThanhApp

Tài liệu hướng dẫn duy nhất của repo. Đọc từ trên xuống nếu mới vào dự án; tra cứu theo mục lục nếu đã quen.
Mọi mô tả ở đây được viết theo code thật trong repo (đường dẫn file được ghi kèm để kiểm chứng). Lý do chi tiết
của từng quyết định nằm ở các ADR trong `backend/app/ecommerce/docs/adr/`.

> Quy ước: trừ khi ghi khác, mọi lệnh chạy từ thư mục `backend/app/ecommerce` (gọi tắt là **platform**).

## Mục lục

1. [Tổng quan dự án](#1-tổng-quan-dự-án)
2. [Bản đồ repo (cấp gốc)](#2-bản-đồ-repo-cấp-gốc)
3. [Kiến trúc tổng thể của platform](#3-kiến-trúc-tổng-thể-của-platform)
4. [Công nghệ và lý do chọn](#4-công-nghệ-và-lý-do-chọn)
5. [Cấu trúc thư mục platform (chi tiết từng file)](#5-cấu-trúc-thư-mục-platform-chi-tiết-từng-file)
6. [Danh sách service](#6-danh-sách-service)
7. [Luồng đi của hệ thống](#7-luồng-đi-của-hệ-thống)
8. [Business logic quan trọng (kèm sơ đồ)](#8-business-logic-quan-trọng-kèm-sơ-đồ)
9. [Cách chạy dự án](#9-cách-chạy-dự-án)
10. [Phát triển từng loại service](#10-phát-triển-từng-loại-service)
11. [Contract, code sinh, kiểm tra trước khi push](#11-contract-code-sinh-kiểm-tra-trước-khi-push)
12. [Data lakehouse](#12-data-lakehouse)
13. [Streaming, giao hàng theo SLO, bảo mật runtime, agentic commerce](#13-streaming-giao-hàng-theo-slo-bảo-mật-runtime-agentic-commerce)
14. [Pay later (trả góp)](#14-pay-later-trả-góp)
15. [Flash sale (vận hành)](#15-flash-sale-vận-hành)
16. [Hạ tầng và triển khai (AWS, Kubernetes, GitOps)](#16-hạ-tầng-và-triển-khai-aws-kubernetes-gitops)
17. [Test, CI/CD](#17-test-cicd)
18. [Xử lý sự cố thường gặp](#18-xử-lý-sự-cố-thường-gặp)
19. [Quy ước khi đóng góp](#19-quy-ước-khi-đóng-góp)

- [Phụ lục A. Cây thư mục đầy đủ của platform](#phụ-lục-a-cây-thư-mục-đầy-đủ-của-platform)

---

## 1. Tổng quan dự án

CongThanhApp là một monorepo cá nhân gom nhiều mảng (backend, frontend, mobile, desktop, AI, ML, data, blockchain).
Phần trọng tâm và được đầu tư nhiều nhất là **nền tảng thương mại điện tử** ở `backend/app/ecommerce`: khoảng 30
service microservice viết bằng 4 ngôn ngữ (Go, Java, TypeScript, Python), giao tiếp bằng gRPC + Kafka, có checkout
saga bằng Temporal, flash sale chống bán lố bằng Redis Lua, trả góp (pay later) qua đối tác, data lakehouse
(CDC → Iceberg → Spark/Trino/Airflow), stream processing bằng Flink và một MCP server cho AI agent.

Thị trường mặc định là Mỹ (USD, `America/New_York`, thuế bán hàng theo bang, Stripe + PayPal) theo ADR 0015.

| Nghiệp vụ chính                       | Service chịu trách nhiệm                                                         |
| ------------------------------------- | -------------------------------------------------------------------------------- |
| Duyệt sản phẩm, danh mục, tìm kiếm    | product, catalog, search, pricing, review, recommendation                        |
| Giỏ hàng, checkout, đặt đơn           | cart, checkout, order, promotion, shipping, tax, fraud                           |
| Thanh toán (thẻ, ví, PayPal, trả góp) | payment                                                                          |
| Đổi trả, sổ cái, quyền riêng tư       | returns, ledger, privacy (ADR 0024)                                              |
| Store credit, chargeback, đối soát    | stored-value, dispute, reconciliation (ADR 0025)                                 |
| Điểm thưởng, mua định kỳ, kiểm duyệt  | loyalty, subscription, moderation (ADR 0026)                                     |

Cách các service mới nối vào checkout, review/message và luồng xoá dữ liệu: ADR 0027. Runbook xoá dữ liệu trong lakehouse: `backend/app/ecommerce/docs/runbooks/privacy-erasure-lakehouse.md`.
| Tồn kho, kho, flash sale              | inventory                                                                        |
| Khách hàng, thông báo, chat           | customer, notification, message                                                  |
| Người bán, media, webhook đối tác     | supplier, media, webhook                                                         |
| AI                                    | chatbot, translate, commerce-mcp                                                 |
| Dữ liệu                               | clickstream-collector, stream-processor (Flink), lakehouse (Spark/Trino/Airflow) |

---

## 2. Bản đồ repo (cấp gốc)

```
congthanh-app/
├── INSTRUCTION.md           tài liệu này
├── README.md                giới thiệu cũ (danh sách công nghệ đời đầu, đã lỗi thời so với code hiện tại)
├── Dockerfile               Dockerfile cũ đời đầu (Spring + Node), ngkhông dùng cho platform
├── docker-compose.yml       compose cũ đời đầu (Postgres 12, Zookeeper...), không dùng; platform dùng deploy/local
├── LICENSE
├── .github/                 CI/CD GitHub Actions cho cả monorepo (xem mục 17)
│   ├── workflows/           ci-go, ci-java, ci-node, ci-python, contracts, security, release
│   ├── CODEOWNERS, pull_request_template.md, dependabot.yml, renovate.json5
├── backend/app/ecommerce/   ★ PLATFORM thương mại điện tử (mục 3–17)
├── frontend/
│   ├── ecommerce/           ★ storefront Next.js 15 + React 19 (mục 9.6)
│   ├── management/          trang quản trị Angular (SSR, server.ts)
│   └── microfrontend/       thử nghiệm micro-frontend: app-shell, store-app, blog-app
├── mobile/
│   ├── ecommerce/           app Flutter (lib/: features, models, providers, router.dart)
│   └── shipping/            app React Native/Expo cho shipper (App.tsx)
├── desktop/staff/           app desktop cho nhân viên (TypeScript, index.html + main.ts)
├── ai/                      thử nghiệm AI độc lập: chatbot (LangChain, RAG), face-recognition (DeepFace), resume-screening
├── machine-learning/        customer-purchase-prediction (MLOps: Kafka, Flink, Spark, Ray, MLflow), sales-forecast, product-recommend (notebook)
├── data-analysis/           data-crawling, data-preprocessing (notebook), visualazation (app Python + Docker)
├── blockchain/              smart contract Solidity: cryptocurrency-payment, loyalty-points
└── _document/               báo cáo LaTeX (kiến trúc, yêu cầu, quản lý dự án) + hình minh hoạ
```

Các mảng ngoài `backend/app/ecommerce` và `frontend/ecommerce` là dự án thử nghiệm độc lập, mỗi thư mục có
README riêng; chúng không phụ thuộc platform và không có trong CI.

**Vì sao monorepo?** (ADR 0002) Contract (proto, GraphQL, OpenAPI), thư viện nền và cấu hình triển khai thay đổi
cùng nhau với service. Một PR có thể đổi proto + code sinh + service + values Helm, CI kiểm tất cả trong một lần,
không phải đồng bộ phiên bản giữa nhiều repo. CI tách theo ngôn ngữ và lọc theo `paths`, nên đổi một service Go
không làm chạy test Java.

---

## 3. Kiến trúc tổng thể của platform

### 3.1 Sơ đồ thành phần

```mermaid
flowchart TB
  subgraph clients[Client]
    web[Storefront Next.js]
    admin[Backoffice]
    agent[AI agent qua MCP]
  end

  subgraph edge[Biên]
    cf[CloudFront + WAF + Shield<br/>chỉ trên AWS]
    apisix[APISIX gateway<br/>OIDC, rate limit, route]
    kc[Keycloak<br/>realm ecommerce]
  end

  subgraph gql[GraphQL]
    r1[Cosmo Router :4000<br/>supergraph storefront]
    r2[Cosmo Router :4001<br/>supergraph backoffice]
    sbff[storefront-bff]
    bbff[backoffice-bff]
    nest[catalog, customer, review,<br/>notification, message]
  end

  subgraph core[Service nghiệp vụ]
    go[Go: cart, inventory, pricing, tax,<br/>shipping, search, media, webhook]
    java[Java: product, checkout, order,<br/>payment, promotion]
    py[Python: fraud, recommendation, supplier,<br/>translate, chatbot, commerce-mcp]
  end

  subgraph infra[Hạ tầng dữ liệu]
    pg[(PostgreSQL<br/>mỗi service 1 schema<br/>+ outbox)]
    redis[(Redis)]
    mongo[(MongoDB<br/>read model sản phẩm)]
    es[(Elasticsearch)]
    temporal[(Temporal)]
    dbz[Debezium]
    kafka[(Kafka + Apicurio)]
  end

  subgraph data[Dữ liệu phân tích]
    flink[Flink stream-processor]
    lake[Spark → Iceberg ← Trino<br/>Airflow]
  end

  web & admin --> cf --> apisix
  agent --> apisix
  apisix -.OIDC.-> kc
  apisix -->|/graphql| r1
  apisix -->|/admin/graphql| r2
  apisix -->|/api/ecommerce/*| go & java & py
  r1 --> sbff & nest
  r2 --> bbff & nest
  sbff & bbff -->|gRPC/REST, chuyển tiếp token| go & java & py
  java -->|saga| temporal
  go & java & nest & py --> pg
  go --> redis
  java --> mongo
  go --> es
  pg -->|WAL| dbz --> kafka
  kafka --> go & java & nest & py
  kafka --> flink --> kafka
  kafka --> lake
```

### 3.2 Các nguyên tắc kiến trúc

| Nguyên tắc                   | Cách làm trong code                                                                                                                        | ADR              |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | ---------------- |
| Polyglot có chủ đích         | Go cho service nóng/đơn giản, Java cho miền giao dịch phức tạp, NestJS cho GraphQL/CRUD, Python cho ML/AI                                  | 0001             |
| Contract-first               | `api/proto` là nguồn sự thật cho gRPC và event; code sinh vào `libs/*/proto`                                                               | 0002, 0003       |
| Hexagonal (ports & adapters) | `domain` thuần, `app/application` chỉ biết port, `adapter` hiện thực port; kiểm bằng ArchUnit (Java), dependency-cruiser (TS), review (Go) | 0002             |
| Database per service         | Một Postgres, mỗi service một schema riêng (`DB_SCHEMA`), không join chéo schema                                                           | 0002             |
| Event-driven tin cậy         | Transactional Outbox + Debezium, consumer idempotent bằng `processed_events`, DLQ `<topic>.dlq`                                            | 0003, 0021       |
| Saga orchestration           | Temporal `CheckoutWorkflow` thay cho Axon/bảng saga tự viết                                                                                | 0004, 0012, 0021 |
| Defense in depth             | Gateway kiểm token, service kiểm lại JWT, OpenFGA cho quyền theo quan hệ, Istio mTLS                                                       | 0006, 0010       |
| Mọi thứ khai báo trong Git   | Route gateway, topic, connector, supergraph, Helm values, Terraform; ArgoCD/Kargo đồng bộ                                                  | 0009, 0014       |

---

## 4. Công nghệ và lý do chọn

Bảng dưới liệt kê mọi công nghệ có trong platform, vai trò, và vì sao chọn nó thay vì lựa chọn tương đương.
Phiên bản lấy từ `go.work`, `gradle/libs.versions.toml`, `package.json`, `pyproject.toml`, `deploy/local/docker-compose.yaml`.

### 4.1 Ngôn ngữ và framework service

| Công nghệ                                                                                                        | Dùng cho                                                                               | Vì sao chọn (so với lựa chọn khác)                                                                                                                                                                                                                                                                                                    |
| ---------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Go 1.26** (chi v5, pgx v5, franz-go, go-redis v9, goose, grpc-go)                                              | cart, inventory, pricing, tax, shipping, search, media, webhook, clickstream-collector | Độ trễ thấp, RAM ít, binary tĩnh khởi động trong mili giây nên autoscale theo Kafka lag nhanh. So với Java: không có JVM warm-up; so với Node: xử lý song song thật bằng goroutine. `chi` nhẹ, tương thích `net/http`; `franz-go` là client Kafka thuần Go nhanh nhất, không cần librdkafka; `pgx` nhanh hơn `database/sql` + lib/pq. |
| **Java 25 + Spring Boot 4.1** (virtual threads, Liquibase, Spring Data JPA, gRPC Java, Resilience4j)             | product, checkout, order, payment, promotion                                           | Miền giao dịch phức tạp cần hệ sinh thái trưởng thành: Temporal Java SDK đầy đủ nhất, Stripe Java SDK, JPA, Spring Security OAuth2. Virtual threads cho throughput gần reactive mà vẫn viết code tuần tự. Có GraalVM native image opt-in (ADR 0014).                                                                                  |
| **NestJS 12 / TypeScript 6 / Node 24** (Drizzle ORM, Apollo Federation, @confluentinc/kafka-javascript, ioredis) | catalog, customer, review, notification, message, storefront-bff, backoffice-bff       | Code-first GraphQL Federation v2 tốt nhất trong hệ sinh thái Node; cùng ngôn ngữ với frontend nên chia sẻ type. Drizzle thay TypeORM/Prisma: SQL-first, migration là file SQL rõ ràng, không runtime engine riêng. Client Kafka của Confluent thay kafkajs (kafkajs không còn được bảo trì).                                          |
| **Python 3.13 + FastAPI** (asyncpg, aiokafka, redis-py, pgvector)                                                | fraud, recommendation, translate, chatbot                                              | Hệ sinh thái ML/AI (Anthropic SDK, embedding, numpy). FastAPI async + Pydantic: nhanh, tự sinh OpenAPI. asyncpg là driver Postgres async nhanh nhất cho Python.                                                                                                                                                                       |
| **Django 6 + DRF**                                                                                               | supplier-service                                                                       | CRUD nhiều form, cần admin duyệt người bán: Django admin + DRF có sẵn mọi thứ.                                                                                                                                                                                                                                                        |
| **MCP Python SDK 2.x**                                                                                           | commerce-mcp-service                                                                   | Chuẩn mở để AI agent (Claude, IDE) gọi tool; Streamable HTTP stateless nên scale ngang.                                                                                                                                                                                                                                               |
| **Flink 2.2 (Java 21)**                                                                                          | stream-processor                                                                       | Stateful stream processing với cửa sổ thời gian, exactly-once checkpoint, RocksDB state. So với Kafka Streams: tách khỏi service, có operator K8s và savepoint khi nâng cấp; so với Spark Streaming: độ trễ thấp hơn (event-at-a-time thay micro-batch). Java 21 vì image Flink 2.2 chưa có JDK 25.                                   |
| **Spark 4 (PySpark, Structured Streaming, Spark Connect)**                                                       | lakehouse                                                                              | Chuẩn de facto để ghi Iceberg (MERGE, compaction); Spark Connect cho phép Airflow gửi job mà không cài Spark.                                                                                                                                                                                                                         |
| **Next.js 15 + React 19**                                                                                        | frontend/ecommerce                                                                     | SSR/ISR cho SEO trang sản phẩm, App Router, i18n (next-intl), NextAuth với Keycloak.                                                                                                                                                                                                                                                  |

### 4.2 Giao tiếp và contract

| Công nghệ                              | Vai trò                            | Vì sao                                                                                                                                                                                                                            |
| -------------------------------------- | ---------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **gRPC + Protobuf** (Buf)              | gọi đồng bộ giữa service           | Contract chặt, sinh code cho 4 ngôn ngữ, nhị phân nhỏ, HTTP/2 multiplex. `buf lint` + `buf breaking` chặn thay đổi phá tương thích ngay trong CI (REST/JSON không có cơ chế tương đương rẻ như vậy).                              |
| **REST/JSON**                          | API công khai qua gateway, webhook | Frontend, đối tác và công cụ (curl, Hurl, k6) dùng dễ. OpenAPI ở `api/openapi`, lint bằng Spectral, so breaking bằng oasdiff.                                                                                                     |
| **GraphQL Federation v2**              | API cho web/app                    | Frontend lấy đúng trường cần trong một request thay vì gọi N REST; mỗi team sở hữu subgraph của mình.                                                                                                                             |
| **WunderGraph Cosmo Router**           | router GraphQL                     | Apache-2.0, có sẵn JWT, subscription WebSocket, giới hạn độ sâu, compose offline bằng `wgc`. Apollo Router bản miễn phí (ELv2) thiếu `@authenticated`, persisted query, subscription callback (cần GraphOS Enterprise). ADR 0011. |
| **Apache Kafka 4.1** (KRaft)           | event giữa service                 | Log bền, replay được, giữ thứ tự theo key (aggregate id), nhiều consumer group độc lập (service, Flink, lakehouse). So với RabbitMQ: không replay, khó làm nguồn cho analytics. KRaft bỏ Zookeeper.                               |
| **Debezium 3.3** (Outbox Event Router) | đẩy outbox/CDC từ WAL sang Kafka   | Không cần code publish trong service, không mất event khi pod chết giữa ghi DB và gửi Kafka (vấn đề dual-write).                                                                                                                  |
| **Apicurio Registry 3.1**              | Schema Registry                    | Lưu schema Protobuf/JSON Schema với rule BACKWARD; mã nguồn mở hoàn toàn (Confluent Schema Registry có giới hạn giấy phép Community).                                                                                             |
| **AsyncAPI**                           | tài liệu event                     | Sinh tự động từ proto event (`tools/scripts/gen-asyncapi.py`).                                                                                                                                                                    |

### 4.3 Dữ liệu

| Kho                                    | Dùng cho                                                                                         | Vì sao                                                                                                                                                                            |
| -------------------------------------- | ------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL 18 + pgvector**           | dữ liệu giao dịch mọi service, embedding gợi ý                                                   | ACID, JSONB (phiên checkout), `FOR UPDATE SKIP LOCKED` (scheduler giá, reservation), logical replication cho Debezium, pgvector để khỏi thêm vector DB riêng. Trên AWS là Aurora. |
| **Redis 8 (ElastiCache 7.1 trên AWS)** | flash sale (Lua nguyên tử), cache giá/bản dịch, velocity fraud, rate limit, pub/sub subscription | Lua script chạy nguyên tử đơn luồng: kiểm hàng + trừ + tạo claim trong một bước, không cần khoá phân tán. ADR 0017 giải thích vì sao quay lại Redis thay Valkey.                  |
| **MongoDB 8 (Atlas)**                  | read model trang sản phẩm (CQRS)                                                                 | Một document gộp sẵn mọi thứ trang chi tiết cần, đọc một lần theo slug. Atlas thay DocumentDB vì DocumentDB chỉ tương thích một phần API. ADR 0020.                               |
| **Elasticsearch 9 (Elastic Cloud)**    | tìm kiếm full-text, gợi ý từ khoá                                                                | Relevance, completion suggester, Kibana/ES\|QL; dùng bản managed qua PrivateLink để khỏi tự vận hành. ADR 0019.                                                                   |
| **Temporal 1.29**                      | trạng thái saga checkout                                                                         | Workflow bền: pod chết thì worker khác replay; retry/timer/signal khai báo trong code; UI xem từng đơn. ADR 0004.                                                                 |
| **Apache Iceberg + Polaris**           | bảng lakehouse                                                                                   | Định dạng bảng mở: Spark ghi, Trino đọc cùng bảng, có snapshot/time travel, MERGE theo dòng. Polaris là REST catalog chuẩn có RBAC. ADR 0013.                                     |
| **MinIO / S3**                         | object storage (media, lakehouse, checkpoint Flink, backup)                                      | S3 API; MinIO cho local.                                                                                                                                                          |
| **Trino**                              | SQL tương tác trên lakehouse                                                                     | Truy vấn nhanh không cần cụm Spark luôn chạy, JDBC cho BI.                                                                                                                        |
| **Airflow**                            | điều phối batch: decode protobuf → dbt → Feast                                                   | DAG trong Python, retry, lịch, UI.                                                                                                                                                |
| **dbt (dbt-trino)**                    | silver/gold của lakehouse                                                                        | SQL thuần dễ review, test khai báo, source freshness, docs và lineage cột. ADR 0023.                                                                                              |
| **Superset**                           | BI trên lakehouse                                                                                | Kết nối Trino tốt, dashboard khai báo trong code, phân quyền theo dataset. ADR 0023.                                                                                              |
| **OpenLineage + Marquez**              | lineage dữ liệu                                                                                  | Chuẩn mở, Spark/dbt/Airflow đều phát sẵn event. ADR 0023.                                                                                                                         |
| **Feast**                              | feature store cho ML                                                                             | Offline Trino, online Redis có sẵn; cùng định nghĩa feature cho training và serving. ADR 0023.                                                                                    |
| **OpenMetadata**                       | data catalog                                                                                     | Tìm bảng/cột, mô tả và test từ dbt, lineage; nhẹ hơn DataHub. ADR 0023.                                                                                                           |
| **ClickHouse, ScyllaDB**               | phân tích dashboard, lịch sử chat                                                                | Có manifest ở `platform/data`, profile compose `analytics`.                                                                                                                       |

### 4.4 Bảo mật, edge, mesh

| Công nghệ                                            | Vai trò                                                     | Vì sao                                                                                                                                                              |
| ---------------------------------------------------- | ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Keycloak 26**                                      | đăng nhập (OIDC/PKCE), role `CUSTOMER/SELLER/ADMIN/SUPPORT` | Mã nguồn mở, social login, consent screen (dùng cho MCP), thay auth-service tự viết.                                                                                |
| **OpenFGA**                                          | phân quyền theo quan hệ (ReBAC)                             | "Ai xem được đơn nào, seller sửa được sản phẩm nào" khó biểu diễn bằng role; model ở `platform/authz/openfga/model.fga`, có test.                                   |
| **Apache APISIX 3.14**                               | edge gateway                                                | Plugin OIDC, rate limit theo IP/consumer, giới hạn kết nối, OTel; cấu hình sinh từ một file `platform/gateway/routes.yaml`. Thay api-gateway Go tự viết (ADR 0005). |
| **Istio Ambient**                                    | mTLS, AuthorizationPolicy deny-by-default                   | Không sidecar nên ít tài nguyên, nâng mesh không phải restart pod (ADR 0006).                                                                                       |
| **Cilium + Hubble, Tetragon**                        | network policy L3–L7, runtime security eBPF                 | Đang chạy chế độ audit trước khi enforce.                                                                                                                           |
| **Vault + External Secrets**                         | bí mật                                                      | Secret không nằm trong Git; ESO đồng bộ vào K8s Secret.                                                                                                             |
| **Kyverno, Cosign, SLSA provenance, Syft, Trivy**    | supply chain                                                | Chỉ image đã ký, có SBOM và provenance từ CI mới chạy được.                                                                                                         |
| **CloudFront + WAF + Shield, GuardDuty, AWS Backup** | biên và an ninh AWS                                         | ADR 0019.                                                                                                                                                           |

### 4.5 Quan sát, giao hàng, hạ tầng

| Công nghệ                                                                                          | Vai trò                                                      | Vì sao                                                                                                     |
| -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------- |
| **OpenTelemetry** (SDK + Collector)                                                                | trace/metric/log chuẩn                                       | Không khoá vào vendor; Collector sinh RED metrics từ span, tail-sampling giữ 100% trace lỗi/chậm.          |
| **Grafana LGTM** (Loki, Grafana, Tempo, Mimir/Prometheus) + **Pyroscope** + **Alloy (Beyla eBPF)** | lưu trữ và xem                                               | Một stack mở thống nhất, log có `trace_id` để nhảy qua trace.                                              |
| **Pyrra**                                                                                          | SLO dạng CRD, cảnh báo multi-window burn rate                | SLO khai báo thay vì viết rule tay.                                                                        |
| **Unleash**                                                                                        | feature flag, kill switch (vd. `inventory.flash-sale.claim`) | Tự host được, SDK cho cả 4 ngôn ngữ.                                                                       |
| **Terraform**                                                                                      | hạ tầng AWS                                                  | IaC chuẩn, module tái dùng cho 4 env.                                                                      |
| **EKS + Karpenter + KEDA**                                                                         | chạy container, cấp node, autoscale theo lịch/lag            | Karpenter cấp node đúng loại nhanh hơn Cluster Autoscaler; KEDA scale theo Kafka lag và cron (flash sale). |
| **Helm + ArgoCD + Argo Rollouts + Kargo**                                                          | GitOps, canary theo SLO, promote giữa env                    | Deploy chỉ qua Git; canary tự rollback khi burn rate vượt ngưỡng.                                          |
| **ko, Jib, Paketo**                                                                                | build image                                                  | Không cần Docker daemon, image distroless non-root.                                                        |
| **Velero, Chaos Mesh**                                                                             | backup cluster, chaos định kỳ                                | Kiểm chứng độ bền thay vì giả định.                                                                        |
| **Backstage** (`catalog-info.yaml` mỗi service, template ở `tools/backstage`)                      | danh mục service, tạo service mới từ khuôn                   |                                                                                                            |

### 4.6 Build và chất lượng code

| Công cụ                                                                       | Ngôn ngữ  | Vai trò                                                                                       |
| ----------------------------------------------------------------------------- | --------- | --------------------------------------------------------------------------------------------- |
| Go workspace (`go.work`), golangci-lint, Testcontainers                       | Go        | nhiều module trong một workspace, lint, test tích hợp với DB thật                             |
| Gradle 9 + convention plugin (`build-logic/`), version catalog, ArchUnit, Jib | Java      | cấu hình chung cho mọi service Spring trong một chỗ                                           |
| npm workspaces, Biome, dependency-cruiser, Vitest, SWC                        | TS        | lint + format một công cụ (Biome thay ESLint + Prettier, nhanh hơn nhiều), kiểm luật lục giác |
| uv, ruff, pytest                                                              | Python    | uv cài nhanh và có lockfile; ruff thay flake8 + black + isort                                 |
| Buf                                                                           | proto     | lint, breaking, generate                                                                      |
| Hurl, k6                                                                      | e2e, load | file text dễ review, chạy trong CI/ArgoCD hook                                                |

---

## 5. Cấu trúc thư mục platform (chi tiết từng file)

Mục này giải thích từng thư mục và file quan trọng. Cây đầy đủ tới từng file (đã bỏ code sinh, lockfile, file
dịch) nằm ở [Phụ lục A](#phụ-lục-a-cây-thư-mục-đầy-đủ-của-platform).

### 5.1 Vì sao cấu trúc như vậy

Platform chia theo **vai trò** trước, theo **service** sau:

```
backend/app/ecommerce/
├── api/        hợp đồng (contract): thứ mà nhiều service cùng phụ thuộc → đặt riêng, là nguồn sự thật
├── libs/       thư viện nền theo ngôn ngữ + code sinh từ api/  → chuẩn chung (log, trace, auth, outbox) viết một lần
├── services/   service nghiệp vụ phục vụ request, mỗi service một thư mục độc lập (build, test, deploy riêng)
├── data/       pipeline dữ liệu (không phục vụ request người dùng): ingestion, streaming, lakehouse
├── ai/         thành phần cho AI agent (MCP)
├── platform/   cấu hình phần mềm nền chạy trong cluster (gateway, identity, Kafka, mesh, observability...)
├── deploy/     cách đóng gói và triển khai service (Helm, ArgoCD, compose local)
├── infra/      hạ tầng cloud (Terraform AWS)
├── tests/      test xuyên service (load, chaos, contract, e2e)
├── tools/      script sinh cấu hình, template Backstage
└── docs/       ADR, kiến trúc, runbook
```

Lý do:

- **Contract tách khỏi service** để đổi contract là một thay đổi có chủ đích, được CI kiểm breaking, và mọi ngôn ngữ
  sinh code từ cùng một nguồn.
- **`platform/` tách khỏi `deploy/`**: `platform/` là phần mềm của bên thứ ba (APISIX, Keycloak, Kafka...) mà mọi
  service dùng chung; `deploy/` là cách chạy service của chính mình. Hai thứ có vòng đời và người sở hữu khác nhau.
- **`data/` tách khỏi `services/`**: job dữ liệu có SLA, cách scale và công cụ khác (Spark, Flink, Airflow) so với
  service phục vụ request.
- **Mỗi service một thư mục tự đủ** (Dockerfile, migration, config, catalog-info) để CI build theo `paths` và một
  team có thể sở hữu trọn service.

### 5.2 File cấu hình ở gốc platform

| File                                                                                                   | Vai trò                                                                                                                                                                                                                                             |
| ------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Makefile`                                                                                             | mọi lệnh thường dùng: `up`, `down`, `logs`, `proto`, `gen`, `check`, `test-*`, `lint`, `token`, `fga-*`, `lakehouse-*` (`make help` để liệt kê)                                                                                                     |
| `.env.example`                                                                                         | biến cho compose local (mật khẩu Postgres, secret gateway, token Unleash); `make up` tự copy thành `.env`                                                                                                                                           |
| `go.work`, `go.work.sum`                                                                               | Go workspace gom `libs/go/*`, các service Go và clickstream-collector để dùng chung code local không cần publish module                                                                                                                             |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew` | Gradle multi-project: `libs/java/*`, 5 service Spring, job Flink; version catalog là chỗ duy nhất khai phiên bản thư viện Java                                                                                                                      |
| `build-logic/`                                                                                         | convention plugin Gradle: `congthanh.java-conventions` (toolchain JDK 25, test), `congthanh.spring-service` (starter web/security/grpc/otel/liquibase, Jib, ArchUnit), `congthanh.protobuf-conventions`, `congthanh.spring-native` (GraalVM opt-in) |
| `package.json`, `tsconfig.base.json`, `vitest.config.ts`, `vitest.global-setup.ts`                     | npm workspaces cho `libs/ts/*` và 7 service NestJS; Vitest global setup tự bật Postgres bằng Testcontainers                                                                                                                                         |
| `biome.json`                                                                                           | lint + format TypeScript                                                                                                                                                                                                                            |
| `.dependency-cruiser.cjs`                                                                              | luật lục giác cho TS: `domain-is-pure`, `application-no-adapters`, `application-no-infra-libs`, `inbound-not-outbound`, `no-cross-service`                                                                                                          |
| `.golangci.yml`                                                                                        | cấu hình lint Go                                                                                                                                                                                                                                    |
| `buf.yaml`, `buf.gen.yaml`                                                                             | module Buf trỏ vào `api/proto`, rule lint/breaking, plugin sinh Go vào `libs/go/proto`                                                                                                                                                              |
| `catalog-info.yaml`                                                                                    | khai báo hệ thống cho Backstage                                                                                                                                                                                                                     |
| `.editorconfig`, `.gitignore`, `.dockerignore`                                                         |                                                                                                                                                                                                                                                     |

### 5.3 `api/` — contract

```
api/
├── proto/ecommerce/<miền>/v1/
│   ├── <miền>_service.proto     gRPC service (vd. inventory_service.proto: Reserve/Commit/Release, CheckAvailability)
│   └── <miền>_events.proto      domain event phát qua outbox (vd. order_events.proto: OrderPlaced, OrderConfirmed...)
│   common/v1/event_envelope.proto   EventEnvelope bọc mọi event (google.protobuf.Any + traceparent + occurred_at)
│   common/v1/money.proto, query.proto
│   stream/v1/stream_events.proto    feature thời gian thực do Flink ghi (CustomerRiskFeatures, ProductTrending, StockCover)
├── asyncapi/asyncapi.yaml        tài liệu toàn bộ topic + event (SINH từ proto, không sửa tay)
├── openapi/cart-service.yaml, inventory-service.yaml   REST công khai của service Go
├── jsonschema/clickstream/v1/clickstream-event.json    schema event từ trình duyệt (JSON, không phải proto)
└── graphql/                      (trống: SDL GraphQL do từng subgraph tự sinh ra services/<svc>/schema.graphql)
```

Miền có proto: cart, catalog, common, customer, fraud, inventory, message, order, payment, pricing, product,
promotion, review, search, shipping, stream, tax. Đổi proto thì chạy `make proto` và commit cả code sinh.

### 5.4 `libs/` — thư viện nền

Mục tiêu: mọi service cùng ngôn ngữ có hành vi giống nhau (log JSON có trace id, OTel, xác thực JWT, outbox,
consumer chống trùng, DLQ) mà không copy code.

```
libs/go/platform/                 module Go dùng chung
├── apperr/        lỗi nghiệp vụ có mã (NotFound, Conflict, Invalid...) → map sang HTTP/gRPC status
├── auth/          xác thực JWT (JWKS Keycloak hoặc secret), fail-closed, RequireRole(verifier, "ADMIN", ...)
├── config/        đọc biến môi trường có mặc định
├── db/            pgxpool, Store, UnitOfWork (tx.go), dbtest/ cho test
├── featureflag/   client Unleash (kill switch)
├── grpcx/         gRPC client có OTel, retry, deadline
├── httpx/         ghi response JSON/problem+json
├── idgen/         Snowflake ID (NODE_ID mỗi service khác nhau)
├── kafka/         producer.go, consumer.go: mỗi partition tuần tự, retry backoff, DLQ <topic>.dlq + header x-dlq-*
├── logging/       slog JSON/text có trace_id
├── money/         tiền theo đơn vị nhỏ nhất, làm tròn
├── otel/          khởi tạo trace/metric/log OTLP
├── outbox/        outbox.go (ghi event trong cùng tx), janitor.go (xoá outbox > 1h, processed_events > 14 ngày)
├── server/        http.go, grpc.go, run.go (khởi động, graceful shutdown)
└── testkit/       PostgresURL(t), RedisURL(t): Testcontainers hoặc TEST_DATABASE_URL/TEST_REDIS_URL
libs/go/proto/                    code Go sinh từ api/proto (buf generate)

libs/java/
├── common-core/       nền chung cho mọi service Spring, tự bật qua auto-configuration (ADR 0022): lỗi RFC 9457
│                      (DomainException → HTTP problem+json / gRPC status + trailer error-code), access log, BaseEntity + JPA auditing, AuthenticationUtils
├── platform/          auto-configuration Spring Boot cho platform:
│   ├── outbox/OutboxWriter, OutboxJanitor       ghi outbox cùng transaction, dọn định kỳ
│   ├── inbox/ProcessedEvents, EventEnvelopes    consumer idempotent theo (event_id, consumer)
│   ├── idempotency/IdempotencyKeyFilter, IdempotencyStore   header Idempotency-Key, lease 60s
│   ├── security/KeycloakJwtRoles                map claim roles → GrantedAuthority
│   ├── temporal/TemporalAutoConfiguration, TemporalWorkerRegistration   client + worker Temporal
│   ├── migration/LiquibaseSchemaCreator         tạo schema trước khi Liquibase chạy
│   └── resources/db/platform/001..004-*.sql     bảng outbox, processed_events, idempotency_keys (+ lease)
├── proto/             code Java sinh từ api/proto trong Gradle
├── workflow-contracts/   interface Temporal dùng chung giữa checkout/order/payment:
│   ├── TaskQueues.java          checkout-workflow, checkout, payment
│   ├── checkout/CheckoutActivities, CheckoutInput, CheckoutResult, CheckoutState, PaymentCompleted, CheckoutContract
│   └── payment/PaymentActivities, AuthorizePaymentRequest, PaymentAuthorization, PaymentFailures, RefundPaymentRequest
└── testing/HexagonalArchitecture.java   luật ArchUnit dùng chung

libs/ts/
├── proto-gen/  (@ecommerce/proto)    code Protobuf-ES sinh vào src/gen/, không commit
├── shared/     (@ecommerce/shared)   money.ts, problem.ts dùng được cả ở domain
├── platform/   (@ecommerce/platform) NestJS: auth (guard, @Public/@Roles, PrincipalMiddleware, jwt-verifier),
│               db (Drizzle, outbox, inbox, janitor), kafka/event-consumer (retry + DLQ), graphql (federation,
│               subscriptions), grpc (client/server), realtime/redis-pubsub, errors/problem.filter, telemetry, health
└── clients/    (@ecommerce/clients)  client gRPC/REST tới cart, checkout, customer, inventory, order, product, promotion, recommendation

libs/python/
├── ecommerce-platform/   FastAPI: auth.py (JWT), fastapi.py (health, lỗi, middleware), logging.py
└── ecommerce-proto/      code protobuf Python (được commit), generate.sh
```

### 5.5 `services/` — cấu trúc chuẩn theo từng ngôn ngữ

Mọi service theo **kiến trúc lục giác**: luồng phụ thuộc chỉ đi vào trong.

```mermaid
flowchart LR
  subgraph driving[Adapter vào]
    http[HTTP handler]
    grpcin[gRPC server]
    cons[Kafka consumer]
    wf[Temporal activity/workflow]
  end
  subgraph app[Application]
    uc[Use case / service]
    port[(Port: interface)]
  end
  subgraph dom[Domain]
    ent[Entity, value object,<br/>quy tắc, domain event]
  end
  subgraph driven[Adapter ra]
    repo[Postgres/Redis/Mongo repo]
    grpcout[gRPC client]
    prod[Outbox/Kafka]
  end
  http & grpcin & cons & wf --> uc
  uc --> ent
  uc --> port
  repo & grpcout & prod -. hiện thực .-> port
```

Lợi ích: domain test không cần DB; đổi hạ tầng (Valkey → Redis, OpenSearch → Elasticsearch) chỉ đổi adapter;
use case test được với adapter giả.

**Service Go** (ví dụ thật: `services/inventory-service`)

```
inventory-service/
├── cmd/server/main.go                composition root: đọc config, tạo pool DB/Redis/Kafka, nối adapter vào use case, chạy HTTP + gRPC + consumer + sweeper
├── internal/
│   ├── config/config.go              biến môi trường (HTTP_ADDR, GRPC_ADDR, DATABASE_URL, DB_SCHEMA, FLASH_CLAIM_TTL...)
│   ├── domain/                       thuần, không import hạ tầng
│   │   ├── item.go, stock.go, warehouse.go, reservation.go   thực thể và quy tắc (giữ chỗ all-or-nothing, hết hạn)
│   │   ├── events.go, errors.go
│   │   └── domain_test.go
│   ├── app/                          use case + port
│   │   ├── ports.go                  interface: UnitOfWork, Reader, WarehouseRepository, FlashSaleStore, ClaimPublisher
│   │   ├── stock.go, warehouse.go    CRUD tồn kho, điều chỉnh
│   │   ├── reservation.go            Reserve / Commit / Release / ReleaseExpired (saga gọi)
│   │   └── flashsale.go              Start / Queue / Claim / Sweep / End flash sale
│   ├── adapter/
│   │   ├── http/handler.go, flashsale.go, errors.go     REST chi router (/api/ecommerce/inventory, /warehouses, /flash-sales)
│   │   ├── grpc/server.go                               gRPC InventoryService (order-service gọi)
│   │   ├── messaging/consumer.go, events.go, claims.go  đọc event, publish FlashSaleClaimed
│   │   └── repository/
│   │       ├── postgres/repository.go, warehouse.go     pgx + outbox trong tx
│   │       └── redis/flashsale.go + lua/{claim,queue,drain,release,sweep,transition}.lua
│   └── integration/                  test tích hợp với Postgres + Redis thật (Testcontainers)
├── migrations/00001_init.sql, 00002_outbox.sql, 00003_processed_events_pk.sql, embed.go   goose, nhúng vào binary
├── Dockerfile, .env.example, catalog-info.yaml, go.mod, Jenkinsfile (cũ)
```

Service Go khác giống hệt khung này (`cmd/server`, `internal/{config,domain,app,adapter}`, `migrations`).

**Service Java/Spring** (ví dụ thật: `services/checkout-service`)

```
checkout-service/
├── build.gradle.kts                    plugins { id("congthanh.spring-service") } + phụ thuộc riêng
├── src/main/java/com/congthanh/checkoutservice/
│   ├── CheckoutServiceApplication.java
│   ├── domain/
│   │   ├── model/CheckoutSession.java   aggregate phiên checkout (items, quote, địa chỉ, trạng thái, flash sale)
│   │   ├── model/SessionStatus.java     OPEN, PENDING_REVIEW, PLACED, REJECTED, EXPIRED, CANCELLED
│   │   ├── model/Money.java, Address.java
│   │   └── exception/CheckoutException.java   kind: INVALID, CONFLICT, REJECTED, NOT_FOUND, UNAVAILABLE
│   ├── application/
│   │   ├── port/in/CheckoutUseCase.java
│   │   ├── port/out/Catalog, Pricing, Promotions, Shipping, Tax, FraudCheck, Orders, SessionRepository, CheckoutWorkflows, Ids
│   │   └── service/CheckoutService.java       open / update / place / cancel / onFraudReviewDecided / onFlashSaleClaimed / requote
│   ├── adapter/
│   │   ├── in/web/CheckoutController, CheckoutDtos, PlaceLimiter (giới hạn đồng thời), ProblemHandler
│   │   ├── in/messaging/FlashSaleEventsConsumer, FraudEventsConsumer
│   │   ├── in/workflow/CheckoutWorkflow, CheckoutWorkflowImpl      ★ saga Temporal
│   │   ├── out/grpc/{Catalog,Pricing,Promotions,Shipping,Tax,Orders}GrpcAdapter
│   │   ├── out/http/FraudHttpAdapter
│   │   ├── out/persistence/JdbcSessionRepository (JSONB + khoá lạc quan), UuidV7Ids
│   │   └── out/workflow/TemporalCheckoutWorkflows
│   └── config/CheckoutConfig.java (nối bean), SecurityConfig.java
└── src/main/resources/
    ├── application.yaml                cấu hình, mọi giá trị đọc từ biến môi trường
    └── db/changelog/db.changelog-master.yaml + changes/0001-checkout-sessions.sql   Liquibase
```

Java dùng `adapter/in` (driving) và `adapter/out` (driven) để thấy rõ chiều. ArchUnit (`libs/java/testing`) chặn
`domain` import Spring/JPA và `application` import adapter.

**Service NestJS** (ví dụ thật: `services/catalog-service`)

```
catalog-service/
├── src/
│   ├── main.ts               bootstrap (platform/bootstrap.ts): OTel, log, HTTP + gRPC
│   ├── app.module.ts         nối module, provider ↔ port
│   ├── config/env.ts         schema biến môi trường
│   ├── domain/               brand.ts, category.ts (+ test), country.ts, slug.ts, tag.ts — thuần
│   ├── application/          catalog.service.ts (use case), ports.ts
│   ├── adapters/
│   │   ├── in/graphql/       catalog.resolver.ts, types.ts, mappers.ts   (subgraph Federation)
│   │   ├── in/grpc/          catalog.grpc.ts                             (product-service, checkout gọi)
│   │   └── out/persistence/  schema.ts (Drizzle), repositories.ts, unit-of-work.ts; out/product-directory.grpc.ts
│   ├── emit-schema.ts        ghi SDL ra schema.graphql
│   └── migrate.ts            chạy migration (init container)
├── drizzle/0000_init.sql, 0001_seed_reference_data.sql   migration SQL sinh bằng drizzle-kit
├── schema.graphql            SDL subgraph (sinh, commit để CI so khớp supergraph)
├── test/catalog.it.test.ts   test tích hợp
└── package.json, tsconfig*.json, drizzle.config.ts
```

BFF (`storefront-bff`, `backoffice-bff`) không có DB: `application/storefront.service.ts` gọi `@ecommerce/clients`,
`adapters/in/graphql/loaders.ts` gom request bằng DataLoader. Image chung: `services/node.Dockerfile`.

**Service Python/FastAPI** (ví dụ thật: `services/fraud-service`)

```
fraud-service/
├── app/
│   ├── __main__.py           uvicorn, đọc HTTP_PORT
│   ├── main.py               FastAPI app, route, lifespan (kết nối DB/Redis/Kafka, chạy migration)
│   ├── config.py             cấu hình (pydantic-settings)
│   ├── domain/rules.py       ★ luật chấm điểm thuần, có giải thích
│   ├── service.py            use case: assess, review, consume event
│   ├── ports.py              Protocol cho repo/cache
│   ├── adapters/postgres.py, redis_store.py, kafka.py
│   └── migrations/001_init.sql, 002_shipment_returned.sql
├── tests/test_rules.py, test_api.py, test_integration.py, test_stream.py
└── pyproject.toml (+ uv.lock), .env.example, catalog-info.yaml
```

Image chung: `services/python.Dockerfile --build-arg SERVICE=<svc>`. `supplier-service` là Django
(`config/settings.py`, `suppliers/{models,views,serializers,service}.py`, `manage.py`).

### 5.6 Thư mục đặc biệt trong từng service

| Service                | File/thư mục đáng chú ý                                                                                                                                                                                   | Nội dung                                                  |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| inventory              | `internal/app/flashsale.go`, `adapter/repository/redis/lua/*.lua`                                                                                                                                         | toàn bộ flash sale (mục 8.6)                              |
| cart                   | `internal/app/service.go` (`OrderPlaced/Confirmed/Cancelled`), `migrations/00003_pending_order_clears.sql`                                                                                                | xoá giỏ đúng lúc (mục 8.8)                                |
| pricing                | `internal/domain/pricing.go`, `adapter/cache/redis.go`, `migrations/00002_usd_default.sql`                                                                                                                | bảng giá, lịch giảm giá SALE/FLASH, giá thấp nhất 30 ngày |
| search                 | adapter Elasticsearch, consumer `ecommerce.product.events`                                                                                                                                                | index + completion suggester                              |
| webhook                | HMAC, retry backoff, chặn SSRF                                                                                                                                                                            | gửi event công khai cho đối tác                           |
| checkout               | `adapter/in/workflow/CheckoutWorkflowImpl.java`, `application/service/CheckoutService.java`                                                                                                               | saga + phiên checkout (mục 8.2–8.4)                       |
| order                  | `adapter/in/workflow/CheckoutActivitiesImpl.java`, `application/service/OrderCommandService.java`, `CheckoutRecovery.java`, `adapter/in/grpc/OrderGrpcService.java`                                       | activity của saga, `PlaceOrder`, quét đơn PENDING treo    |
| payment                | `adapter/out/provider/{Stripe,PayPal,Sandbox}Provider.java`, `ProviderRegistry.java`, `adapter/in/web/SandboxController.java`, `domain/model/InstallmentPolicy.java`, `config/InstallmentProperties.java` | cổng thanh toán, trả góp, webhook                         |
| product                | `adapter/in/messaging/ProductReadModelConsumer.java`, `application/service/ProductReadModelService.java`, `ProductDocumentAssembler.java`, `adapter/out/readmodel/MongoProductReadModelStore.java`        | read model MongoDB (mục 8.9)                              |
| promotion              | `domain/model/{Promotion,DiscountRule,Redemption,Quote}.java`, `adapter/in/messaging/OrderEventsConsumer.java`                                                                                            | voucher, redeem/huỷ redeem theo đơn                       |
| notification           | kênh email/SMS/web push/FCM, `SKIPPED` khi thiếu cấu hình                                                                                                                                                 |                                                           |
| recommendation         | `app/domain/ranking.py`, `embedding.py`, `adapters/ollama.py`, `migrations/002_stream_trending.sql`                                                                                                       | mua kèm, tương tự, xu hướng                               |
| commerce-mcp (ở `ai/`) | `app/server.py` (tool MCP), `adapters/openfga.py`, `adapters/http.py`                                                                                                                                     | agentic commerce (mục 13)                                 |

`Jenkinsfile` trong một số service là di sản thời Jenkins, CI hiện tại là GitHub Actions.

### 5.7 `data/`, `ai/`

```
data/
├── ingestion/clickstream-collector/   service Go: POST /v1/events → kiểm JSON Schema → băm IP → Kafka clickstream.events
├── streaming/stream-processor/        job Flink (Gradle): pipeline/{CustomerRisk,ProductTrending,StockCover}Pipeline.java,
│                                      logic/{RiskWindow,TrendingAggregate,StockCoverState}.java, io/{KafkaIo,Envelopes}.java
└── lakehouse/
    ├── jobs/stream_ingest.py          Spark Structured Streaming: Kafka → bronze (append) + ods (MERGE)
    ├── jobs/ods_tables.py             khai báo bảng ODS (nguồn, khoá, kiểu)
    ├── jobs/decode_events.py          giải mã protobuf outbox + Flink → silver.domain_events / stream_signals
    ├── jobs/maintenance.py, common.py  bảo trì Iceberg, tiện ích chung
    ├── dbt/                           ★ silver/gold bằng SQL (dbt-trino): models, test, source freshness
    ├── feast/                         feature repo Feast (nguồn gold.customer_features)
    ├── airflow/dags/lakehouse.py      DAG hằng giờ (decode → dbt → Feast) + bảo trì hằng ngày
    ├── sql/trino-examples.sql
    └── tools/simulate_clickstream.py
ai/commerce-mcp-service/               MCP server (Python) cho AI agent của khách
```

### 5.8 `platform/` — phần mềm nền

| Thư mục                                                                                                    | Nội dung                                                                                                                                                                                      |
| ---------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `gateway/routes.yaml`                                                                                      | ★ nguồn sự thật định tuyến; sinh ra `gateway/apisix/apisix.yaml` (local) và `gateway/apisix/k8s/routes.yaml` (ApisixRoute)                                                                    |
| `gateway/apisix/config.yaml`, `k8s/global-rules.yaml`, `k8s/values.yaml`                                   | cấu hình APISIX, plugin toàn cục, Helm values                                                                                                                                                 |
| `identity/keycloak/realms/ecommerce-realm.json`                                                            | realm import khi chạy local: client, role, user mẫu; `k8s/` cho cluster                                                                                                                       |
| `authz/openfga/model.fga`, `tests.fga.yaml`, `values.yaml`                                                 | model ReBAC + test                                                                                                                                                                            |
| `messaging/kafka/{kafka-cluster,topics,users}.yaml`                                                        | Strimzi Kafka, KafkaTopic, KafkaUser                                                                                                                                                          |
| `messaging/debezium/local/*.json`                                                                          | connector outbox từng service + `ods-cdc.json` (lakehouse), đăng ký bởi container `connect-init*`                                                                                             |
| `messaging/debezium/{kafka-connect.yaml, connectors/outbox-connectors.yaml, connectors/iceberg-sink.yaml}` | KafkaConnect + connector trên K8s                                                                                                                                                             |
| `messaging/schema-registry/apicurio.yaml`                                                                  | Apicurio trên K8s                                                                                                                                                                             |
| `graphql/{storefront,backoffice}/graph.yaml` → `config.json`, `supergraph.graphql`                         | compose supergraph (sinh, commit để review diff)                                                                                                                                              |
| `graphql/router/{config.yaml, Dockerfile}`                                                                 | cấu hình Cosmo Router                                                                                                                                                                         |
| `mesh/istio/*`                                                                                             | cài Ambient, namespace, PeerAuthentication, AuthorizationPolicy deny-by-default, traffic (retry/timeout)                                                                                      |
| `observability/`                                                                                           | `otel-collector/otelcol-config.yaml`, `prometheus/{prometheus,alert-rules,slo-rules}.yaml`, `grafana/provisioning`, `loki`, `tempo`, `pyrra`, `slo/slos.yaml`, `k8s/{lgtm,alloy}-values.yaml` |
| `workflow/temporal/{values,namespaces}.yaml`                                                               | Temporal trên K8s                                                                                                                                                                             |
| `featureflags/unleash/{flags.json, values.yaml}`                                                           | flag mặc định                                                                                                                                                                                 |
| `secrets/{vault/values.yaml, external-secrets/cluster-secret-store.yaml}`                                  |                                                                                                                                                                                               |
| `policy/kyverno/policies.yaml`                                                                             | chặn image chưa ký, thiếu SBOM/provenance, `:latest`, thiếu probe/resources                                                                                                                   |
| `delivery/argo-rollouts/*`, `delivery/kargo/*`                                                             | canary theo SLO (`slo-burn-rate.yaml`), pipeline promote (`pipeline.yaml` sinh bằng `gen-kargo.py`)                                                                                           |
| `network/cilium/*`, `security/tetragon/*`                                                                  | eBPF network policy, runtime security (audit)                                                                                                                                                 |
| `streaming/flink/{base,overlays/<env>}`                                                                    | FlinkDeployment                                                                                                                                                                               |
| `inference/{base,overlays}`                                                                                | KServe + vLLM (Qwen3-8B), node GPU Karpenter                                                                                                                                                  |
| `data/{postgres-cnpg,redis,clickhouse,scylla}.yaml`                                                        | kho dữ liệu cho cluster không phải AWS                                                                                                                                                        |
| `chaos/schedules.yaml`                                                                                     | Chaos Mesh định kỳ                                                                                                                                                                            |
| `debezium/connectors/`, `kafka/topics.yaml`, `temporal/namespaces.yaml`                                    | **di sản**, không còn được tham chiếu (connector của `user-service`/`seller-service` đã gỡ); bản đang dùng nằm trong `messaging/` và `workflow/`                                              |

### 5.9 `deploy/`, `infra/`, `tests/`, `tools/`, `docs/`

```
deploy/
├── local/docker-compose.yaml            ★ stack local, chia profile (mục 9)
├── local/docker-compose.lakehouse.yaml  ghép thêm lakehouse
├── local/postgres/{Dockerfile,init.sql} Postgres + pgvector, tạo DB ecommerce/keycloak/temporal/unleash/openfga/registry
├── local/lakehouse/                     Dockerfile Spark/Airflow/dbt/Superset/Feast, catalog Trino (lakehouse,
│                                        lakehouse_ro), init Polaris + RBAC, Marquez, dashboard Superset, ingest OpenMetadata
├── helm/microservice/                   chart dùng chung cho MỌI service: workload (Deployment/Rollout), service,
│                                        httproute, autoscaling (HPA/KEDA), pdb, externalsecret, servicemonitor, analysis-template
├── helm/values/<svc>/values*.yaml       values theo service và môi trường (thêm thư mục = thêm service vào GitOps)
├── helm/events/events-<env>.yaml        lịch sự kiện để scale trước (flash sale)
├── argocd/bootstrap.yaml                app-of-apps
├── argocd/applicationsets/              services (mỗi values × mỗi cluster), streaming, inference, backup, chaos
├── argocd/platform/, projects/          operator, namespace, AppProject
└── legacy/                              cấu hình đời trước (Spring Cloud, ELK, k8s thuần): chỉ tham khảo

infra/terraform/
├── modules/  network, eks, rds-postgres, elasticache, msk, elastic-cloud, mongodb-atlas, s3-cdn, dns, waf, edge,
│             backup, guardduty, workload-bucket
└── envs/     dev, test, staging, prod (mỗi env một state, tfvars)

tests/
├── load/k6/browse-and-cart.js, flash-sale.js
├── chaos/inventory-pod-kill.yaml, kafka-network-delay.yaml, redis-failure.yaml
├── contract/.spectral.yaml
└── e2e/smoke.hurl, graphql.hurl

tools/
├── scripts/gen-apisix-routes.py     routes.yaml → cấu hình APISIX (--check trong CI)
├── scripts/gen-asyncapi.py          proto event → api/asyncapi/asyncapi.yaml
├── scripts/gen-kargo.py             helm/values → platform/delivery/kargo/pipeline.yaml
├── scripts/compose-supergraph.mjs   SDL subgraph → supergraph (npm run supergraph[:check])
├── scripts/new-node-service.sh      tạo service NestJS từ khuôn
├── scripts/validate-*.{py,mjs}      kiểm router config, Hurl GraphQL
├── backstage/templates/go-service/  template tạo service Go
└── agents/                          OTel Java agent (không commit .jar)

docs/
├── adr/0001..0021 + README.md       quyết định kiến trúc (đọc khi muốn biết "tại sao")
├── architecture/overview.md          sơ đồ tổng quan
└── runbooks/                         debezium-outbox, flash-sale, kafka-consumer-lag, slo-burn
```

---

## 6. Danh sách service

| Service                      | Ngôn ngữ                         | HTTP            | gRPC                                         | Vai trò                                                                                                            |
| ---------------------------- | -------------------------------- | --------------- | -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| inventory-service            | Go 1.26                          | 5014            | 9092                                         | tồn kho, kho, giữ chỗ saga, flash sale (Redis Lua: giành suất, claim có hạn, phòng chờ)                            |
| cart-service                 | Go 1.26                          | 5002            | 19091                                        | giỏ hàng; xoá sản phẩm đã đặt khi đơn được xác nhận                                                                |
| tax-service                  | Go 1.26                          | 5022            | 9096                                         | thuế suất theo bang, tính thuế từng dòng                                                                           |
| search-service               | Go 1.26                          | 5013            | 9098                                         | tìm kiếm, gợi ý từ khoá (Elasticsearch), cập nhật từ `ecommerce.product.events`                                    |
| shipping-service             | Go 1.26                          | 5016            | 9097                                         | phí giao hàng, vận đơn, hành trình                                                                                 |
| media-service                | Go 1.26                          | 5007            |                                              | upload S3/R2 bằng URL ký sẵn                                                                                       |
| webhook-service              | Go 1.26                          | 5023            |                                              | webhook bên thứ ba: HMAC, thử lại có backoff, chặn SSRF                                                            |
| pricing-service              | Go 1.26                          | 5025            | 9099                                         | bảng giá theo nhóm khách, lịch giảm giá, lịch sử giá (giá thấp nhất 30 ngày)                                       |
| privacy-service              | Go 1.26                          | 5033            |                                              | CCPA/CPRA: consent, GPC, yêu cầu xem/xoá dữ liệu điều phối qua các service (hạn 45 ngày)                           |
| clickstream-collector        | Go 1.26                          | 5029            |                                              | thu thập view/search/add-to-cart từ web/app vào Kafka cho lakehouse                                                |
| product-service              | Java 25 / Spring Boot 4          | 5008            | 29090                                        | sản phẩm; read model trang sản phẩm trên MongoDB (CQRS)                                                            |
| checkout-service             | Java 25 / Spring Boot 4          | 5027            | 19095                                        | phiên checkout, báo giá, kiểm tra gian lận, chạy saga Temporal (queue `checkout-workflow`)                         |
| order-service                | Java 25 / Spring Boot 4          | 5006            | 19090                                        | đơn hàng, activity của saga (queue `checkout`), gRPC `PlaceOrder`                                                  |
| payment-service              | Java 25 / Spring Boot 4          | 5018            | 9090 (không publish ra máy khi chạy compose) | thanh toán: thẻ, Apple Pay, Google Pay, Affirm/Klarna/Afterpay (Stripe), PayPal, sandbox; activity queue `payment` |
| promotion-service            | Java 25 / Spring Boot 4          | 5009            | 9095                                         | mã giảm giá                                                                                                        |
| ledger-service               | Java 25 / Spring Boot 4          | 5031            |                                              | sổ cái kép chỉ ghi thêm: hạch toán từ event thanh toán, số dư, cân đối thử, bút toán tay maker/checker             |
| returns-service              | Java 25 / Spring Boot 4          | 5032            |                                              | đổi trả (RMA), chính sách trả, kiểm hàng, hoàn tiền một phần qua workflow Temporal (queue `returns`)               |
| stored-value-service         | Java 25 / Spring Boot 4          | 5034            |                                              | store credit: số dư, giữ chỗ/trừ/trả (activity Temporal queue `stored-value`), hoàn hàng bằng credit              |
| dispute-service              | Java 25 / Spring Boot 4          | 5035            |                                              | tranh chấp thẻ: webhook Stripe, gom bằng chứng tự động, khuyến nghị chống/chấp nhận, hạn nộp                      |
| loyalty-service              | Java 25 / Spring Boot 4          | 5037            |                                              | điểm thưởng theo lô (chờ → khả dụng → hết hạn), đổi điểm (activity queue `loyalty`), hạng thành viên               |
| subscription-service         | Java 25 / Spring Boot 4          | 5038            |                                              | mua định kỳ: bỏ qua/tạm dừng/huỷ, nhắc kỳ giao, dunning; tạo đơn tự động chưa bật                                  |
| stream-processor             | Flink 2.2 (Java 21)              | 8091 (UI local) |                                              | feature thời gian thực: rủi ro khách 24h, xu hướng 1h, số giờ còn hàng                                             |
| catalog-service              | NestJS 12 / TS 6                 | 5003            | 19092                                        | danh mục, thương hiệu, tag, quốc gia                                                                               |
| customer-service             | NestJS 12 / TS 6                 | 5004            | 19094                                        | địa chỉ giao hàng, wishlist, lịch sử tìm kiếm                                                                      |
| review-service               | NestJS 12 / TS 6                 | 5010            |                                              | đánh giá (chỉ khách đã mua), điểm trung bình                                                                       |
| notification-service         | NestJS 12 / TS 6                 | 5011            |                                              | in-app, email, SMS, web push, FCM                                                                                  |
| message-service              | NestJS 12 / TS 6                 | 5012            |                                              | chat khách – người bán/CSKH                                                                                        |
| storefront-bff               | NestJS 12 / TS 6                 | 5020            |                                              | GraphQL cho web/app khách: Product, giỏ, đặt hàng, gợi ý                                                           |
| backoffice-bff               | NestJS 12 / TS 6                 | 5021            |                                              | GraphQL quản trị: sản phẩm, tồn kho, hỗ trợ khách                                                                  |
| graphql-router / -backoffice | Cosmo Router                     | 4000 / 4001     |                                              | supergraph storefront / backoffice                                                                                 |
| translate-service            | FastAPI + Claude API             | 5017            |                                              | dịch sản phẩm/đánh giá, cache Redis                                                                                |
| chatbot-service              | FastAPI + Claude / Ollama / vLLM | 5024            |                                              | trợ lý mua sắm, SSE                                                                                                |
| commerce-mcp-service         | MCP (Python SDK `mcp` 2.x)       | 5030            |                                              | AI agent của khách tìm sản phẩm, quản lý giỏ, xem/huỷ đơn                                                          |
| supplier-service             | Django 6 + DRF                   | 5019            |                                              | gian hàng người bán, duyệt bởi admin                                                                               |
| fraud-service                | FastAPI + asyncpg + Redis        | 5026            |                                              | chấm điểm rủi ro, hàng chờ duyệt tay, danh sách chặn                                                               |
| reconciliation-service       | FastAPI + asyncpg                | 5036            |                                              | đối soát Stripe (API) / CSV với event payment, hàng chờ chênh lệch                                                 |
| moderation-service           | FastAPI + asyncpg + Claude       | 5039            |                                              | kiểm duyệt nội dung: luật + LLM, hàng chờ người duyệt, khiếu nại                                                   |
| recommendation-service       | FastAPI + pgvector               | 5028            |                                              | mua kèm, tương tự, xu hướng, gợi ý cho riêng khách                                                                 |

Thư mục: clickstream-collector ở `data/ingestion/`, stream-processor ở `data/streaming/`, commerce-mcp-service ở `ai/`;
service còn lại ở `services/`. Service NestJS (trừ BFF) chỉ có GraphQL (qua router) và gRPC nội bộ, không có route REST
ở gateway. Mỗi service có `.env.example` (nếu cần cấu hình); copy thành `.env` khi chạy ngoài Docker.

---

## 7. Luồng đi của hệ thống

### 7.1 Một request đi từ trình duyệt tới service

```mermaid
sequenceDiagram
  autonumber
  participant B as Trình duyệt (Next.js)
  participant KC as Keycloak
  participant CF as CloudFront + WAF (AWS)
  participant GW as APISIX
  participant R as Cosmo Router
  participant BFF as storefront-bff
  participant S as Service (vd. cart, Go)
  participant DB as Postgres (schema của service)

  B->>KC: Đăng nhập OIDC + PKCE (client storefront-web)
  KC-->>B: access token (JWT, claim roles)
  B->>CF: POST /graphql, Authorization: Bearer
  CF->>GW: chuyển tiếp (VPC origin → NLB nội bộ)
  GW->>GW: rate limit, route theo platform/gateway/routes.yaml
  GW->>R: /graphql
  R->>R: kiểm chữ ký JWT (JWKS), giới hạn độ sâu
  R->>BFF: query con của subgraph, kèm Authorization
  BFF->>S: gRPC/REST, chuyển tiếp token người dùng (token relay)
  S->>S: tự kiểm JWT lại + kiểm quyền sở hữu (defense in depth)
  S->>DB: đọc/ghi trong schema riêng
  S-->>BFF: kết quả
  BFF-->>R: dữ liệu (DataLoader gom N sản phẩm thành 1 lần gọi)
  R-->>B: JSON ghép từ nhiều subgraph
```

REST công khai (`/api/ecommerce/*`) đi thẳng APISIX → service; route có `auth: required` bị APISIX chặn nếu
thiếu/sai token (plugin `openid-connect`, Keycloak `bearer_only`). Bảng route: `platform/gateway/routes.yaml`.

### 7.2 Luồng event: Transactional Outbox → Debezium → Kafka → consumer

```mermaid
sequenceDiagram
  autonumber
  participant UC as Use case (service A)
  participant DB as Postgres A
  participant DBZ as Debezium (Outbox Event Router)
  participant K as Kafka topic ecommerce.{miền}.events
  participant C as Consumer (service B)
  participant DB2 as Postgres B

  UC->>DB: BEGIN, ghi dữ liệu nghiệp vụ
  UC->>DB: INSERT outbox(EventEnvelope protobuf)
  UC->>DB: COMMIT (cả hai cùng thành hoặc cùng hỏng)
  DBZ->>DB: đọc WAL qua replication slot
  DBZ->>K: publish, key = aggregate id (giữ thứ tự theo aggregate)
  K->>C: poll (mỗi partition xử lý tuần tự)
  C->>DB2: BEGIN, INSERT processed_events(event_id, consumer)
  alt đã có (event giao lại)
    C->>DB2: ROLLBACK, bỏ qua
  else lần đầu
    C->>DB2: xử lý + COMMIT
  end
  Note over C,K: lỗi lặp lại sau N lần retry → ghi {topic}.dlq kèm header x-dlq-*, đi tiếp
  Note over DB: janitor xoá outbox sau 1h, processed_events sau 14 ngày (connector bỏ qua DELETE)
```

Vì sao không publish thẳng Kafka trong code? Ghi DB rồi gửi Kafka là hai bước; pod chết giữa chừng thì mất event
hoặc gửi event cho dữ liệu chưa commit. Outbox + Debezium biến nó thành một transaction (ADR 0003). Ngoại lệ có chủ
đích: `FlashSaleClaimed` (không có transaction DB, mục 8.6) và `clickstream.events` (nguồn là trình duyệt).

Topic chính: `ecommerce.{cart,catalog,customer,dispute,fraud,inventory,ledger,loyalty,message,moderation,order,payment,pricing,privacy,product,promotion,reconciliation,returns,review,shipping,storedvalue,subscription}.events`,
`ecommerce.stream.*` (Flink), `clickstream.events`, `ods.<schema>.<bảng>` (CDC cho lakehouse).

### 7.3 Bản đồ ai gọi ai

```mermaid
flowchart LR
  sbff[storefront-bff] -->|gRPC| product & cart & order & inventory & promotion & customer & recommendation
  sbff -->|REST| checkout
  checkout -->|gRPC| catalog & pricing & promotion & shipping & tax & order
  checkout -->|HTTP 300ms| fraud
  order -->|gRPC| inventory & product & promotion
  cart -->|gRPC| inventory & product
  product -->|gRPC| catalog
  mcp[commerce-mcp] -->|REST + token| search & product & cart & order
  mcp -->|check| openfga[(OpenFGA)]
  temporal[(Temporal)] -.task queue checkout.-> order
  temporal -.task queue payment.-> payment
  temporal -.task queue checkout-workflow.-> checkout
```

Đường gọi đồng bộ mới phải được mở trong `platform/mesh/istio/authorization-policies.yaml` (mặc định từ chối).

### 7.4 Luồng mua hàng đầu-cuối (góc nhìn khách)

```mermaid
flowchart LR
  A[Xem sản phẩm<br/>product read model] --> B[Thêm vào giỏ<br/>cart-service]
  B --> C[Mở phiên checkout<br/>POST /checkout/sessions]
  C --> D[Nhập địa chỉ, chọn ship<br/>PATCH → báo giá lại]
  D --> E[Đặt đơn<br/>POST .../place + Idempotency-Key]
  E --> F{fraud}
  F -->|APPROVE| G[order-service tạo đơn PENDING<br/>start saga Temporal]
  F -->|REVIEW| H[202 chờ duyệt tay]
  F -->|REJECT| X[422 FRAUD_REJECTED]
  H -->|duyệt OK| G
  G --> I[giữ hàng → authorize]
  I -->|cổng redirect| J[khách trả tiền ở Stripe/PayPal/BNPL<br/>webhook → signal]
  I -->|thẻ OK ngay| K[capture → commit kho → confirm]
  J --> K
  K --> L[OrderConfirmed → cart xoá món đã mua,<br/>notification, search, lakehouse...]
```

### 7.5 Luồng dữ liệu phân tích

```mermaid
flowchart LR
  pg[(Postgres các service)] -->|outbox| k1[ecommerce.*.events]
  pg -->|CDC ods-cdc| k2[ods.*]
  web[Web/app] -->|POST /v1/events| col[clickstream-collector] --> k3[clickstream.events]
  k1 --> flink[Flink stream-processor] --> k4[ecommerce.stream.*] --> fraud & reco[recommendation] & webhook
  k1 & k2 & k3 & k4 --> ls[lake-stream Spark 30s] --> bronze[(bronze)] & ods[(ods)]
  bronze -->|decode protobuf| silver[(silver)]
  ods -->|dbt hằng giờ + test| silver -->|dbt| gold[(gold)]
  gold --> trino[Trino lakehouse_ro] --> superset[Superset] & feast[Feast → Redis]
  ls & silver & gold -.->|OpenLineage| marquez[Marquez]
  gold -.->|metadata| om[OpenMetadata]
```

### 7.6 Luồng code tới production

```mermaid
flowchart LR
  dev[Dev push PR] --> ci[GitHub Actions<br/>ci-go/java/node/python + contracts + security]
  ci -->|merge main| img[Build image ko/Jib/Docker<br/>SBOM Syft, Trivy, Cosign ký, SLSA provenance]
  img --> reg[(Registry tag sha12)]
  reg --> kargo[Kargo Freight]
  kargo -->|tự động| devenv[dev] -->|tự động| testenv[test] -->|tự động| stg[staging] -->|mở PR| prod[prod]
  prod --> argo[ArgoCD sync] --> ro[Argo Rollouts canary<br/>phân tích slo-burn-rate]
  ro -->|burn > 14.4x| rb[rollback tự động]
  kyv[Kyverno] -. chặn image thiếu chữ ký/SBOM/provenance .-> argo
```

---

## 8. Business logic quan trọng (kèm sơ đồ)

### 8.1 Phiên checkout: trạng thái

File: `services/checkout-service/.../domain/model/SessionStatus.java`, `CheckoutSession.java`. Phiên lưu JSONB,
khoá lạc quan, hết hạn sau `CHECKOUT_SESSION_TTL` (mặc định 30 phút; phiên flash sale hết hạn cùng claim).

```mermaid
stateDiagram-v2
  [*] --> OPEN: POST /checkout/sessions\n(hoặc tự mở khi nhận FlashSaleClaimed)
  OPEN --> OPEN: PATCH đổi item/voucher/địa chỉ/ship → báo giá lại
  OPEN --> PLACED: place + fraud APPROVE → order-service tạo đơn
  OPEN --> PENDING_REVIEW: place + fraud REVIEW
  OPEN --> REJECTED: fraud REJECT / sản phẩm không bán được
  OPEN --> EXPIRED: quá hạn
  OPEN --> CANCELLED: khách huỷ
  PENDING_REVIEW --> PLACED: FraudReviewDecided approved
  PENDING_REVIEW --> REJECTED: FraudReviewDecided rejected
  PLACED --> [*]
  REJECTED --> [*]
  EXPIRED --> [*]
  CANCELLED --> [*]
```

### 8.2 Báo giá và đặt đơn

File: `CheckoutService.requote()` và `place()`.

```mermaid
flowchart TB
  start([place: sessionId, paymentMethod, token, expectedTotal]) --> st{trạng thái phiên}
  st -->|PLACED / PENDING_REVIEW| ret[trả kết quả cũ: gọi lại an toàn]
  st -->|OPEN| rq[requote]
  subgraph requote[requote: luôn chạy lại ngay trước khi đặt]
    c1[catalog: sản phẩm còn bán? vượt maxQuantityPerOrder?] --> c2[pricing.quote: giá theo nhóm khách, lịch giảm giá]
    c2 --> c3[promotion.quote: giảm giá ≤ tạm tính]
    c3 --> c4[shipping.quote theo địa chỉ + cân nặng + tiền sau giảm]
    c4 --> c5[tax.calculate: giảm giá phân bổ theo tỷ lệ dòng, phần lẻ dồn dòng cuối]
  end
  rq --> c1
  c5 --> tot["tổng = tạm tính − giảm giá + phí ship + thuế"]
  tot --> chk{expectedTotal khớp?}
  chk -->|không| pc[409 PRICE_CHANGED]
  chk -->|có| fr["fraud.assess (timeout 300ms, sập thì cho qua: FRAUD_FAIL_OPEN)"]
  fr -->|REJECT| rj[422 FRAUD_REJECTED]
  fr -->|REVIEW| rv[202 PENDING_REVIEW]
  fr -->|APPROVE| po["gRPC OrderService.PlaceOrder<br/>giá đã chốt, order id = id sinh trước của phiên"]
  po --> pl[PLACED]
```

Chống quá tải: `PlaceLimiter` giới hạn lượt `place` đồng thời mỗi pod (`CHECKOUT_PLACE_MAX_CONCURRENT`, mặc định 16);
quá ngưỡng trả `503 CHECKOUT_BUSY` + `Retry-After`. Client gửi lại cùng `Idempotency-Key` (lease 60s, `libs/java/platform/idempotency`).
`PlaceOrder` idempotent nhờ order id cố định: gọi lại trả đơn đã có.

### 8.3 Saga checkout (Temporal)

File: `services/checkout-service/.../adapter/in/workflow/CheckoutWorkflowImpl.java`; activity ở
`order-service/.../CheckoutActivitiesImpl.java` và `payment-service/.../PaymentActivitiesImpl.java`;
hợp đồng ở `libs/java/workflow-contracts`.

- order-service lưu đơn `PENDING` rồi start workflow id `checkout-<orderId>` trên queue `checkout-workflow`
  (reuse policy `ALLOW_DUPLICATE_FAILED_ONLY`: start lại là no-op). Start lỗi thì `CheckoutRecovery` quét đơn
  PENDING quá 1 phút và start lại.
- Worker workflow chạy ở checkout-service; activity đơn/kho chạy ở order-service (queue `checkout`), thanh toán ở
  payment-service (queue `payment`).

```mermaid
sequenceDiagram
  autonumber
  participant O as order-service
  participant W as CheckoutWorkflow (checkout-service)
  participant PR as promotion
  participant I as inventory
  participant P as payment
  participant G as Cổng (Stripe/PayPal)

  O->>W: start checkout-{orderId}
  opt đơn có voucher
    W->>W: đăng ký bù trừ cancelPromotionRedemption
    W->>PR: redeemPromotion (qua activity order-service)
  end
  W->>W: đăng ký bù trừ releaseInventory
  W->>I: reserveInventory (reservation checkout-{orderId}, hạn = timeout thanh toán + margin)
  W->>W: đăng ký bù trừ refund
  W->>P: authorize
  alt ACTION_REQUIRED (3DS, PayPal, Affirm/Klarna/Afterpay)
    W-->>O: query status trả redirect URL cho frontend
    G-->>P: webhook / return URL
    P->>W: signal paymentCompleted
    Note over W: timer bền vững 15 phút, hết hạn hoặc signal cancel → bù trừ
  end
  W->>P: capture (bị từ chối vẫn bù trừ được)
  W->>I: commitInventory ★ PIVOT: retry lỗi tạm thời vô hạn
  W->>O: confirmOrder → CONFIRMED, outbox OrderConfirmed
  Note over W: lỗi bất kỳ trước pivot: refund ← release ← cancel voucher, rồi cancelOrder
  Note over W: confirm bị từ chối sau pivot: refund + cancelOrder + log lỗi cho vận hành
```

Vì sao **capture trước commit kho**: tiền hoàn lại được (refund), hàng đã commit thì không trả về kho tự động
(ADR 0021). Bù trừ được đăng ký **trước** khi gọi activity, để activity đã chạy ở phía kia nhưng timeout ở đây vẫn
được trả lại; mọi bù trừ idempotent. Thay đổi thứ tự được bọc `Workflow.getVersion("capture-before-commit")` để
workflow đang chạy dở vẫn replay theo nhánh cũ.

Retry: bước tiến lùi được (`FORWARD_RETRY`) thử 6 lần, backoff 1s→30s, không retry lỗi `REJECTED/DECLINED/UNSUPPORTED_METHOD`;
bù trừ và bước sau pivot (`MUST_COMPLETE_RETRY`) retry không giới hạn, backoff tới 5 phút.

### 8.4 Trạng thái đơn hàng và thanh toán

```mermaid
stateDiagram-v2
  direction LR
  state "Order (order-service)" as O {
    [*] --> PENDING
    PENDING --> RESERVED: reserveInventory
    RESERVED --> CONFIRMED: confirmOrder
    PENDING --> CANCELLED: bù trừ
    RESERVED --> CANCELLED: bù trừ
  }
```

```mermaid
stateDiagram-v2
  direction LR
  [*] --> INITIATED
  INITIATED --> AUTHORIZED
  INITIATED --> PENDING_ACTION: khách ở cổng thanh toán
  INITIATED --> DECLINED
  PENDING_ACTION --> AUTHORIZED
  PENDING_ACTION --> CAPTURED
  PENDING_ACTION --> DECLINED
  PENDING_ACTION --> VOIDED
  AUTHORIZED --> CAPTURED
  AUTHORIZED --> VOIDED: refund trước capture = void
  CAPTURED --> REFUNDED
```

`CONFIRMED` là điểm không quay lại: huỷ sau đó là luồng trả hàng/hoàn tiền, không phải saga. Khách huỷ đơn
(`POST /orders/{code}/cancel`) gửi signal `cancel` cho workflow, chỉ có tác dụng trước capture.
Payment idempotent theo `order_id UNIQUE`. Phương thức (`PaymentMethod.java`): `CARD`, `APPLE_PAY`, `GOOGLE_PAY`
(category CARD/WALLET, qua Stripe), `PAYPAL`, `AFFIRM`, `KLARNA`, `AFTERPAY` (category INSTALLMENT).
`ProviderRegistry` chọn provider theo method; không có khoá thật thì `SandboxProvider` (`PAYMENT_SANDBOX_ENABLED=true`).

### 8.5 Giữ chỗ tồn kho

File: `services/inventory-service/internal/app/reservation.go`.

```mermaid
stateDiagram-v2
  [*] --> ACTIVE: Reserve (all-or-nothing, gọi lại cùng id = idempotent)
  ACTIVE --> COMMITTED: Commit (trừ hẳn tồn kho; có items thì chốt theo items, dư trả về)
  ACTIVE --> RELEASED: Release (bù trừ, gọi lại vẫn OK)
  ACTIVE --> EXPIRED: ReleaseExpired (sweeper định kỳ, RESERVATION_SWEEP_INTERVAL)
```

Tồn kho khả dụng = on hand − đang giữ. Hạn giữ chỗ do order-service gửi (`CHECKOUT_PAYMENT_TIMEOUT` +
`CHECKOUT_RESERVATION_MARGIN`, mặc định 15 + 15 phút) để hàng không tự nhả khi saga còn chờ khách trả tiền.

### 8.6 Flash sale (chống bán lố)

File: `internal/app/flashsale.go`, `adapter/repository/redis/lua/*.lua`, `checkout-service/.../FlashSaleEventsConsumer.java`.
ADR 0007, 0018, 0021.

Ý tưởng: khoá dòng tồn kho trên Postgres cho mỗi lượt bấm sẽ thành nút cổ chai. Nên số hàng của đợt sale được
**giữ một lần trong Postgres** lúc mở, rồi **phân phối bằng Lua nguyên tử trên Redis**; chỉ khi đóng sale mới đối
soát lại Postgres.

```mermaid
sequenceDiagram
  autonumber
  participant A as Admin
  participant C as Khách
  participant INV as inventory-service
  participant PG as Postgres
  participant RD as Redis (Lua)
  participant K as Kafka
  participant CK as checkout-service

  A->>INV: POST .../start (opensAt, admitPerSecond, admitBurst) [ADMIN]
  INV->>PG: reservation FLASH_SALE flashsale:{sale}:{product} (hàng không bán kênh khác được)
  INV->>RD: nạp hash trạng thái (remaining, limit, opens_at, admit_rate...)
  C->>INV: POST .../queue (xếp hàng phòng chờ)
  loop theo retryAfterSeconds (2–30s)
    C->>INV: GET .../queue → canClaim?
  end
  C->>INV: POST .../claim {quantity}
  INV->>RD: claim.lua (nguyên tử)
  RD-->>INV: còn lại / -1 hết / -2 vượt giới hạn / -3 chưa mở / -4,-5 chưa tới lượt / -6 chưa tới giờ
  INV->>K: FlashSaleClaimed (gửi thẳng, không qua outbox)
  INV-->>C: claimId = checkoutSessionId, expiresAt (FLASH_CLAIM_TTL 10 phút)
  K->>CK: mở phiên checkout id = claim id, chỉ chứa món đã giành
  C->>CK: GET /checkout/sessions/{id} (404 vài trăm ms đầu thì hỏi lại), PATCH địa chỉ, POST place
  Note over CK,INV: saga gọi Reserve/Commit/Release với checkout-{claimId}<br/>inventory nhận ra là claim → CONFIRMED / COMMITTED / trả suất trên Redis, không giữ thêm ở Postgres
  Note over INV,RD: sweep.lua mỗi 5s trả suất của claim PENDING quá hạn
  A->>INV: POST .../end [ADMIN] (409 nếu còn claim dở)
  INV->>PG: commit đúng số đã bán, phần dư trả về kho
```

Logic của `claim.lua` (đọc file để xem đầy đủ):

```mermaid
flowchart TB
  s([claim customer, qty]) --> a{sale đã nạp?}
  a -->|không| r3[-3]
  a --> b{now ≥ opens_at?}
  b -->|không| r6[-6]
  b --> c{admit_rate > 0?}
  c -->|có| d{có trong hàng chờ?}
  d -->|không| r4[-4]
  d --> e{"thứ hạng < burst + (now − opens)·rate/1000?"}
  e -->|không| r5[-5 chưa tới lượt]
  c -->|không| f
  e --> f{đã mua + qty ≤ limit mỗi khách?}
  f -->|không| r2[-2]
  f --> g{remaining ≥ qty?}
  g -->|không| r1[-1 hết hàng]
  g --> h[tăng số đã mua, tạo claim PENDING có deadline,<br/>ZADD hàng đợi hết hạn, trừ remaining] --> ok[trả remaining mới]
```

Gửi `FlashSaleClaimed` lỗi: broker từ chối hẳn (`ErrClaimNotPublished`) thì trả suất ngay; lỗi mơ hồ (timeout) thì
giữ claim và gửi lại nền tới gần `expiresAt` (event có thể đã lên Kafka, nhả suất sẽ bán lố). checkout-service bỏ
qua bản trùng theo claim id. Redis lỗi khi đang có sale: saga thử lại, không đoán đơn nào là flash sale; không có
sale nào chạy thì đơn thường không phụ thuộc Redis. Test: `TestFlashSaleNeverOversells` (200 lượt tranh 20 suất →
đúng 20).

### 8.7 Chấm điểm gian lận

File: `services/fraud-service/app/domain/rules.py`. Điểm 0–100: ≥ 80 `REJECT`, ≥ 50 `REVIEW` (duyệt tay qua
`/api/ecommerce/fraud`), còn lại `APPROVE`. Blocklist luôn REJECT.

```mermaid
flowchart LR
  in[đơn + ngữ cảnh: email, IP, thiết bị,<br/>phương thức, tuổi tài khoản, địa chỉ] --> bl{blocklist?}
  bl -->|có| rej[REJECT 100]
  bl -->|không| sum[cộng điểm các luật]
  sum --> v["velocity (Redis): VELOCITY_CUSTOMER +25/+45, VELOCITY_IP +20,<br/>DEVICE_MANY_ACCOUNTS +30, CARD_TESTING +35, MANY_ADDRESSES +15"]
  sum --> h["lịch sử (Postgres từ event): PAYMENT_FAILURES +25, FREQUENT_REFUNDS +15,<br/>RETURNED_SHIPMENTS, TRUSTED_CUSTOMER −15"]
  sum --> st["feature Flink: STREAM_PAYMENT_FAILURES +20, STREAM_ORDER_BURST +15,<br/>STREAM_CANCELLATIONS +15, STREAM_ADDRESS_HOPPING +15"]
  sum --> amt["giá trị: NEW_ACCOUNT_HIGH_VALUE +20, AMOUNT_ANOMALY +20,<br/>FIRST_ORDER_HIGH_VALUE +25, NEW_ADDRESS_HIGH_VALUE +10, HIGH_QUANTITY +10"]
  sum --> oth["COUNTRY_MISMATCH, WALLET_AUTHENTICATED −5"]
  v & h & st & amt & oth --> score{tổng điểm}
  score -->|≥ 80| R[REJECT]
  score -->|≥ 50| RV[REVIEW → FraudReviewDecided]
  score -->|< 50| AP[APPROVE]
```

Kết quả có danh sách lý do (giải thích được). Feature stream cũ hơn 1 giờ bị bỏ qua, nên fraud vẫn chạy đúng khi
Flink dừng.

### 8.8 Giỏ hàng xoá đúng lúc

File: `services/cart-service/internal/app/service.go`.

```mermaid
sequenceDiagram
  participant K as Kafka ecommerce.order.events
  participant C as cart-service
  K->>C: OrderPlaced (đơn còn PENDING)
  C->>C: chỉ ghi nhớ sản phẩm vào pending_order_clears
  alt thanh toán + kho OK
    K->>C: OrderConfirmed
    C->>C: xoá các sản phẩm đó khỏi giỏ mặc định
  else huỷ
    K->>C: OrderCancelled
    C->>C: bỏ ghi nhớ, giỏ giữ nguyên
  end
```

Trước ADR 0021, giỏ bị xoá ngay khi `OrderPlaced`; đơn bị huỷ thì khách mất giỏ.

### 8.9 Read model trang sản phẩm (CQRS)

File: `product-service/.../ProductReadModelConsumer.java`, `ProductReadModelService.java`, `ProductDocumentAssembler.java`.

```mermaid
flowchart LR
  subgraph write[Ghi]
    api[REST admin] --> pg[(Postgres products + outbox)]
  end
  pg -->|Debezium| kp[ecommerce.product.events]
  kpr[pricing.events] & krv[review.events] & kin[inventory.events] & kca[catalog.events]
  kp --> cons[consumer group product-read-model]
  kpr & krv & kin & kca --> cons
  cons -->|event product: đọc lại Postgres REPEATABLE READ,<br/>ghi đè nếu coreVersion mới hơn| mongo[(MongoDB collection product)]
  cons -->|event khác: cập nhật từng trường,<br/>mỗi trường một mốc versions.*| mongo
  subgraph read[Đọc]
    page[GET trang chi tiết theo slug] --> mongo
    page -.chưa có document / Mongo lỗi.-> pg
  end
```

Ghi có điều kiện theo mốc thời gian nên event giao lại, sai thứ tự hay dựng lại song song không làm lùi dữ liệu.
Mongo sập thì consumer đứng chờ (không đẩy event vào DLQ), trang vẫn đọc Postgres.

### 8.10 Giá bán (pricing-service)

File: `services/pricing-service/internal/domain/pricing.go`. Giá theo đơn vị nhỏ nhất của tiền tệ.

- Bảng giá `default` (USD) và bảng giá theo nhóm khách (lấy từ role Keycloak).
- Lịch giảm giá loại `SALE`/`FLASH`, theo số tiền hoặc phần trăm, trạng thái `SCHEDULED → ACTIVE → ENDED/CANCELLED`;
  scheduler dùng `FOR UPDATE SKIP LOCKED` nên nhiều pod chạy không trùng.
- Lịch sử giá + giá thấp nhất 30 ngày (để hiển thị "giá gốc" hợp lệ khi khuyến mãi).
- Phát `PriceChanged` qua outbox; seed giá từ `ecommerce.product.events`; cache Redis.

### 8.11 Gợi ý sản phẩm

File: `services/recommendation-service/app/domain/ranking.py`, `embedding.py`. Học hoàn toàn từ event Kafka.

| Loại          | Cách tính                                                                                                |
| ------------- | -------------------------------------------------------------------------------------------------------- |
| Mua kèm       | đồng xuất hiện trong đơn, chuẩn hoá cosine để bớt thiên vị hàng bán chạy                                 |
| Tương tự      | embedding tên + danh mục trong pgvector (mặc định hashing, có thể dùng Ollama `bge-m3`)                  |
| Xu hướng      | tín hiệu có suy giảm theo thời gian; ưu tiên `ecommerce.stream.product-trending` của Flink trong 15 phút |
| Cho riêng bạn | trộn ba nguồn, giới hạn số món mỗi danh mục                                                              |

### 8.12 Phân quyền nhiều lớp và AI agent (MCP)

```mermaid
flowchart LR
  ag[AI agent] -->|OAuth PKCE + consent<br/>scope commerce:read/cart/orders.cancel| kc[Keycloak client commerce-mcp-agent]
  ag -->|Bearer| gw[APISIX rate limit] --> mcp[commerce-mcp-service]
  mcp -->|1. kiểm scope| mcp
  mcp -->|2. OpenFGA check, quan hệ sở hữu gửi dạng contextual tuple, fail-closed| fga[(OpenFGA)]
  mcp -->|3. gọi API hiện có, chuyển tiếp token| svc[cart / order / product / search]
  svc -->|tự kiểm token + quyền sở hữu| svc
```

Không có tool thanh toán; `cancel_order` bắt buộc `confirm=true`.

---

## 9. Cách chạy dự án

### 9.1 Chuẩn bị máy

| Công cụ                                                                                  | Phiên bản                                         | Cần khi                                                                      |
| ---------------------------------------------------------------------------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------- |
| Docker Desktop (hoặc OrbStack/Colima) + Docker Compose v2                                | mới                                               | luôn luôn; cấp ≥ 8 GB RAM cho Docker (≥ 12 GB nếu chạy lakehouse/full stack) |
| `make`, `git`, `curl`, `jq`, Python 3                                                    |                                                   | luôn luôn (`make token` dùng python3)                                        |
| Go                                                                                       | 1.26                                              | sửa/chạy service Go ngoài Docker                                             |
| JDK                                                                                      | 25 (Gradle tự tải toolchain qua foojay nếu thiếu) | sửa/chạy service Java                                                        |
| Node.js + npm                                                                            | Node ≥ 24, npm 11                                 | service NestJS, supergraph, frontend                                         |
| uv                                                                                       | mới                                               | service Python                                                               |
| buf, golangci-lint                                                                       |                                                   | `make proto`, `make lint`                                                    |
| Tuỳ chọn: Stripe CLI, Temporal CLI, `kubectl`, `helm`, `terraform`, `istioctl`, `velero` |                                                   | test webhook thanh toán, triển khai cluster                                  |

Không cần cài Postgres/Kafka/Redis... trên máy: tất cả chạy trong Docker.

### 9.2 Stack local và các profile

File compose: `deploy/local/docker-compose.yaml` (lakehouse ghép thêm `docker-compose.lakehouse.yaml`). Compose chia
**profile** để chỉ bật phần cần dùng:

| Profile                | Container                                                                                                                                                                                      | Ghi chú                                 |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| (lõi, luôn bật)        | postgres (+pgvector), redis, kafka (KRaft), schema-registry (Apicurio), connect (Debezium) + connect-init, keycloak, apisix, unleash, openfga, otel-collector, inventory-service, cart-service | đủ để thử giỏ hàng, tồn kho, flash sale |
| `apps-go`              | tax, shipping, webhook, pricing, search (+elasticsearch), media (+minio)                                                                                                                       |                                         |
| `workflow`             | temporal, temporal-ui                                                                                                                                                                          | bắt buộc cho `apps-java`                |
| `apps-java`            | mongo, product, order, payment, promotion, checkout                                                                                                                                            |                                         |
| `apps-node`            | catalog, customer, review, notification, message, storefront-bff, backoffice-bff, 2 router GraphQL, mailpit                                                                                    | cần build supergraph trước (mục 10.3)   |
| `apps-python`          | translate, chatbot, commerce-mcp, supplier, fraud, recommendation                                                                                                                              | cần `ANTHROPIC_API_KEY` hoặc `ollama`   |
| `observability`        | prometheus, tempo, loki, pyroscope, grafana                                                                                                                                                    |                                         |
| `streaming`            | Flink jobmanager + taskmanager (stream-processor)                                                                                                                                              |                                         |
| `analytics`            | clickhouse, scylla                                                                                                                                                                             |                                         |
| `ollama`               | ollama                                                                                                                                                                                         | LLM tự host cho chatbot/recommendation  |
| `lakehouse`, `airflow` | qua `make lakehouse-up` (mục 12)                                                                                                                                                               |                                         |

Postgres local tạo sẵn các DB `ecommerce` (mọi service, mỗi service một schema), `keycloak`, `temporal`,
`temporal_visibility`, `unleash`, `openfga`, `registry` (`deploy/local/postgres/init.sql`). Mật khẩu `postgres/postgres`.

### 9.3 Chạy lần đầu, từng bước

```bash
# 1. Lấy code và vào platform
cd /Users/admin/Desktop/congthanh-app/backend/app/ecommerce

# 2. Tạo .env (make up tự làm nếu chưa có)
cp .env.example .env

# 3. Bật lõi. Lần đầu build image khá lâu.
make up
make ps                                   # chờ các container healthy
make logs S=inventory-service             # xem log một service

# 4. Lấy token thử (user mẫu trong realm: customer@congthanh.vn / customer, admin@congthanh.vn / admin)
make token USER=customer@congthanh.vn PASS=customer

# 5. Gọi API qua gateway
TOKEN=$(make -s token USER=customer@congthanh.vn PASS=customer)
curl -H "Authorization: Bearer $TOKEN" localhost:9080/api/ecommerce/inventory/levels
```

Muốn chạy **luồng checkout đầy đủ** (giỏ → phiên checkout → saga → thanh toán sandbox):

```bash
make up PROFILES="workflow apps-go apps-java apps-python"
# payment-service local chạy sandbox khi không có khoá Stripe/PayPal (PAYMENT_SANDBOX_ENABLED=true)
```

Muốn có **GraphQL cho frontend**:

```bash
npm ci && npm run build
npm run schema:emit --workspaces --if-present && npm run supergraph
make up PROFILES="workflow apps-go apps-java apps-node"
```

Chạy tất cả (cần máy mạnh): `make up PROFILES="observability workflow apps-go apps-java apps-node apps-python streaming"`.
Dừng: `make down` (giữ volume dữ liệu). Xoá sạch dữ liệu: `docker compose -f deploy/local/docker-compose.yaml down -v`.

### 9.4 Địa chỉ và tài khoản local

| Thành phần                        | Địa chỉ                                                                               | Tài khoản                                                                                                                 |
| --------------------------------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Gateway APISIX                    | http://localhost:9080 (REST `/api/ecommerce/*`, GraphQL `/graphql`, `/admin/graphql`) | token Keycloak                                                                                                            |
| Keycloak admin                    | http://localhost:8180                                                                 | `admin` / `admin`; realm `ecommerce`: `customer@congthanh.vn`/`customer` (CUSTOMER), `admin@congthanh.vn`/`admin` (ADMIN) |
| Temporal UI                       | http://localhost:8233                                                                 | xem từng workflow `checkout-<orderId>`                                                                                    |
| Grafana                           | http://localhost:3000                                                                 | profile `observability`                                                                                                   |
| Prometheus / Pyroscope            | http://localhost:9090 / http://localhost:4040                                         |                                                                                                                           |
| Unleash                           | http://localhost:4242                                                                 | flag `inventory.flash-sale.claim`...                                                                                      |
| Mailpit (email)                   | http://localhost:8025                                                                 | profile `apps-node`                                                                                                       |
| MinIO console                     | http://localhost:9001                                                                 | profile `apps-go` hoặc `lakehouse`                                                                                        |
| Apicurio                          | http://localhost:8085                                                                 |                                                                                                                           |
| Debezium Connect REST             | http://localhost:8083/connectors                                                      |                                                                                                                           |
| OpenFGA                           | http://localhost:8089                                                                 |                                                                                                                           |
| Router GraphQL trực tiếp          | http://localhost:4000 / :4001                                                         |                                                                                                                           |
| Postgres / Redis / Kafka (từ máy) | localhost:5432 / 6379 / 29092                                                         | `postgres`/`postgres`                                                                                                     |
| Mỗi service                       | cổng HTTP trong bảng mục 6                                                            |                                                                                                                           |

Client Keycloak có sẵn: `dev-cli` (password grant, dùng cho `make token`), `storefront-web` (public + PKCE,
redirect `http://localhost:3000/*`), `backoffice-web` (`http://localhost:3001/*`), `apisix-gateway`,
`commerce-mcp-agent`, và service account cho order/notification.

### 9.5 Thử nhanh các luồng nghiệp vụ bằng curl

```bash
TOKEN=$(make -s token USER=customer@congthanh.vn PASS=customer)
ADMIN=$(make -s token USER=admin@congthanh.vn PASS=admin)
H="Authorization: Bearer $TOKEN"

# Giỏ hàng (customerId = claim sub trong token)
curl -H "$H" localhost:9080/api/ecommerce/carts/customer/<sub>/default
curl -X POST -H "$H" -H 'Content-Type: application/json' localhost:9080/api/ecommerce/carts/<cartId>/items -d '{"productId":"<productId>","quantity":1}'

# Phiên checkout (cần profile workflow apps-go apps-java)
curl -X POST -H "$H" -H 'Content-Type: application/json' localhost:9080/api/ecommerce/checkout/sessions \
  -d '{"items":[{"productId":"<id>","quantity":1}]}'
# Thêm địa chỉ (country 2 ký tự; với địa chỉ Mỹ, city mang mã bang) rồi chọn phương thức giao trong shippingOptions trả về
curl -X PATCH -H "$H" -H 'Content-Type: application/json' localhost:9080/api/ecommerce/checkout/sessions/<id> \
  -d '{"shippingAddress":{"fullName":"Jane Doe","phone":"+12125550100","line1":"1 Main St","city":"NY","country":"US","postalCode":"10001"}}'
curl -X PATCH -H "$H" -H 'Content-Type: application/json' localhost:9080/api/ecommerce/checkout/sessions/<id> \
  -d '{"shipping":{"carrier":"<carrier>","serviceCode":"<serviceCode>"}}'
curl -X POST -H "$H" -H "Idempotency-Key: $(uuidgen)" -H 'Content-Type: application/json' \
  localhost:9080/api/ecommerce/checkout/sessions/<id>/place -d '{"paymentMethod":"CARD","paymentToken":"tok_ok"}'
# Sandbox: token bất kỳ được chấp nhận, tok_decline bị từ chối (thử nhánh bù trừ);
# PAYPAL, AFFIRM/KLARNA/AFTERPAY hoặc tok_redirect trả progress.paymentRedirectUrl tới trang sandbox để bấm Trả/Huỷ.
curl -H "$H" localhost:9080/api/ecommerce/checkout/sessions/<id>      # xem status, orderCode, progress.step
# Theo dõi saga: Temporal UI http://localhost:8233, workflow checkout-<orderId>

# Flash sale (admin mở, khách xếp hàng + giành)
curl -X POST -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  localhost:9080/api/ecommerce/flash-sales/s1/products/<productId>/start \
  -d '{"quantity":20,"perCustomerLimit":1,"admitPerSecond":5,"admitBurst":10}'
curl -X POST -H "$H" localhost:9080/api/ecommerce/flash-sales/s1/products/<productId>/queue
curl -H "$H" localhost:9080/api/ecommerce/flash-sales/s1/products/<productId>/queue      # tới khi canClaim=true
curl -X POST -H "$H" -H 'Content-Type: application/json' \
  localhost:9080/api/ecommerce/flash-sales/s1/products/<productId>/claim -d '{"quantity":1}'
```

Body chính xác của từng API xem Swagger của service Java (`http://localhost:<port>/swagger-ui.html`), OpenAPI trong
`api/openapi/`, handler trong `adapter/http` (Go) hoặc DTO trong `adapter/in/web` (Java).

### 9.6 Frontend ecommerce

Next.js 15 + React 19 ở `frontend/ecommerce`. Gọi gateway (`/graphql` + REST), đăng nhập bằng NextAuth với Keycloak
(client `storefront-web`).

```bash
cd frontend/ecommerce
cp .env.example .env.local          # NEXT_PUBLIC_GATEWAY_URL=http://localhost:9080, KEYCLOAK_ISSUER=http://localhost:8180/realms/ecommerce
                                    # đặt NEXTAUTH_SECRET (vd. openssl rand -base64 32)
npm install
npm run dev                          # http://localhost:3000
npm run check-types && npm run lint && npm test
npm run test:e2e:playwright          # e2e
npm run graphql:check                # kiểm query GraphQL khớp supergraph
```

Cấu trúc chính: `src/app/[locale]/` (route theo ngôn ngữ: product, category, cart, checkout, payment, order, wishlist,
notification, supplier, manage, auth), `src/features/` (theo tính năng), `src/components/` (UI dùng chung, shadcn
ở `components/ui`), `src/graphql/` (client, documents, types), `src/api/` (REST), `src/store/` (zustand),
`src/locales/` + `src/i18n/` (next-intl), `src/config/` (axios, providers).

Lưu ý: Grafana (profile `observability`) cũng dùng cổng 3000. Khi chạy frontend thì không bật profile
`observability` cùng lúc, hoặc tạm đổi cổng Grafana trong compose; Keycloak chỉ cho redirect về
`http://localhost:3000/*` nên không nên đổi cổng frontend.

---

## 10. Phát triển từng loại service

### 10.1 Service Go

Go 1.26, workspace `go.work`. Test tích hợp tự bật Postgres/Redis bằng Testcontainers (cần Docker), qua
`libs/go/platform/testkit` (`testkit.PostgresURL(t)`, `testkit.RedisURL(t)` hoặc `dbtest.New`). Đặt
`TEST_DATABASE_URL`/`TEST_REDIS_URL` thì dùng DB đó thay vì container (CI dùng service container). Không có Docker:
local thì skip, CI (biến `CI`) thì fail.

```bash
cd services/<svc> && cp .env.example .env && go run ./cmd/server   # migration goose tự chạy khi khởi động
make test-go                                      # vet + test -race mọi module trong GO_MODULES
make lint                                         # golangci-lint + Biome/dependency-cruiser
go test ./...                                     # trong một module
```

- Chạy ngoài Docker nhưng dùng hạ tầng của `make up`: `DATABASE_URL=postgres://postgres:postgres@localhost:5432/ecommerce?sslmode=disable`,
  `KAFKA_BROKERS=localhost:29092`, `JWT_JWKS_URL=http://localhost:8180/realms/ecommerce/protocol/openid-connect/certs`
  (một số `.env.example` còn ghi DB/realm cũ, hãy sửa theo giá trị này).
- Xác thực fail-closed: thiếu cả `JWT_JWKS_URL` lẫn `JWT_SECRET` thì service không khởi động. Chạy local không cần
  token thì đặt `AUTH_DISABLED=true` (chỉ dev/test). Phân quyền route dùng `auth.RequireRole(verifier, "ADMIN", ...)`
  của `libs/go/platform/auth`, không tự viết `requireAdmin` trong từng service.
- `domain` không được import hạ tầng; use case trong `app` chỉ biết port.
- Image: `ko` (CI) hoặc `docker build -f services/<svc>/Dockerfile .`. Service mới: template Backstage
  `tools/backstage/templates/go-service`, thêm vào `go.work`, `GO_MODULES` trong Makefile, `deploy/helm/values/<svc>`.

### 10.2 Service Java

Java 25, Spring Boot 4, Gradle (Liquibase cho migration, Temporal cho saga, ArchUnit kiểm luật lục giác).

```bash
./gradlew :services:<svc>:bootRun                 # cấu hình qua biến môi trường trong application.yaml
./gradlew test                                    # hoặc make test-java
./gradlew :services:<svc>:test --tests '*CheckoutWorkflowTest'
./gradlew :services:<svc>:jibDockerBuild          # image local không cần Dockerfile (CI dùng :jib để push)
```

- checkout-service chạy worker `CheckoutWorkflow`; activity đơn hàng ở order-service, thanh toán ở payment-service.
  Interface dùng chung trong `libs/java/workflow-contracts`. Đổi logic workflow thì bọc bằng `Workflow.getVersion`.
  Test saga bằng `TestWorkflowEnvironment` (time-skipping).
- Thị trường mặc định là Mỹ: USD, giờ America/New_York, thuế bán hàng theo bang, phí ship tính bằng cent (ADR 0015).
- payment-service bật cổng nào có khoá: `STRIPE_API_KEY` + `STRIPE_WEBHOOK_SECRET`, `PAYPAL_CLIENT_ID` +
  `PAYPAL_CLIENT_SECRET` (mặc định sandbox PayPal). Không có khoá thì `PAYMENT_SANDBOX_ENABLED=true`: token
  `tok_decline` bị từ chối, PayPal hoặc `tok_redirect` đi qua trang sandbox. Stripe webhook local:
  `stripe listen --forward-to localhost:9080/api/ecommerce/payment/webhooks/stripe`. Frontend Stripe gửi
  ConfirmationToken `ctoken_...`; `PAYPAL` bắt buộc `returnUrl`.
- `libs/java/common-core` là nền chung, tự bật qua auto-configuration (ADR 0022). Lỗi nghiệp vụ kế thừa
  `DomainException` (khai `errorKind()` và `code()`), không cần viết handler: HTTP trả RFC 9457 với `code` và
  `type`, gRPC trả status tương ứng kèm trailer `error-code`. Service chỉ viết `@RestControllerAdvice` cho lỗi
  đặc thù và đặt `@Order(0)` để đứng trước handler chung. Kèm theo: access log (logger `http.access`, không ghi
  header/query/body), `BaseEntity` + JPA auditing, `AuthenticationUtils`. Cấu hình bằng `common.*`:
  `common.logging.requests-enabled`, `common.logging.slow-query-threshold` (mặc định 500ms),
  `common.cors.allowed-origins` (để trống thì CORS do APISIX lo), `common.audit.system-auditor`,
  `common.problems.type-base`.
- Chạy ngoài K8s có thể gắn OTel Java agent ở `tools/agents/` (file .jar không commit).
- Thêm service Spring: thư mục `services/<svc>` với `build.gradle.kts` dùng plugin `congthanh.spring-service`,
  `include(":services:<svc>")` trong `settings.gradle.kts`, Liquibase changelog, values Helm.

### 10.3 Service Node (NestJS) và GraphQL

Node 24, npm workspaces.

```bash
npm ci && npm run build                     # proto → shared → platform → clients → service
npm test                                    # Vitest; không có TEST_DATABASE_URL thì tự bật Postgres (Testcontainers)
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

| Graph      | Subgraph                                                         | Router                          | Qua APISIX       |
| ---------- | ---------------------------------------------------------------- | ------------------------------- | ---------------- |
| storefront | storefront-bff, catalog, customer, review, notification, message | graphql-router :4000            | `/graphql`       |
| backoffice | backoffice-bff, catalog, review, notification, message           | graphql-router-backoffice :4001 | `/admin/graphql` |

```bash
npm run schema:emit --workspaces --if-present && npm run supergraph   # → platform/graphql/<graph>/config.json + supergraph.graphql
npm run supergraph:check                                              # CI: compose được và supergraph.graphql khớp
```

Review diff `supergraph.graphql` để phát hiện thay đổi phá tương thích; muốn bỏ trường thì `@deprecated` trước.
Thêm trường vào `Product` từ subgraph khác chỉ cần `@ResolveField` trên `ProductRef`.

**Cấu hình kênh notification-service:** `SMTP_URL`/`MAIL_FROM` (local: Mailpit `smtp://localhost:1025`),
`SMS_WEBHOOK_URL`/`SMS_API_KEY` (trống = chỉ ghi log), `VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY`
(`npx web-push generate-vapid-keys`), `FCM_SERVICE_ACCOUNT_JSON`, `KEYCLOAK_CLIENT_SECRET`.
Kênh thiếu cấu hình thì lượt gửi bị `SKIPPED`.

**Code TypeScript từ proto** (`@ecommerce/proto`, Protobuf-ES v2): không commit, sinh bằng
`npm run build -w @ecommerce/proto` vào `libs/ts/proto-gen/src/gen/`.

### 10.4 Service Python

Python 3.13, quản lý bằng `uv`; image chung `services/python.Dockerfile` (`--build-arg SERVICE=<svc>`).

```bash
cd services/<svc>
uv sync --all-groups                        # tạo .venv theo uv.lock
uv run python -m app                        # FastAPI (cổng lấy từ HTTP_PORT); supplier: uv run python manage.py runserver
uv run ruff check . && uv run ruff format --check .
TEST_DATABASE_URL=postgresql://postgres:postgres@localhost:5432/ecommerce \
TEST_REDIS_URL=redis://localhost:6379/15 uv run pytest
```

- apps-python cần `ANTHROPIC_API_KEY` (hoặc `CHATBOT_LLM_PROVIDER=ollama` + profile `ollama`, lần đầu:
  `docker compose -f deploy/local/docker-compose.yaml exec ollama ollama pull mistral`).
- fraud-service và recommendation-service tự chạy migration SQL khi khởi động (schema `DB_SCHEMA`).
- recommendation-service cần pgvector (`VECTOR_ENABLED=false` để tắt); embedding mặc định `hashing`,
  đổi sang `EMBEDDINGS_PROVIDER=ollama` + `EMBEDDING_MODEL=bge-m3` để có embedding ngữ nghĩa.
- Code protobuf Python (`libs/python/ecommerce-proto`) được commit; sinh lại bằng `make proto-python`.
- commerce-mcp-service bắt buộc có `JWT_JWKS_URL` và `OPENFGA_STORE_ID` (local: `make fga-init` in ra store id và
  model id, đặt `OPENFGA_STORE_ID`/`OPENFGA_MODEL_ID` vào `.env`).
- chatbot-service: `LLM_PROVIDER=vllm` gọi model tự host trên KServe (`VLLM_URL`, `VLLM_PATH`, `VLLM_MODEL`).

---

## 11. Contract, code sinh, kiểm tra trước khi push

```bash
make proto      # buf lint + buf generate (Go) + sinh Python
make gen        # route APISIX (từ platform/gateway/routes.yaml), AsyncAPI (từ proto event), pipeline Kargo, supergraph
make check      # như CI: buf lint, file sinh còn khớp, supergraph:check
make fga-test   # test model OpenFGA (platform/authz/openfga/tests.fga.yaml)
make test       # test-go + test-java + test-node
make lint
```

| Bạn đổi                         | Phải chạy                                                | Commit kèm                                                                      |
| ------------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `api/proto/**`                  | `make proto`                                             | `libs/go/proto`, `libs/python/ecommerce-proto`, `api/asyncapi` (qua `make gen`) |
| `platform/gateway/routes.yaml`  | `make gen`                                               | `platform/gateway/apisix/apisix.yaml`, `apisix/k8s/routes.yaml`                 |
| resolver/type GraphQL           | `npm run schema:emit ... && npm run supergraph`          | `services/<svc>/schema.graphql`, `platform/graphql/<graph>/*`                   |
| thêm `deploy/helm/values/<svc>` | `make gen`                                               | `platform/delivery/kargo/pipeline.yaml`                                         |
| schema DB                       | migration mới (goose / Liquibase / Drizzle / SQL Python) | file migration, không sửa migration cũ                                          |

- `buf breaking` trong CI chặn thay đổi phá tương thích proto; Schema Registry (Apicurio, rule BACKWARD) chặn ở runtime.
- OpenAPI (`api/openapi/*.yaml`) lint bằng Spectral và so breaking bằng `oasdiff`.
- Event: ghi vào bảng `outbox` cùng transaction, Debezium đẩy sang topic `ecommerce.<miền>.events`; consumer
  idempotent qua `processed_events` (khoá `(event_id, consumer)`). Topic khai báo bằng Strimzi `KafkaTopic` trong
  `platform/messaging/kafka/`; connector local trong `platform/messaging/debezium/local/`.
- Consumer Go (`libs/go/platform/kafka`) và TS (`KafkaEventConsumer`) thử lại có backoff, quá số lần thì đẩy record sang
  `<topic>.dlq` kèm header `x-dlq-*` rồi đi tiếp; chỉ khi không ghi được DLQ mới dừng partition đó và đọc lại.
- Janitor dọn `outbox` sau 1 giờ và `processed_events` sau 14 ngày (Java chỉnh bằng `platform.outbox.retention`,
  `platform.outbox.processed-retention`).
- `Idempotency-Key` (Java): request đầu giữ key với lease `platform.idempotency.lease` (mặc định 60s); pod chết giữa
  chừng thì request gửi lại cùng payload tiếp quản sau khi hết lease.

---

## 12. Data lakehouse

Kafka → Spark → Iceberg (MinIO) ← Trino, silver/gold bằng dbt, điều phối bằng Airflow; thêm BI (Superset), lineage
(Marquez), feature store (Feast), data catalog (OpenMetadata). Kiến trúc và lý do chọn:
`backend/app/ecommerce/docs/adr/0013-data-lakehouse.md` (nền) và `0023-lakehouse-dbt-bi-lineage-features-catalog.md`
(giai đoạn 2). Lệnh chạy từ `backend/app/ecommerce`.

```
nguồn                         Kafka topic                     Iceberg (catalog "lakehouse" trong Polaris)
outbox (Debezium)        →  ecommerce.<miền>.events  ─┐
Flink stream-processor   →  ecommerce.stream.*       ─┤
CDC bảng nghiệp vụ       →  ods.<schema>.<bảng>      ─┼─ lake-stream (Spark, 30s) → bronze.*  (thô, append)
clickstream-collector    →  clickstream.events       ─┘                           → ods.*     (MERGE, trạng thái hiện tại)
Airflow hằng giờ: decode_events (Spark, protobuf → silver.domain_events/stream_signals)
                  → dbt build (silver.* → gold.* + 60 test) → Feast materialize, dbt docs
                  ∥ dbt source freshness
gold/silver ← Trino lakehouse_ro (chỉ đọc) ← Superset, Feast      lineage: Spark, dbt, Airflow → Marquez
```

| Tầng   | Bảng                                                                                            | Ai ghi, khi nào                                         |
| ------ | ----------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| bronze | `outbox_events`, `stream_signals`, `cdc_changes`, `clickstream_events`                          | Spark streaming, chỉ append                             |
| ods    | `orders`, `order_lines`, `payments`, `products`                                                 | Spark streaming MERGE theo khoá + LSN, trễ ~30 giây     |
| silver | `domain_events`, `stream_signals`                                                               | Spark `decode_events.py`: protobuf → cột + payload JSON |
| silver | `orders`, `order_lines`, `payments`, `products`, `clickstream_events`, `order_events`           | dbt: chuẩn kiểu, khử trùng lặp, bỏ PII, lọc bot         |
| gold   | `daily_sales`, `product_daily_sales`, `funnel_daily`, `customer_features`, `order_status_daily` | dbt sau silver                                          |
| gold   | `product_trending_current`, `stock_cover_current`, `customer_risk_current`                      | dbt, bản mới nhất từ Flink                              |

**Chạy** (file compose `deploy/local/docker-compose.lakehouse.yaml` ghép vào compose chính). Profile thêm vào
`LAKEHOUSE_PROFILES`: `airflow` (~1.5 GB RAM), `bi` (Superset), `lineage` (Marquez, tự bật gửi lineage), `ml` (Feast),
`catalog` (OpenMetadata, ~3 GB RAM).

```bash
make lakehouse-up                                                  # lõi + MinIO, Polaris, Spark, Trino, collector
make lakehouse-up PROFILES="workflow apps-java"                    # + service Java để có dữ liệu ODS
make lakehouse-up LAKEHOUSE_PROFILES="lakehouse airflow bi lineage ml"   # đủ bộ trừ catalog
make lakehouse-simulate SESSIONS=300                               # gửi clickstream giả qua collector
make lakehouse-batch                                               # decode protobuf + dbt build ngay (không cần Airflow)
make lakehouse-dbt ARGS="source freshness"                         # lệnh dbt bất kỳ: "test -s gold", "build -s +daily_sales"
make lakehouse-sql                                                 # Trino CLI: SELECT * FROM gold.daily_sales;
make lakehouse-dashboards                                          # nạp lại dashboard Superset
make lakehouse-catalog                                             # nạp lại metadata vào OpenMetadata
make lakehouse-cdc                                                 # đăng ký lại connector ods-cdc sau khi bật apps-java
make lakehouse-stop                                                # dừng riêng phần lakehouse
```

| Địa chỉ                     | Dùng để                                                                                  | Đăng nhập (chỉ local)                                                      |
| --------------------------- | ---------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| http://localhost:8088       | Trino; JDBC `jdbc:trino://localhost:8088/lakehouse` (ghi) hoặc `/lakehouse_ro` (chỉ đọc) | user bất kỳ                                                                |
| http://localhost:8092       | Superset: dashboard Doanh thu, Funnel, Realtime (Flink)                                  | `admin` / `admin`                                                          |
| http://localhost:8090       | Airflow: DAG `lakehouse_silver_gold`, `lakehouse_maintenance`                            | không cần                                                                  |
| http://localhost:3100       | Marquez UI (API http://localhost:5100)                                                   | không cần                                                                  |
| http://localhost:8585       | OpenMetadata: tìm bảng/cột, mô tả và test dbt, lineage                                   | `admin@open-metadata.org` / `admin`                                        |
| http://localhost:6566       | Feast feature server (`POST /get-online-features`)                                       | không cần                                                                  |
| http://localhost:8181       | Polaris (REST catalog)                                                                   | `root`/`s3cr3t` (quản trị), `etl`/`etl-s3cr3t`, `analyst`/`analyst-s3cr3t` |
| http://localhost:4041, 4042 | Spark UI streaming, Spark Connect (`sc://localhost:15002`)                               |                                                                            |
| http://localhost:9001       | MinIO console, bucket `lakehouse`                                                        | `minioadmin` / `minioadmin`                                                |
| http://localhost:5029       | clickstream-collector                                                                    |                                                                            |

| Thư mục / file                                         | Nội dung                                                                                                                                  |
| ------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `data/lakehouse/jobs/stream_ingest.py`                 | job streaming Kafka → bronze + ods (container `lake-stream`)                                                                              |
| `data/lakehouse/jobs/ods_tables.py`                    | bảng ODS: nguồn, khoá, kiểu cột — thêm bảng mới ở đây và trong `ods-cdc.json`                                                             |
| `data/lakehouse/jobs/decode_events.py`                 | giải mã protobuf `EventEnvelope` (descriptor build từ `api/proto` trong image Spark)                                                      |
| `data/lakehouse/jobs/maintenance.py`                   | compact file, dọn delete file, expire snapshot                                                                                            |
| `data/lakehouse/dbt/`                                  | project dbt: `models/sources.yml` (nguồn + freshness), `models/silver`, `models/gold`, test trong `_*.yml` và `tests/generic`             |
| `data/lakehouse/airflow/dags/lakehouse.py`             | DAG decode → dbt build → Feast + dbt docs, freshness song song; bảo trì hằng ngày                                                         |
| `data/lakehouse/feast/`                                | feature repo: `customer_rfm`, `customer_behaviour`, feature service `customer_purchase_v1`                                                |
| `data/lakehouse/sql/trino-examples.sql`                | truy vấn mẫu, time travel, catalog chỉ đọc                                                                                                |
| `data/lakehouse/tools/simulate_clickstream.py`         | sinh clickstream giả                                                                                                                      |
| `deploy/local/lakehouse/`                              | Dockerfile Spark/Airflow/dbt/Superset/Feast/OpenMetadata ingest, catalog Trino, init Polaris (RBAC), cấu hình Marquez, dashboard Superset |
| `platform/messaging/debezium/local/ods-cdc.json`       | connector CDC cho ODS (đã loại cột nhạy cảm)                                                                                              |
| `api/jsonschema/clickstream/v1/clickstream-event.json` | schema event clickstream (đăng ký ở Apicurio, group `clickstream`)                                                                        |
| `data/ingestion/clickstream-collector`                 | service Go nhận `POST /v1/events` (tối đa 100 event/lô)                                                                                   |

Gửi event từ frontend (`occurred_at` phải lệch không quá 24 giờ so với giờ server, `event_id` là UUID để khử trùng lặp):

```bash
curl -X POST localhost:5029/v1/events -H 'Content-Type: application/json' -d '{"events":[{
  "event_id":"'"$(uuidgen)"'","event_type":"add_to_cart","occurred_at":"'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'",
  "session_id":"s-1","anonymous_id":"a-1","product_id":"p-1","quantity":1,"price":"19.99","currency":"USD"}]}'
```

Lấy feature cho model (sau khi bật profile `ml` và DAG đã chạy):

```bash
curl -X POST localhost:6566/get-online-features -H 'Content-Type: application/json' \
  -d '{"feature_service":"customer_purchase_v1","entities":{"customer_id":["customer-1"]}}'
```

Thêm bảng hoặc chỉ số mới:

- Model silver/gold mới: thêm file `.sql` trong `data/lakehouse/dbt/models/<tầng>/`, khai báo cột + test trong `_<tầng>.yml`,
  chạy `make lakehouse-dbt ARGS="build -s <tên_model>+"`. Bảng mới hiện ngay ở Trino; muốn lên dashboard thì thêm vào
  `DATASETS`/`CHARTS` trong `deploy/local/lakehouse/superset/bootstrap_dashboards.py` rồi `make lakehouse-dashboards`.
- Kiểu event protobuf mới không cần sửa Spark: `silver.domain_events` nhận mọi `payload_type`; tách cột trong một model dbt.

Lưu ý:

- ODS cần bảng nguồn do service Java tạo (Liquibase). Khi chưa bật `apps-java`, connector `ods-cdc` chưa chạy được;
  bật xong thì `make lakehouse-cdc`.
- Phân quyền Polaris: Spark, dbt dùng principal `etl` (catalog Trino `lakehouse`); Superset, Feast dùng `analyst`
  (catalog `lakehouse_ro`, chỉ thấy silver/gold). Script `deploy/local/lakehouse/polaris/init.sh` tạo lại được, chạy nhiều lần an toàn.
- Lần đầu bật `bi` trước khi dbt build thì dataset Superset chưa có cột: chạy `make lakehouse-batch` rồi
  `make lakehouse-dashboards`.
- Checkpoint streaming nằm ở volume `lake-checkpoints`. Muốn nạp lại từ đầu Kafka: dừng `lake-stream`, xoá volume rồi chạy lại.
- Image Spark tải sẵn jar Iceberg/Kafka/protobuf/OpenLineage và build descriptor protobuf lúc build; sửa `api/proto` thì
  build lại image Spark (`make lakehouse-up` có `--build`).

---

## 13. Streaming, giao hàng theo SLO, bảo mật runtime, agentic commerce

Quyết định và lý do: `backend/app/ecommerce/docs/adr/0014-streaming-delivery-runtime.md`. Lệnh chạy từ `backend/app/ecommerce`.

**Flink stream-processor** (`data/streaming/stream-processor`, proto `api/proto/ecommerce/stream/v1`):

| Pipeline         | Đọc                          | Ghi (topic compacted)               | Ai dùng                                                      |
| ---------------- | ---------------------------- | ----------------------------------- | ------------------------------------------------------------ |
| customer-risk    | order/payment events         | `ecommerce.stream.customer-risk`    | fraud-service (luật `STREAM_*`, Redis `fraud:stream:cust:*`) |
| product-trending | order, cart, customer events | `ecommerce.stream.product-trending` | recommendation-service (live 15 phút, rồi về batch)          |
| stock-cover      | inventory, order events      | `ecommerce.stream.stock-cover`      | webhook-service (`inventory.stockout_risk`)                  |

```bash
./gradlew :data:streaming:stream-processor:test   # unit + MiniCluster
make up PROFILES="streaming"                       # chạy local (standalone job), PIPELINES=all
```

Trên K8s: `FlinkDeployment` ở `platform/streaming/flink/overlays/<env>` (Flink Kubernetes Operator, checkpoint
`s3://congthanhapp-<env>-flink-state`, Terraform module `flink_state`). Nâng job: đổi image, operator tự lấy savepoint.

**Giao hàng**: chart `microservice` dùng Argo Rollouts + Gateway API (`rollout.trafficRouting: gatewayAPI`) và phân
tích nền `slo-burn-rate` (`platform/delivery/argo-rollouts`). Kargo (`platform/delivery/kargo`, sinh bằng
`python3 tools/scripts/gen-kargo.py`) theo dõi image `<sha12>` của mọi service: dev → test → staging tự promote, prod mở PR. Mỗi service có `values-dev/test/staging/prod.yaml`: test giống dev (1 pod, không canary), staging giống prod nhưng ít pod.
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

---

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

---

## 15. Flash sale (vận hành)

Thiết kế ở ADR 0007 và 0018 (sơ đồ ở mục 8.6); cách vận hành một đợt sale ở `docs/runbooks/flash-sale.md`.

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
- Gửi `FlashSaleClaimed` lỗi: broker từ chối hẳn (`app.ErrClaimNotPublished`) thì trả suất ngay; lỗi mơ hồ (timeout)
  thì giữ claim và gửi lại ở nền tới gần `expiresAt`, vì event có thể đã lên Kafka. checkout-service bỏ qua bản trùng
  theo claim id.
- Mọi thay đổi trạng thái sale là Lua trong `services/inventory-service/internal/adapter/repository/redis/lua/`
  (`claim`, `transition`, `release`, `sweep`, `queue`, `drain`); các script dùng chung thứ tự KEYS.
- Mở/đóng sale (`.../start`, `.../end`) cần role `ADMIN`. `start` nhận thêm `opensAt`, `admitPerSecond`,
  `admitBurst`; `end` trả 409 khi còn claim chưa chốt.
- Đặt đơn quá tải trả `503 CHECKOUT_BUSY` + `Retry-After`; client gửi lại với cùng `Idempotency-Key`
  (`CHECKOUT_PLACE_MAX_CONCURRENT`, mặc định 16 mỗi pod).
- Scale trước theo lịch: thêm khung giờ vào `deploy/helm/events/events-<env>.yaml`, số pod mỗi service ở
  `scheduledScaling.replicas` trong `values-<env>.yaml`.
- Test: `go test ./internal/integration/...` ở inventory-service (tự bật Postgres + Redis bằng Testcontainers),
  load test `tests/load/k6/flash-sale.js`.

---

## 16. Hạ tầng và triển khai (AWS, Kubernetes, GitOps)

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

| Kho                      | Dùng cho                                              | Triển khai                                                  |
| ------------------------ | ----------------------------------------------------- | ----------------------------------------------------------- |
| PostgreSQL 18 + pgvector | dữ liệu giao dịch (mỗi service một schema), embedding | RDS / CloudNativePG (`platform/data/postgres-cnpg.yaml`)    |
| Redis                    | cache, rate limit gateway, flash sale                 | ElastiCache / `platform/data/redis.yaml`                    |
| Kafka                    | event giữa service                                    | MSK / Strimzi (`platform/messaging`)                        |
| ClickHouse               | phân tích                                             | `platform/data/clickhouse.yaml`                             |
| ScyllaDB                 | lịch sử chat                                          | `platform/data/scylla.yaml`                                 |
| Iceberg trên S3          | data lake, replay/ML                                  | Kafka Connect Iceberg sink; local: Spark + Polaris (mục 12) |
| Elasticsearch 9          | full-text search                                      | Elastic Cloud (PrivateLink)                                 |
| MongoDB 8                | read model trang sản phẩm (product-service)           | MongoDB Atlas (PrivateLink) / `mongo:8.0` local             |

**Read model trang sản phẩm (CQRS, ADR 0020)**

product-service ghi Postgres + outbox như mọi service. Read model trong MongoDB (collection `product`, mỗi sản
phẩm một document đã gộp danh mục, ảnh, thuộc tính, variant, giá, điểm review, tồn kho) do chính product-service
dựng khi đọc lại event từ Kafka (consumer group `product-read-model`): event của nó thì đọc lại Postgres, event giá
(pricing), review, tồn kho (inventory), danh mục/thương hiệu (catalog) thì cập nhật từng phần. Trang chi tiết đọc
một document theo slug; chưa có thì dựng tạm từ Postgres. Mongo sập thì trang vẫn chạy (đọc Postgres), consumer
đứng chờ rồi chạy tiếp, không mất event.

- Dựng lại toàn bộ (lần đầu, đổi cấu trúc document, sau sự cố):
  `curl -X POST -H "Authorization: Bearer <token ADMIN>" https://api.<domain>/api/ecommerce/products/read-model/rebuild`
  (chạy nền trên một pod, kết quả trong log).
- Event lỗi nhiều lần nằm ở `<topic>.dlq`; độ trễ projection = consumer lag của group `product-read-model`.
- Atlas: đặt `MONGODB_ATLAS_CLIENT_ID`/`MONGODB_ATLAS_CLIENT_SECRET` (service account của organization), điền
  `atlas_org_id`, `terraform apply`, rồi lưu vào Vault:
  `vault kv patch secret/ecommerce/product-service mongodb_uri="mongodb+srv://product-service:<terraform output -json mongodb_user_passwords>@<host trong mongodb_private_srv>/ecommerce_product?retryWrites=true&w=majority"`

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

---

## 17. Test, CI/CD

### 17.1 Các tầng test

| Tầng                    | Ở đâu                                                                                             | Công cụ                                            | Chạy                                              |
| ----------------------- | ------------------------------------------------------------------------------------------------- | -------------------------------------------------- | ------------------------------------------------- |
| Domain (unit)           | `internal/domain/*_test.go`, `src/test/.../domain`, `src/domain/*.test.ts`, `tests/test_rules.py` | go test, JUnit, Vitest, pytest                     | không cần Docker                                  |
| Use case + adapter thật | `internal/integration/`, `*IT`, `test/*.it.test.ts`, `tests/test_integration.py`                  | Testcontainers (Postgres, Redis)                   | cần Docker                                        |
| Kiến trúc               | ArchUnit (`libs/java/testing`), dependency-cruiser                                                |                                                    | `./gradlew test`, `npm run lint`                  |
| Saga                    | `CheckoutWorkflowTest`                                                                            | Temporal `TestWorkflowEnvironment` (time-skipping) | `./gradlew test`                                  |
| Flink                   | `PipelineIT`, `RiskWindowTest`...                                                                 | MiniCluster                                        | `./gradlew :data:streaming:stream-processor:test` |
| Contract                | `buf breaking`, Spectral, oasdiff, `supergraph:check`, `fga-test`                                 |                                                    | `make check`, CI `contracts`                      |
| E2E                     | `tests/e2e/*.hurl`                                                                                | Hurl                                               | sau mỗi deploy (ArgoCD PostSync hook)             |
| Load                    | `tests/load/k6/browse-and-cart.js`, `flash-sale.js`                                               | k6                                                 | trước đợt sale lớn, hằng tuần trên staging        |
| Chaos                   | `tests/chaos/*.yaml` (thủ công), `platform/chaos/schedules.yaml` (định kỳ)                        | Chaos Mesh                                         | staging                                           |

### 17.2 GitHub Actions (`.github/workflows/`)

| Workflow        | Kích hoạt khi đổi                                                                                                    | Việc chính                                                                                                                                                                    |
| --------------- | -------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ci-go.yml`     | `libs/go`, service Go, `data/ingestion`, `go.work`                                                                   | vet, test -race (service container Postgres/Redis), golangci-lint; main: build image `ko`                                                                                     |
| `ci-java.yml`   | `libs/java`, 5 service Spring, stream-processor, `build-logic`, `api/proto`                                          | `./gradlew build`, `processAot`; main: image Jib, image native (Paketo), image Flink                                                                                          |
| `ci-node.yml`   | `libs/ts`, service NestJS                                                                                            | typecheck, Biome, dependency-cruiser, test với Postgres thật, migration không bị quên, `schema.graphql` cập nhật, supergraph khớp, router config hợp lệ; image + image router |
| `ci-python.yml` | service Python                                                                                                       | ruff, pytest; image                                                                                                                                                           |
| `contracts.yml` | `api/`, proto sinh, `platform/gateway`, `tools/scripts`, `deploy/helm/values`, `platform/delivery`, `platform/authz` | buf lint/breaking, file sinh còn khớp, test OpenFGA, OpenAPI lint/breaking                                                                                                    |
| `security.yml`  |                                                                                                                      | quét secret, IaC + dependency, CodeQL, OpenSSF Scorecard                                                                                                                      |
| `release.yml`   | push tag `vX.Y.Z`                                                                                                    | lấy digest image đã ký của commit, xác minh chữ ký Cosign, mở PR cập nhật `values-prod.yaml` (merge thì ArgoCD canary lên prod)                                               |

Dependabot + Renovate (`.github/renovate.json5`) cập nhật dependency; Renovate lo manifest ArgoCD/Helm/Kustomize, chờ
14 ngày sau phát hành.

---

## 18. Xử lý sự cố thường gặp

| Triệu chứng                                                              | Nguyên nhân thường gặp                             | Cách xử lý                                                                                                      |
| ------------------------------------------------------------------------ | -------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Service Go thoát ngay khi khởi động, log nói thiếu JWT                   | xác thực fail-closed                               | đặt `JWT_JWKS_URL` (Keycloak) hoặc `AUTH_DISABLED=true` khi dev                                                 |
| `401` khi gọi qua gateway                                                | token hết hạn (realm local: 5 phút) hoặc sai realm | lấy lại `make token`; realm local là `ecommerce`                                                                |
| Container Java restart liên tục                                          | chưa bật profile `workflow` (Temporal)             | `make up PROFILES="workflow apps-java"`                                                                         |
| Router GraphQL lỗi khi start                                             | chưa build supergraph                              | `npm ci && npm run build && npm run schema:emit --workspaces --if-present && npm run supergraph`                |
| `GET /checkout/sessions/{claimId}` trả 404 ngay sau khi giành flash sale | phiên mở qua Kafka, trễ vài trăm ms                | hỏi lại sau 200–500 ms                                                                                          |
| Place trả `409 PRICE_CHANGED`                                            | giá/voucher/phí ship đổi so với `expectedTotal`    | lấy lại phiên, cho khách xem tổng mới rồi đặt lại                                                               |
| Place trả `503 CHECKOUT_BUSY`                                            | quá `CHECKOUT_PLACE_MAX_CONCURRENT`                | gửi lại sau `Retry-After` với cùng `Idempotency-Key`                                                            |
| Event không tới consumer                                                 | connector Debezium chưa đăng ký / lỗi              | `curl localhost:8083/connectors?expand=status`; xem `docs/runbooks/debezium-outbox.md`; chạy lại `connect-init` |
| Consumer lag tăng                                                        | consumer lỗi lặp lại / chậm                        | `docs/runbooks/kafka-consumer-lag.md`; kiểm topic `<topic>.dlq`                                                 |
| Saga treo ở `AWAITING_PAYMENT`                                           | khách chưa trả, webhook chưa tới                   | Temporal UI xem workflow; local Stripe cần `stripe listen`; hết 15 phút tự bù trừ                               |
| Frontend không mở được cổng 3000                                         | Grafana đang chiếm 3000                            | tắt profile `observability` khi chạy frontend                                                                   |
| Hết RAM, container bị kill                                               | bật quá nhiều profile                              | chỉ bật profile cần dùng; tăng RAM Docker                                                                       |
| Muốn làm lại từ đầu                                                      | dữ liệu cũ trong volume                            | `docker compose -f deploy/local/docker-compose.yaml down -v` rồi `make up`                                      |
| SLO burn rate cảnh báo                                                   |                                                    | `docs/runbooks/slo-burn.md`                                                                                     |

---

## 19. Quy ước khi đóng góp

- **Contract trước, code sau.** Đổi proto/GraphQL/OpenAPI thì chạy `make proto`/`make gen`, giữ tương thích ngược;
  muốn bỏ trường thì `@deprecated`/`reserved` trước.
- **Không sửa file sinh bằng tay**: `libs/go/proto`, `libs/python/ecommerce-proto/src`, `libs/ts/proto-gen/src/gen`,
  `api/asyncapi`, `platform/gateway/apisix/*.yaml`, `platform/graphql/*/{config.json,supergraph.graphql}`,
  `platform/delivery/kargo/pipeline.yaml`, `services/*/schema.graphql`.
- **Giữ luật lục giác**: domain không import hạ tầng; use case chỉ biết port; adapter vào không gọi thẳng adapter ra;
  service không import code service khác.
- **Event** luôn qua outbox trong cùng transaction; consumer phải idempotent; không xoá migration cũ, chỉ thêm mới.
- **Saga**: đổi logic `CheckoutWorkflowImpl` phải bọc `Workflow.getVersion`; activity phải idempotent theo `orderId`.
- **Đường gọi đồng bộ mới** phải mở trong `platform/mesh/istio/authorization-policies.yaml`; route mới khai trong
  `platform/gateway/routes.yaml`.
- **Quyết định kiến trúc mới** viết ADR mới trong `docs/adr` (không sửa ADR đã Accepted, đánh dấu Superseded).
- **Triển khai**: không dùng image `:latest`, image phải ký bằng Cosign, deploy chỉ qua Git (ArgoCD).
- Trước khi push: `make check && make lint && make test` (hoặc test của phần mình đổi).

---

## Phụ lục A. Cây thư mục đầy đủ của platform

Sinh tự động từ `backend/app/ecommerce` (ngày viết tài liệu). Đã bỏ: `node_modules`, `build`, `dist`, `.venv`,
lockfile (`*.sum`, `uv.lock`, `package-lock.json`), `gradlew*`, file dịch `messages_*.properties`, metadata
`drizzle/meta`. Thư mục code sinh và di sản chỉ ghi một dòng. Giải thích từng phần ở mục 5.

<details>
<summary>Mở cây thư mục (khoảng 2.300 dòng)</summary>

```
backend/app/ecommerce/
├── ai/commerce-mcp-service/
│   ├── app/
│   │   ├── adapters/
│   │   │   ├── __init__.py
│   │   │   ├── http.py
│   │   │   └── openfga.py
│   │   ├── __init__.py
│   │   ├── __main__.py
│   │   ├── config.py
│   │   ├── domain.py
│   │   ├── main.py
│   │   ├── ports.py
│   │   ├── server.py
│   │   └── service.py
│   ├── tests/
│   │   ├── __init__.py
│   │   ├── fakes.py
│   │   ├── test_adapters.py
│   │   ├── test_server.py
│   │   └── test_service.py
│   ├── catalog-info.yaml
│   └── pyproject.toml
├── api/
│   ├── asyncapi/
│   │   └── asyncapi.yaml
│   ├── graphql/
│   ├── jsonschema/clickstream/v1/
│   │   └── clickstream-event.json
│   ├── openapi/
│   │   ├── cart-service.yaml
│   │   └── inventory-service.yaml
│   └── proto/ecommerce/
│       ├── cart/v1/
│       │   ├── cart_events.proto
│       │   └── cart_service.proto
│       ├── catalog/v1/
│       │   ├── brand_service.proto
│       │   ├── catalog_events.proto
│       │   ├── category_service.proto
│       │   └── tag_service.proto
│       ├── common/v1/
│       │   ├── event_envelope.proto
│       │   ├── money.proto
│       │   └── query.proto
│       ├── customer/v1/
│       │   ├── customer_events.proto
│       │   └── customer_service.proto
│       ├── fraud/v1/
│       │   └── fraud_events.proto
│       ├── inventory/v1/
│       │   ├── inventory_events.proto
│       │   └── inventory_service.proto
│       ├── message/v1/
│       │   └── message_events.proto
│       ├── order/v1/
│       │   ├── order_events.proto
│       │   └── order_service.proto
│       ├── payment/v1/
│       │   └── payment_events.proto
│       ├── pricing/v1/
│       │   ├── pricing_events.proto
│       │   └── pricing_service.proto
│       ├── product/v1/
│       │   ├── product_events.proto
│       │   └── product_service.proto
│       ├── promotion/v1/
│       │   ├── promotion_events.proto
│       │   └── promotion_service.proto
│       ├── review/v1/
│       │   └── review_events.proto
│       ├── search/v1/
│       │   └── search_service.proto
│       ├── shipping/v1/
│       │   ├── shipping_events.proto
│       │   └── shipping_service.proto
│       ├── stream/v1/
│       │   └── stream_events.proto
│       └── tax/v1/
│           └── tax_service.proto
├── build-logic/
│   ├── .kotlin/sessions/
│   ├── bin/main/gradle/kotlin/dsl/
│   │   ├── accessors/
│   │   │   ├── _231530f1a807ec2618b6672dff9201b2/
│   │   │   ├── _2c64335917de9499984c725d45093148/
│   │   │   ├── _5a6eaebca61cdb2ad7d91418d7c015af/
│   │   │   └── _bf8b12bd0fc7c98f5dc769fe9f256f11/
│   │   └── plugins/_7edbf033b05b78a9c7d16371409a1a32/
│   ├── src/main/kotlin/
│   │   ├── congthanh.java-conventions.gradle.kts
│   │   ├── congthanh.protobuf-conventions.gradle.kts
│   │   ├── congthanh.spring-native.gradle.kts
│   │   └── congthanh.spring-service.gradle.kts
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── data/
│   ├── ingestion/clickstream-collector/
│   │   ├── cmd/server/
│   │   │   └── main.go
│   │   ├── internal/
│   │   │   ├── adapter/
│   │   │   │   ├── http/
│   │   │   │   │   ├── handler.go
│   │   │   │   │   └── handler_test.go
│   │   │   │   └── kafka/
│   │   │   │       └── publisher.go
│   │   │   ├── app/
│   │   │   │   └── collector.go
│   │   │   ├── config/
│   │   │   │   └── config.go
│   │   │   └── domain/
│   │   │       ├── event.go
│   │   │       └── event_test.go
│   │   ├── .env.example
│   │   ├── Dockerfile
│   │   ├── catalog-info.yaml
│   │   └── go.mod
│   ├── lakehouse/
│   │   ├── airflow/dags/
│   │   │   └── lakehouse.py
│   │   ├── dbt/
│   │   │   ├── dbt_project.yml, profiles.yml
│   │   │   ├── macros/            generate_schema_name, helpers (initcap, money, lookback...)
│   │   │   ├── models/            sources.yml, silver/*.sql + _silver.yml, gold/*.sql + _gold.yml
│   │   │   └── tests/generic/     expression_is_true, not_negative, recent_rows
│   │   ├── feast/
│   │   │   ├── feature_store.yaml
│   │   │   └── features.py
│   │   ├── jobs/
│   │   │   ├── common.py
│   │   │   ├── decode_events.py
│   │   │   ├── maintenance.py
│   │   │   ├── ods_tables.py
│   │   │   └── stream_ingest.py
│   │   ├── sql/
│   │   │   └── trino-examples.sql
│   │   └── tools/
│   │       └── simulate_clickstream.py
│   └── streaming/stream-processor/
│       ├── bin/
│       │   ├── main/com/congthanh/stream/
│       │   │   ├── io/
│       │   │   ├── logic/
│       │   │   ├── model/
│       │   │   └── pipeline/
│       │   └── test/com/congthanh/stream/
│       ├── src/
│       │   ├── main/java/com/congthanh/stream/
│       │   │   ├── io/
│       │   │   │   ├── Envelopes.java
│       │   │   │   └── KafkaIo.java
│       │   │   ├── logic/
│       │   │   │   ├── RiskWindow.java
│       │   │   │   ├── StockCoverState.java
│       │   │   │   └── TrendingAggregate.java
│       │   │   ├── model/
│       │   │   │   ├── InteractionSignal.java
│       │   │   │   ├── InventorySignal.java
│       │   │   │   ├── KeyedRecord.java
│       │   │   │   └── OrderSignal.java
│       │   │   ├── pipeline/
│       │   │   │   ├── CustomerRiskPipeline.java
│       │   │   │   ├── ProductTrendingPipeline.java
│       │   │   │   └── StockCoverPipeline.java
│       │   │   ├── StreamConfig.java
│       │   │   └── StreamProcessorApp.java
│       │   └── test/java/com/congthanh/stream/
│       │       ├── EnvelopesTest.java
│       │       ├── PipelineIT.java
│       │       ├── RiskWindowTest.java
│       │       ├── StockCoverStateTest.java
│       │       └── StreamConfigTest.java
│       ├── Dockerfile
│       ├── build.gradle.kts
│       └── catalog-info.yaml
├── deploy/
│   ├── argocd/
│   │   ├── applicationsets/
│   │   │   ├── backup.yaml
│   │   │   ├── chaos.yaml
│   │   │   ├── inference.yaml
│   │   │   ├── services.yaml
│   │   │   └── streaming.yaml
│   │   ├── platform/
│   │   │   ├── namespaces.yaml
│   │   │   ├── operators.yaml
│   │   │   └── platform-apps.yaml
│   │   ├── projects/
│   │   │   └── projects.yaml
│   │   └── bootstrap.yaml
│   ├── helm/
│   │   ├── events/
│   │   │   ├── events-prod.yaml
│   │   │   └── events-staging.yaml
│   │   ├── microservice/
│   │   │   ├── templates/
│   │   │   │   ├── _helpers.tpl
│   │   │   │   ├── analysis-template.yaml
│   │   │   │   ├── autoscaling.yaml
│   │   │   │   ├── externalsecret.yaml
│   │   │   │   ├── httproute.yaml
│   │   │   │   ├── pdb.yaml
│   │   │   │   ├── service.yaml
│   │   │   │   ├── serviceaccount.yaml
│   │   │   │   ├── servicemonitor.yaml
│   │   │   │   └── workload.yaml
│   │   │   ├── .helmignore
│   │   │   ├── Chart.yaml
│   │   │   └── values.yaml
│   │   └── values/
│   │       ├── backoffice-bff/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── cart-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── catalog-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── chatbot-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── checkout-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── commerce-mcp-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── customer-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── fraud-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── graphql-router/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── graphql-router-backoffice/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── inventory-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── media-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── message-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── notification-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── order-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── payment-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── pricing-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── product-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── promotion-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── recommendation-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── review-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── search-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── shipping-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── storefront-bff/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── supplier-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── tax-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       ├── translate-service/
│   │       │   ├── values-dev.yaml
│   │       │   ├── values-prod.yaml
│   │       │   └── values.yaml
│   │       └── webhook-service/
│   │           ├── values-dev.yaml
│   │           ├── values-prod.yaml
│   │           └── values.yaml
│   ├── legacy/  (cấu hình thời Spring Cloud cũ: compose, ELK, k8s, terraform — chỉ để tham khảo, không dùng)
│   └── local/
│       ├── lakehouse/
│       │   ├── airflow/
│       │   │   └── Dockerfile
│       │   ├── dbt/
│       │   │   └── Dockerfile
│       │   ├── feast/
│       │   │   └── Dockerfile
│       │   ├── marquez/
│       │   │   └── marquez.yml
│       │   ├── openmetadata/
│       │   │   ├── Dockerfile
│       │   │   └── ingest.py
│       │   ├── polaris/
│       │   │   └── init.sh
│       │   ├── spark/
│       │   │   ├── Dockerfile
│       │   │   └── spark-defaults.conf
│       │   ├── superset/
│       │   │   ├── Dockerfile
│       │   │   ├── bootstrap_dashboards.py
│       │   │   └── superset_config.py
│       │   ├── trino/catalog/
│       │   │   ├── lakehouse.properties
│       │   │   └── lakehouse_ro.properties
│       │   └── connect-init.sh
│       ├── postgres/
│       │   ├── Dockerfile
│       │   └── init.sql
│       ├── docker-compose.lakehouse.yaml
│       └── docker-compose.yaml
├── docs/
│   ├── adr/
│   │   ├── 0001-polyglot-services.md
│   │   ├── 0002-monorepo-hexagonal.md
│   │   ├── 0003-outbox-debezium-protobuf.md
│   │   ├── 0004-temporal-saga.md
│   │   ├── 0005-apisix-gateway.md
│   │   ├── 0006-istio-ambient.md
│   │   ├── 0007-flash-sale.md
│   │   ├── 0008-observability.md
│   │   ├── 0009-gitops-supply-chain.md
│   │   ├── 0010-authz.md
│   │   ├── 0011-nestjs-graphql-federation.md
│   │   ├── 0012-checkout-pricing-fraud-recommendation.md
│   │   ├── 0013-data-lakehouse.md
│   │   ├── 0014-streaming-delivery-runtime.md
│   │   ├── 0015-us-market-international-payments.md
│   │   ├── 0016-pay-later-installments.md
│   │   ├── 0017-redis-instead-of-valkey.md
│   │   ├── 0018-flash-sale-orders-waiting-room-prescaling.md
│   │   ├── 0019-elasticsearch-edge-backup-guardduty.md
│   │   ├── 0020-product-read-model-mongodb.md
│   │   ├── 0021-reliability-hardening.md
│   │   ├── 0023-lakehouse-dbt-bi-lineage-features-catalog.md
│   │   └── README.md
│   ├── architecture/
│   │   └── overview.md
│   └── runbooks/
│       ├── debezium-outbox.md
│       ├── flash-sale.md
│       ├── kafka-consumer-lag.md
│       └── slo-burn.md
├── gradle/
│   ├── wrapper/
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
├── infra/terraform/
│   ├── envs/
│   │   ├── dev/
│   │   │   ├── main.tf
│   │   │   ├── outputs.tf
│   │   │   ├── terraform.tfvars
│   │   │   ├── variables.tf
│   │   │   └── versions.tf
│   │   ├── prod/
│   │   │   ├── main.tf
│   │   │   ├── outputs.tf
│   │   │   ├── terraform.tfvars
│   │   │   ├── variables.tf
│   │   │   └── versions.tf
│   │   ├── staging/
│   │   │   ├── main.tf
│   │   │   ├── outputs.tf
│   │   │   ├── terraform.tfvars
│   │   │   ├── variables.tf
│   │   │   └── versions.tf
│   │   └── test/
│   │       ├── main.tf
│   │       ├── outputs.tf
│   │       ├── terraform.tfvars
│   │       ├── variables.tf
│   │       └── versions.tf
│   └── modules/
│       ├── backup/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── dns/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── edge/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── eks/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── elastic-cloud/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── elasticache/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── guardduty/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── mongodb-atlas/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── msk/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── network/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── rds-postgres/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── s3-cdn/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       ├── waf/
│       │   ├── main.tf
│       │   ├── outputs.tf
│       │   ├── variables.tf
│       │   └── versions.tf
│       └── workload-bucket/
│           ├── main.tf
│           ├── outputs.tf
│           └── variables.tf
├── libs/
│   ├── go/
│   │   ├── platform/
│   │   │   ├── apperr/
│   │   │   │   ├── apperr.go
│   │   │   │   └── apperr_test.go
│   │   │   ├── auth/
│   │   │   │   └── auth.go
│   │   │   ├── config/
│   │   │   │   └── env.go
│   │   │   ├── db/
│   │   │   │   ├── dbtest/
│   │   │   │   │   └── dbtest.go
│   │   │   │   ├── postgres.go
│   │   │   │   ├── store.go
│   │   │   │   └── tx.go
│   │   │   ├── featureflag/
│   │   │   │   └── featureflag.go
│   │   │   ├── grpcx/
│   │   │   │   └── client.go
│   │   │   ├── httpx/
│   │   │   │   └── response.go
│   │   │   ├── idgen/
│   │   │   │   └── snowflake.go
│   │   │   ├── kafka/
│   │   │   │   ├── consumer.go
│   │   │   │   ├── consumer_test.go
│   │   │   │   └── producer.go
│   │   │   ├── logging/
│   │   │   │   └── logging.go
│   │   │   ├── money/
│   │   │   │   ├── money.go
│   │   │   │   └── money_test.go
│   │   │   ├── otel/
│   │   │   │   └── otel.go
│   │   │   ├── outbox/
│   │   │   │   ├── janitor.go
│   │   │   │   ├── janitor_test.go
│   │   │   │   └── outbox.go
│   │   │   ├── server/
│   │   │   │   ├── grpc.go
│   │   │   │   ├── http.go
│   │   │   │   └── run.go
│   │   │   ├── testkit/
│   │   │   │   └── testkit.go
│   │   │   └── go.mod
│   │   └── proto/
│   │       ├── ecommerce/  (code Go sinh từ api/proto bằng `make proto`, không sửa tay)
│   │       └── go.mod
│   ├── java/
│   │   ├── common-core/
│   │   │   ├── src/main/java/com/congthanh/commonservice/
│   │   │   │   ├── config/
│   │   │   │   │   ├── CorsConfig.java
│   │   │   │   │   └── WebConfig.java
│   │   │   │   ├── constants/
│   │   │   │   │   ├── Constants.java
│   │   │   │   │   └── ResponseStatus.java
│   │   │   │   ├── exception/
│   │   │   │   │   ├── grpc/
│   │   │   │   │   │   └── ExceptionInterceptor.java
│   │   │   │   │   ├── handlers/
│   │   │   │   │   │   └── ExceptionHandlers.java
│   │   │   │   │   ├── AccessDeniedException.java
│   │   │   │   │   ├── BadRequestException.java
│   │   │   │   │   ├── NotFoundException.java
│   │   │   │   │   └── PermissionException.java
│   │   │   │   ├── interceptor/
│   │   │   │   │   └── LogInterceptor.java
│   │   │   │   ├── logging/
│   │   │   │   │   ├── CustomRequestBodyAdviceAdapter.java
│   │   │   │   │   ├── CustomResponseBodyAdviceAdapter.java
│   │   │   │   │   ├── CustomSqlStatementLogger.java
│   │   │   │   │   ├── CustomSqlStatementLoggerIntegrator.java
│   │   │   │   │   ├── CustomStatisticsFactory.java
│   │   │   │   │   ├── CustomStatisticsImpl.java
│   │   │   │   │   └── QueryExecutionLoggingAspect.java
│   │   │   │   ├── model/
│   │   │   │   │   ├── listener/
│   │   │   │   │   │   └── CustomAuditingEntityListener.java
│   │   │   │   │   ├── response/
│   │   │   │   │   │   └── Response.java
│   │   │   │   │   └── BaseEntity.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── serviceImpl/
│   │   │   │   │   │   └── LoggingServiceImpl.java
│   │   │   │   │   └── LoggingService.java
│   │   │   │   └── utils/
│   │   │   │       ├── AuthenticationUtils.java
│   │   │   │       ├── JsonUtils.java
│   │   │   │       └── MessagesUtils.java
│   │   │   ├── .gitattributes
│   │   │   ├── .gitignore
│   │   │   └── build.gradle.kts
│   │   ├── platform/
│   │   │   ├── bin/main/
│   │   │   │   ├── META-INF/spring/
│   │   │   │   ├── com/congthanh/platform/
│   │   │   │   │   ├── autoconfigure/
│   │   │   │   │   ├── idempotency/
│   │   │   │   │   ├── inbox/
│   │   │   │   │   ├── migration/
│   │   │   │   │   ├── outbox/
│   │   │   │   │   ├── security/
│   │   │   │   │   └── temporal/
│   │   │   │   └── db/platform/
│   │   │   ├── src/
│   │   │   │   ├── main/
│   │   │   │   │   ├── java/com/congthanh/platform/
│   │   │   │   │   │   ├── autoconfigure/
│   │   │   │   │   │   │   ├── PlatformAutoConfiguration.java
│   │   │   │   │   │   │   └── PlatformProperties.java
│   │   │   │   │   │   ├── idempotency/
│   │   │   │   │   │   │   ├── IdempotencyKeyFilter.java
│   │   │   │   │   │   │   └── IdempotencyStore.java
│   │   │   │   │   │   ├── inbox/
│   │   │   │   │   │   │   ├── EventEnvelopes.java
│   │   │   │   │   │   │   └── ProcessedEvents.java
│   │   │   │   │   │   ├── migration/
│   │   │   │   │   │   │   └── LiquibaseSchemaCreator.java
│   │   │   │   │   │   ├── outbox/
│   │   │   │   │   │   │   ├── OutboxJanitor.java
│   │   │   │   │   │   │   └── OutboxWriter.java
│   │   │   │   │   │   ├── security/
│   │   │   │   │   │   │   └── KeycloakJwtRoles.java
│   │   │   │   │   │   └── temporal/
│   │   │   │   │   │       ├── TemporalAutoConfiguration.java
│   │   │   │   │   │       ├── TemporalProperties.java
│   │   │   │   │   │       └── TemporalWorkerRegistration.java
│   │   │   │   │   └── resources/
│   │   │   │   │       ├── META-INF/spring/
│   │   │   │   │       │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │   │   │   │       └── db/platform/
│   │   │   │   │           ├── 001-outbox.sql
│   │   │   │   │           ├── 002-processed-events.sql
│   │   │   │   │           ├── 003-idempotency-keys.sql
│   │   │   │   │           ├── 004-idempotency-lease.sql
│   │   │   │   │           └── changelog.yaml
│   │   │   │   └── test/java/com/congthanh/platform/
│   │   │   │       ├── idempotency/
│   │   │   │       │   └── IdempotencyStoreTest.java
│   │   │   │       ├── outbox/
│   │   │   │       │   └── OutboxJanitorTest.java
│   │   │   │       ├── support/
│   │   │   │       │   └── TestPostgres.java
│   │   │   │       └── temporal/
│   │   │   │           └── TemporalWorkerOptionsTest.java
│   │   │   └── build.gradle.kts
│   │   ├── proto/
│   │   │   ├── bin/
│   │   │   │   ├── main/
│   │   │   │   └── test/
│   │   │   └── build.gradle.kts
│   │   ├── testing/
│   │   │   ├── bin/main/com/congthanh/testing/
│   │   │   ├── src/main/java/com/congthanh/testing/
│   │   │   │   └── HexagonalArchitecture.java
│   │   │   └── build.gradle.kts
│   │   └── workflow-contracts/
│   │       ├── bin/main/com/congthanh/ecommerce/workflow/
│   │       │   ├── checkout/
│   │       │   └── payment/
│   │       ├── src/main/java/com/congthanh/ecommerce/workflow/
│   │       │   ├── checkout/
│   │       │   │   ├── CheckoutActivities.java
│   │       │   │   ├── CheckoutContract.java
│   │       │   │   ├── CheckoutInput.java
│   │       │   │   ├── CheckoutResult.java
│   │       │   │   ├── CheckoutState.java
│   │       │   │   └── PaymentCompleted.java
│   │       │   ├── payment/
│   │       │   │   ├── AuthorizePaymentRequest.java
│   │       │   │   ├── PaymentActivities.java
│   │       │   │   ├── PaymentAuthorization.java
│   │       │   │   ├── PaymentFailures.java
│   │       │   │   └── RefundPaymentRequest.java
│   │       │   └── TaskQueues.java
│   │       └── build.gradle.kts
│   ├── python/
│   │   ├── ecommerce-platform/
│   │   │   ├── src/ecommerce_platform/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── auth.py
│   │   │   │   ├── fastapi.py
│   │   │   │   └── logging.py
│   │   │   ├── tests/
│   │   │   │   ├── test_auth.py
│   │   │   │   └── test_fastapi.py
│   │   │   └── pyproject.toml
│   │   └── ecommerce-proto/
│   │       ├── src/  (code Python sinh từ api/proto bằng `make proto-python`)
│   │       ├── generate.sh
│   │       └── pyproject.toml
│   └── ts/
│       ├── clients/
│       │   ├── src/
│       │   │   ├── cart.ts
│       │   │   ├── checkout.ts
│       │   │   ├── customer.ts
│       │   │   ├── index.ts
│       │   │   ├── inventory.ts
│       │   │   ├── order.ts
│       │   │   ├── product.ts
│       │   │   ├── promotion.ts
│       │   │   └── recommendation.ts
│       │   ├── package.json
│       │   └── tsconfig.json
│       ├── platform/
│       │   ├── src/
│       │   │   ├── auth/
│       │   │   │   ├── auth.guard.ts
│       │   │   │   ├── auth.module.ts
│       │   │   │   ├── decorators.ts
│       │   │   │   ├── jwt-verifier.ts
│       │   │   │   ├── principal.middleware.ts
│       │   │   │   └── principal.ts
│       │   │   ├── config/
│       │   │   │   └── env.ts
│       │   │   ├── db/
│       │   │   │   ├── database.module.ts
│       │   │   │   ├── database.ts
│       │   │   │   ├── inbox.ts
│       │   │   │   ├── janitor.test.ts
│       │   │   │   ├── janitor.ts
│       │   │   │   ├── outbox.ts
│       │   │   │   └── platform-tables.ts
│       │   │   ├── errors/
│       │   │   │   ├── domain-error.ts
│       │   │   │   └── problem.filter.ts
│       │   │   ├── graphql/
│       │   │   │   ├── federation.ts
│       │   │   │   └── subscriptions.ts
│       │   │   ├── grpc/
│       │   │   │   ├── client.ts
│       │   │   │   └── server.ts
│       │   │   ├── health/
│       │   │   │   └── health.module.ts
│       │   │   ├── http/
│       │   │   │   ├── rest-client.test.ts
│       │   │   │   └── rest-client.ts
│       │   │   ├── kafka/
│       │   │   │   └── event-consumer.ts
│       │   │   ├── logging/
│       │   │   │   └── logger.module.ts
│       │   │   ├── realtime/
│       │   │   │   └── redis-pubsub.ts
│       │   │   ├── telemetry/
│       │   │   │   └── register.ts
│       │   │   ├── bootstrap.ts
│       │   │   ├── index.ts
│       │   │   └── testing.ts
│       │   ├── package.json
│       │   └── tsconfig.json
│       ├── proto-gen/
│       │   ├── src/gen/ecommerce/
│       │   │   ├── fraud/v1/
│       │   │   │   └── fraud_events_pb.ts
│       │   │   ├── order/v1/
│       │   │   │   └── order_service_pb.ts
│       │   │   └── pricing/v1/
│       │   │       ├── pricing_events_pb.ts
│       │   │       └── pricing_service_pb.ts
│       │   ├── buf.gen.yaml
│       │   ├── package.json
│       │   └── tsconfig.json
│       └── shared/
│           ├── src/
│           │   ├── index.ts
│           │   ├── money.ts
│           │   └── problem.ts
│           ├── package.json
│           └── tsconfig.json
├── platform/
│   ├── authz/openfga/
│   │   ├── model.fga
│   │   ├── tests.fga.yaml
│   │   └── values.yaml
│   ├── chaos/
│   │   └── schedules.yaml
│   ├── data/
│   │   ├── clickhouse.yaml
│   │   ├── postgres-cnpg.yaml
│   │   ├── redis.yaml
│   │   └── scylla.yaml
│   ├── debezium/connectors/
│   │   ├── catalog-service-outbox.json
│   │   ├── checkout-service-outbox.json
│   │   ├── order-service-outbox.json
│   │   ├── payment-service-outbox.json
│   │   ├── pricing-service-outbox.json
│   │   ├── seller-service-outbox.json
│   │   ├── shipping-service-outbox.json
│   │   └── user-service-outbox.json
│   ├── delivery/
│   │   ├── argo-rollouts/
│   │   │   ├── gatewayapi-plugin-rbac.yaml
│   │   │   ├── kustomization.yaml
│   │   │   └── slo-burn-rate.yaml
│   │   ├── kargo/
│   │   │   ├── credentials.yaml
│   │   │   ├── kustomization.yaml
│   │   │   ├── pipeline.yaml
│   │   │   └── verification.yaml
│   │   └── kustomization.yaml
│   ├── featureflags/unleash/
│   │   ├── flags.json
│   │   └── values.yaml
│   ├── gateway/
│   │   ├── apisix/
│   │   │   ├── k8s/
│   │   │   │   ├── global-rules.yaml
│   │   │   │   ├── routes.yaml
│   │   │   │   └── values.yaml
│   │   │   ├── apisix.yaml
│   │   │   └── config.yaml
│   │   └── routes.yaml
│   ├── graphql/
│   │   ├── backoffice/
│   │   │   ├── graph.yaml
│   │   │   └── supergraph.graphql
│   │   ├── router/
│   │   │   ├── Dockerfile
│   │   │   └── config.yaml
│   │   └── storefront/
│   │       ├── config.json
│   │       ├── graph.yaml
│   │       └── supergraph.graphql
│   ├── identity/keycloak/
│   │   ├── k8s/
│   │   │   ├── keycloak.yaml
│   │   │   ├── kustomization.yaml
│   │   │   └── realm-patch.yaml
│   │   └── realms/
│   │       └── ecommerce-realm.json
│   ├── inference/
│   │   ├── base/
│   │   │   ├── gpu-nodeclass.yaml
│   │   │   ├── gpu-nodepool.yaml
│   │   │   ├── kustomization.yaml
│   │   │   └── shop-assistant.yaml
│   │   └── overlays/
│   │       ├── prod/
│   │       │   └── kustomization.yaml
│   │       └── staging/
│   │           └── kustomization.yaml
│   ├── kafka/
│   │   └── topics.yaml
│   ├── mesh/istio/
│   │   ├── authorization-policies.yaml
│   │   ├── istio-ambient.yaml
│   │   ├── kustomization.yaml
│   │   ├── namespaces.yaml
│   │   ├── peer-authentication.yaml
│   │   └── traffic.yaml
│   ├── messaging/
│   │   ├── debezium/
│   │   │   ├── connectors/
│   │   │   │   ├── iceberg-sink.yaml
│   │   │   │   └── outbox-connectors.yaml
│   │   │   ├── local/
│   │   │   │   ├── cart-outbox.json
│   │   │   │   ├── catalog-outbox.json
│   │   │   │   ├── customer-outbox.json
│   │   │   │   ├── fraud-outbox.json
│   │   │   │   ├── inventory-outbox.json
│   │   │   │   ├── message-outbox.json
│   │   │   │   ├── ods-cdc.json
│   │   │   │   ├── order-outbox.json
│   │   │   │   ├── payment-outbox.json
│   │   │   │   ├── pricing-outbox.json
│   │   │   │   ├── product-outbox.json
│   │   │   │   ├── promotion-outbox.json
│   │   │   │   ├── review-outbox.json
│   │   │   │   └── shipping-outbox.json
│   │   │   └── kafka-connect.yaml
│   │   ├── kafka/
│   │   │   ├── kafka-cluster.yaml
│   │   │   ├── topics.yaml
│   │   │   └── users.yaml
│   │   └── schema-registry/
│   │       └── apicurio.yaml
│   ├── network/cilium/
│   │   ├── policies.yaml
│   │   └── values.yaml
│   ├── observability/
│   │   ├── grafana/provisioning/
│   │   │   ├── dashboards/
│   │   │   │   ├── general.yaml
│   │   │   │   ├── observability_dashboard.json
│   │   │   │   ├── opentelemetry-collector.json
│   │   │   │   └── prometheus-dashboard.json
│   │   │   └── datasources/
│   │   │       └── datasource.yaml
│   │   ├── k8s/
│   │   │   ├── alloy-values.yaml
│   │   │   ├── lgtm-values.yaml
│   │   │   └── otel-collector.yaml
│   │   ├── loki/
│   │   │   └── loki-local.yaml
│   │   ├── otel-collector/
│   │   │   └── otelcol-config.yaml
│   │   ├── prometheus/
│   │   │   ├── alert-rules.yaml
│   │   │   ├── prometheus.yaml
│   │   │   └── slo-rules.yaml
│   │   ├── pyrra/
│   │   │   ├── crd.yaml
│   │   │   ├── kustomization.yaml
│   │   │   └── pyrra.yaml
│   │   ├── slo/
│   │   │   └── slos.yaml
│   │   └── tempo/
│   │       └── tempo-local.yaml
│   ├── policy/kyverno/
│   │   └── policies.yaml
│   ├── secrets/
│   │   ├── external-secrets/
│   │   │   └── cluster-secret-store.yaml
│   │   └── vault/
│   │       └── values.yaml
│   ├── security/tetragon/
│   │   ├── policies.yaml
│   │   └── values.yaml
│   ├── streaming/flink/
│   │   ├── base/
│   │   │   ├── external-secret.yaml
│   │   │   ├── kustomization.yaml
│   │   │   ├── kustomizeconfig.yaml
│   │   │   ├── pod-monitor.yaml
│   │   │   └── stream-processor.yaml
│   │   └── overlays/
│   │       ├── dev/
│   │       │   └── kustomization.yaml
│   │       ├── prod/
│   │       │   └── kustomization.yaml
│   │       ├── staging/
│   │       │   └── kustomization.yaml
│   │       └── test/
│   │           └── kustomization.yaml
│   ├── temporal/
│   │   └── namespaces.yaml
│   └── workflow/temporal/
│       ├── namespaces.yaml
│       └── values.yaml
├── services/
│   ├── backoffice-bff/
│   │   ├── src/
│   │   │   ├── adapters/in/graphql/
│   │   │   │   ├── loaders.ts
│   │   │   │   ├── mappers.ts
│   │   │   │   ├── operations.resolver.ts
│   │   │   │   ├── product.resolver.ts
│   │   │   │   └── types.ts
│   │   │   ├── application/
│   │   │   │   ├── backoffice.service.ts
│   │   │   │   └── ports.ts
│   │   │   ├── config/
│   │   │   │   └── env.ts
│   │   │   ├── app.module.ts
│   │   │   ├── emit-schema.ts
│   │   │   └── main.ts
│   │   ├── test/
│   │   │   └── backoffice.graphql.test.ts
│   │   ├── package.json
│   │   ├── schema.graphql
│   │   ├── tsconfig.build.json
│   │   └── tsconfig.json
│   ├── cart-service/
│   │   ├── cmd/server/
│   │   │   └── main.go
│   │   ├── internal/
│   │   │   ├── adapter/
│   │   │   │   ├── grpc/
│   │   │   │   │   └── server.go
│   │   │   │   ├── grpcclient/
│   │   │   │   │   └── clients.go
│   │   │   │   ├── http/
│   │   │   │   │   ├── dto.go
│   │   │   │   │   ├── errors.go
│   │   │   │   │   └── handler.go
│   │   │   │   ├── messaging/
│   │   │   │   │   ├── consumer.go
│   │   │   │   │   └── events.go
│   │   │   │   └── repository/postgres/
│   │   │   │       └── repository.go
│   │   │   ├── app/
│   │   │   │   ├── ports.go
│   │   │   │   └── service.go
│   │   │   ├── config/
│   │   │   │   └── config.go
│   │   │   ├── domain/
│   │   │   │   ├── cart.go
│   │   │   │   ├── domain_test.go
│   │   │   │   ├── errors.go
│   │   │   │   ├── events.go
│   │   │   │   └── validation.go
│   │   │   └── integration/
│   │   │       └── cart_test.go
│   │   ├── migrations/
│   │   │   ├── 00001_init.sql
│   │   │   ├── 00002_outbox.sql
│   │   │   ├── 00003_pending_order_clears.sql
│   │   │   ├── 00004_processed_events_pk.sql
│   │   │   └── embed.go
│   │   ├── .env.example
│   │   ├── Dockerfile
│   │   ├── Jenkinsfile
│   │   ├── catalog-info.yaml
│   │   └── go.mod
│   ├── catalog-service/
│   │   ├── drizzle/
│   │   │   ├── 0000_init.sql
│   │   │   └── 0001_seed_reference_data.sql
│   │   ├── src/
│   │   │   ├── adapters/
│   │   │   │   ├── in/
│   │   │   │   │   ├── graphql/
│   │   │   │   │   │   ├── catalog.resolver.ts
│   │   │   │   │   │   ├── mappers.ts
│   │   │   │   │   │   └── types.ts
│   │   │   │   │   └── grpc/
│   │   │   │   │       └── catalog.grpc.ts
│   │   │   │   └── out/
│   │   │   │       ├── persistence/
│   │   │   │       │   ├── repositories.ts
│   │   │   │       │   ├── schema.ts
│   │   │   │       │   └── unit-of-work.ts
│   │   │   │       └── product-directory.grpc.ts
│   │   │   ├── application/
│   │   │   │   ├── catalog.service.ts
│   │   │   │   └── ports.ts
│   │   │   ├── config/
│   │   │   │   └── env.ts
│   │   │   ├── domain/
│   │   │   │   ├── brand.ts
│   │   │   │   ├── category.test.ts
│   │   │   │   ├── category.ts
│   │   │   │   ├── country.ts
│   │   │   │   ├── slug.ts
│   │   │   │   └── tag.ts
│   │   │   ├── app.module.ts
│   │   │   ├── emit-schema.ts
│   │   │   ├── main.ts
│   │   │   └── migrate.ts
│   │   ├── test/
│   │   │   └── catalog.it.test.ts
│   │   ├── drizzle.config.ts
│   │   ├── package.json
│   │   ├── schema.graphql
│   │   ├── tsconfig.build.json
│   │   └── tsconfig.json
│   ├── chatbot-service/
│   │   ├── app/
│   │   │   ├── adapters/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── claude.py
│   │   │   │   ├── ollama.py
│   │   │   │   ├── redis_history.py
│   │   │   │   ├── search.py
│   │   │   │   └── vllm.py
│   │   │   ├── domain/
│   │   │   │   ├── __init__.py
│   │   │   │   └── chat.py
│   │   │   ├── __init__.py
│   │   │   ├── __main__.py
│   │   │   ├── config.py
│   │   │   ├── main.py
│   │   │   ├── ports.py
│   │   │   └── service.py
│   │   ├── tests/
│   │   │   ├── __init__.py
│   │   │   ├── fakes.py
│   │   │   ├── test_adapters.py
│   │   │   ├── test_api.py
│   │   │   └── test_service.py
│   │   ├── .env.example
│   │   ├── catalog-info.yaml
│   │   └── pyproject.toml
│   ├── checkout-service/
│   │   ├── bin/
│   │   │   ├── main/
│   │   │   │   ├── com/congthanh/checkoutservice/
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   ├── messaging/
│   │   │   │   │   │   │   ├── web/
│   │   │   │   │   │   │   └── workflow/
│   │   │   │   │   │   └── out/
│   │   │   │   │   │       ├── grpc/
│   │   │   │   │   │       ├── http/
│   │   │   │   │   │       ├── persistence/
│   │   │   │   │   │       └── workflow/
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── port/
│   │   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   └── out/
│   │   │   │   │   │   └── service/
│   │   │   │   │   ├── config/
│   │   │   │   │   └── domain/
│   │   │   │   │       ├── exception/
│   │   │   │   │       └── model/
│   │   │   │   └── db/changelog/changes/
│   │   │   └── test/com/congthanh/checkoutservice/
│   │   │       ├── adapter/
│   │   │       │   ├── in/workflow/
│   │   │       │   └── out/persistence/
│   │   │       └── application/service/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/congthanh/checkoutservice/
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   ├── messaging/
│   │   │   │   │   │   │   │   ├── FlashSaleEventsConsumer.java
│   │   │   │   │   │   │   │   └── FraudEventsConsumer.java
│   │   │   │   │   │   │   ├── web/
│   │   │   │   │   │   │   │   ├── CheckoutController.java
│   │   │   │   │   │   │   │   ├── CheckoutDtos.java
│   │   │   │   │   │   │   │   ├── PlaceLimiter.java
│   │   │   │   │   │   │   │   └── ProblemHandler.java
│   │   │   │   │   │   │   └── workflow/
│   │   │   │   │   │   │       ├── CheckoutWorkflow.java
│   │   │   │   │   │   │       └── CheckoutWorkflowImpl.java
│   │   │   │   │   │   └── out/
│   │   │   │   │   │       ├── grpc/
│   │   │   │   │   │       │   ├── CatalogGrpcAdapter.java
│   │   │   │   │   │       │   ├── OrdersGrpcAdapter.java
│   │   │   │   │   │       │   ├── PricingGrpcAdapter.java
│   │   │   │   │   │       │   ├── PromotionsGrpcAdapter.java
│   │   │   │   │   │       │   ├── Protos.java
│   │   │   │   │   │       │   ├── ShippingGrpcAdapter.java
│   │   │   │   │   │       │   ├── TaxGrpcAdapter.java
│   │   │   │   │   │       │   └── TransientFailure.java
│   │   │   │   │   │       ├── http/
│   │   │   │   │   │       │   └── FraudHttpAdapter.java
│   │   │   │   │   │       ├── persistence/
│   │   │   │   │   │       │   ├── JdbcSessionRepository.java
│   │   │   │   │   │       │   └── UuidV7Ids.java
│   │   │   │   │   │       └── workflow/
│   │   │   │   │   │           └── TemporalCheckoutWorkflows.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── port/
│   │   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   │   └── CheckoutUseCase.java
│   │   │   │   │   │   │   └── out/
│   │   │   │   │   │   │       ├── Catalog.java
│   │   │   │   │   │   │       ├── CheckoutWorkflows.java
│   │   │   │   │   │   │       ├── FraudCheck.java
│   │   │   │   │   │   │       ├── Ids.java
│   │   │   │   │   │   │       ├── Orders.java
│   │   │   │   │   │   │       ├── Pricing.java
│   │   │   │   │   │   │       ├── Promotions.java
│   │   │   │   │   │   │       ├── SessionRepository.java
│   │   │   │   │   │   │       ├── Shipping.java
│   │   │   │   │   │   │       └── Tax.java
│   │   │   │   │   │   └── service/
│   │   │   │   │   │       └── CheckoutService.java
│   │   │   │   │   ├── config/
│   │   │   │   │   │   ├── CheckoutConfig.java
│   │   │   │   │   │   └── SecurityConfig.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── exception/
│   │   │   │   │   │   │   └── CheckoutException.java
│   │   │   │   │   │   └── model/
│   │   │   │   │   │       ├── Address.java
│   │   │   │   │   │       ├── CheckoutSession.java
│   │   │   │   │   │       ├── Money.java
│   │   │   │   │   │       └── SessionStatus.java
│   │   │   │   │   └── CheckoutServiceApplication.java
│   │   │   │   └── resources/
│   │   │   │       ├── db/changelog/
│   │   │   │       │   ├── changes/
│   │   │   │       │   │   └── 0001-checkout-sessions.sql
│   │   │   │       │   └── db.changelog-master.yaml
│   │   │   │       └── application.yaml
│   │   │   └── test/java/com/congthanh/checkoutservice/
│   │   │       ├── adapter/
│   │   │       │   ├── in/workflow/
│   │   │       │   │   └── CheckoutWorkflowTest.java
│   │   │       │   └── out/persistence/
│   │   │       │       └── SnapshotJsonTest.java
│   │   │       ├── application/service/
│   │   │       │   └── CheckoutServiceTest.java
│   │   │       └── ArchitectureTest.java
│   │   ├── Dockerfile
│   │   ├── Jenkinsfile
│   │   ├── build.gradle.kts
│   │   ├── catalog-info.yaml
│   │   └── lombok.config
│   ├── customer-service/
│   │   ├── drizzle/
│   │   │   └── 0000_init.sql
│   │   ├── src/
│   │   │   ├── adapters/
│   │   │   │   ├── in/
│   │   │   │   │   ├── graphql/
│   │   │   │   │   │   ├── customer.resolver.ts
│   │   │   │   │   │   └── types.ts
│   │   │   │   │   └── grpc/
│   │   │   │   │       └── customer.grpc.ts
│   │   │   │   └── out/persistence/
│   │   │   │       ├── repositories.ts
│   │   │   │       ├── schema.ts
│   │   │   │       └── unit-of-work.ts
│   │   │   ├── application/
│   │   │   │   ├── customer.service.ts
│   │   │   │   └── ports.ts
│   │   │   ├── config/
│   │   │   │   └── env.ts
│   │   │   ├── domain/
│   │   │   │   ├── search-history.ts
│   │   │   │   ├── shipping-address.test.ts
│   │   │   │   ├── shipping-address.ts
│   │   │   │   └── wishlist.ts
│   │   │   ├── app.module.ts
│   │   │   ├── emit-schema.ts
│   │   │   ├── main.ts
│   │   │   └── migrate.ts
│   │   ├── test/
│   │   │   └── customer.it.test.ts
│   │   ├── drizzle.config.ts
│   │   ├── package.json
│   │   ├── schema.graphql
│   │   ├── tsconfig.build.json
│   │   └── tsconfig.json
│   ├── fraud-service/
│   │   ├── app/
│   │   │   ├── adapters/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── kafka.py
│   │   │   │   ├── postgres.py
│   │   │   │   └── redis_store.py
│   │   │   ├── domain/
│   │   │   │   ├── __init__.py
│   │   │   │   └── rules.py
│   │   │   ├── migrations/
│   │   │   │   ├── 001_init.sql
│   │   │   │   ├── 002_shipment_returned.sql
│   │   │   │   └── __init__.py
│   │   │   ├── __init__.py
│   │   │   ├── __main__.py
│   │   │   ├── config.py
│   │   │   ├── main.py
│   │   │   ├── ports.py
│   │   │   └── service.py
│   │   ├── tests/
│   │   │   ├── test_api.py
│   │   │   ├── test_integration.py
│   │   │   ├── test_rules.py
│   │   │   └── test_stream.py
│   │   ├── .env.example
│   │   ├── catalog-info.yaml
│   │   └── pyproject.toml
│   ├── inventory-service/
│   │   ├── cmd/server/
│   │   │   └── main.go
│   │   ├── internal/
│   │   │   ├── adapter/
│   │   │   │   ├── grpc/
│   │   │   │   │   └── server.go
│   │   │   │   ├── http/
│   │   │   │   │   ├── errors.go
│   │   │   │   │   ├── flashsale.go
│   │   │   │   │   └── handler.go
│   │   │   │   ├── messaging/
│   │   │   │   │   ├── claims.go
│   │   │   │   │   ├── consumer.go
│   │   │   │   │   └── events.go
│   │   │   │   └── repository/
│   │   │   │       ├── postgres/
│   │   │   │       │   ├── repository.go
│   │   │   │       │   └── warehouse.go
│   │   │   │       └── redis/
│   │   │   │           ├── lua/
│   │   │   │           │   ├── claim.lua
│   │   │   │           │   ├── drain.lua
│   │   │   │           │   ├── queue.lua
│   │   │   │           │   ├── release.lua
│   │   │   │           │   ├── sweep.lua
│   │   │   │           │   └── transition.lua
│   │   │   │           └── flashsale.go
│   │   │   ├── app/
│   │   │   │   ├── flashsale.go
│   │   │   │   ├── ports.go
│   │   │   │   ├── reservation.go
│   │   │   │   ├── stock.go
│   │   │   │   └── warehouse.go
│   │   │   ├── config/
│   │   │   │   └── config.go
│   │   │   ├── domain/
│   │   │   │   ├── domain_test.go
│   │   │   │   ├── errors.go
│   │   │   │   ├── events.go
│   │   │   │   ├── item.go
│   │   │   │   ├── reservation.go
│   │   │   │   ├── stock.go
│   │   │   │   └── warehouse.go
│   │   │   └── integration/
│   │   │       ├── helpers_test.go
│   │   │       ├── inventory_test.go
│   │   │       └── outbox_flashsale_test.go
│   │   ├── migrations/
│   │   │   ├── 00001_init.sql
│   │   │   ├── 00002_outbox.sql
│   │   │   ├── 00003_processed_events_pk.sql
│   │   │   └── embed.go
│   │   ├── .env.example
│   │   ├── Dockerfile
│   │   ├── Jenkinsfile
│   │   ├── catalog-info.yaml
│   │   └── go.mod
│   ├── media-service/
│   │   ├── cmd/server/
│   │   │   └── main.go
│   │   ├── internal/
│   │   │   ├── adapter/
│   │   │   │   ├── http/
│   │   │   │   │   └── handler.go
│   │   │   │   ├── repository/postgres/
│   │   │   │   │   └── repository.go
│   │   │   │   └── s3store/
│   │   │   │       ├── store.go
│   │   │   │       └── store_test.go
│   │   │   ├── app/
│   │   │   │   ├── service.go
│   │   │   │   └── service_test.go
│   │   │   ├── config/
│   │   │   │   └── config.go
│   │   │   ├── domain/
│   │   │   │   ├── media.go
│   │   │   │   └── media_test.go
│   │   │   └── integration/
│   │   │       └── media_test.go
│   │   ├── migrations/
│   │   │   ├── 00001_init.sql
│   │   │   └── embed.go
│   │   ├── .env.example
│   │   ├── Dockerfile
│   │   ├── catalog-info.yaml
│   │   └── go.mod
│   ├── message-service/
│   │   ├── drizzle/
│   │   │   └── 0000_init.sql
│   │   ├── src/
│   │   │   ├── adapters/
│   │   │   │   ├── in/graphql/
│   │   │   │   │   ├── chat.resolver.ts
│   │   │   │   │   └── types.ts
│   │   │   │   └── out/
│   │   │   │       ├── persistence/
│   │   │   │       │   ├── repositories.ts
│   │   │   │       │   ├── schema.ts
│   │   │   │       │   └── unit-of-work.ts
│   │   │   │       ├── product-directory.grpc.ts
│   │   │   │       └── realtime.ts
│   │   │   ├── application/
│   │   │   │   ├── chat.service.ts
│   │   │   │   └── ports.ts
│   │   │   ├── config/
│   │   │   │   └── env.ts
│   │   │   ├── domain/
│   │   │   │   ├── conversation.test.ts
│   │   │   │   └── conversation.ts
│   │   │   ├── app.module.ts
│   │   │   ├── emit-schema.ts
│   │   │   ├── main.ts
│   │   │   └── migrate.ts
│   │   ├── test/
│   │   │   └── chat.it.test.ts
│   │   ├── drizzle.config.ts
│   │   ├── package.json
│   │   ├── schema.graphql
│   │   ├── tsconfig.build.json
│   │   └── tsconfig.json
│   ├── notification-service/
│   │   ├── drizzle/
│   │   │   └── 0000_init.sql
│   │   ├── src/
│   │   │   ├── adapters/
│   │   │   │   ├── in/
│   │   │   │   │   ├── graphql/
│   │   │   │   │   │   ├── notification.resolver.ts
│   │   │   │   │   │   └── types.ts
│   │   │   │   │   └── kafka/
│   │   │   │   │       └── domain-events.consumer.ts
│   │   │   │   └── out/
│   │   │   │       ├── channels/
│   │   │   │       │   ├── email.sender.ts
│   │   │   │       │   ├── fcm.sender.ts
│   │   │   │       │   ├── sms.sender.ts
│   │   │   │       │   └── web-push.sender.ts
│   │   │   │       ├── persistence/
│   │   │   │       │   ├── repositories.ts
│   │   │   │       │   ├── schema.ts
│   │   │   │       │   └── unit-of-work.ts
│   │   │   │       ├── keycloak-directory.ts
│   │   │   │       └── realtime.publisher.ts
│   │   │   ├── application/
│   │   │   │   ├── delivery.worker.ts
│   │   │   │   ├── event-handlers.ts
│   │   │   │   ├── notification.service.ts
│   │   │   │   └── ports.ts
│   │   │   ├── config/
│   │   │   │   └── env.ts
│   │   │   ├── domain/
│   │   │   │   ├── notification.ts
│   │   │   │   ├── preferences.test.ts
│   │   │   │   ├── preferences.ts
│   │   │   │   └── templates.ts
│   │   │   ├── app.module.ts
│   │   │   ├── emit-schema.ts
│   │   │   ├── main.ts
│   │   │   └── migrate.ts
│   │   ├── test/
│   │   │   └── notification.it.test.ts
│   │   ├── drizzle.config.ts
│   │   ├── package.json
│   │   ├── schema.graphql
│   │   ├── tsconfig.build.json
│   │   └── tsconfig.json
│   ├── order-service/
│   │   ├── bin/
│   │   │   ├── main/
│   │   │   │   ├── com/congthanh/orderservice/
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   ├── grpc/
│   │   │   │   │   │   │   ├── web/
│   │   │   │   │   │   │   └── workflow/
│   │   │   │   │   │   └── out/
│   │   │   │   │   │       ├── grpc/
│   │   │   │   │   │       ├── identity/
│   │   │   │   │   │       ├── persistence/
│   │   │   │   │   │       └── workflow/
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── port/
│   │   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   └── out/
│   │   │   │   │   │   └── service/
│   │   │   │   │   ├── config/
│   │   │   │   │   └── domain/
│   │   │   │   │       ├── event/
│   │   │   │   │       ├── exception/
│   │   │   │   │       └── model/
│   │   │   │   └── db/changelog/changes/
│   │   │   └── test/com/congthanh/orderservice/
│   │   │       ├── adapter/in/workflow/
│   │   │       └── domain/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/congthanh/orderservice/
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   ├── grpc/
│   │   │   │   │   │   │   │   └── OrderGrpcService.java
│   │   │   │   │   │   │   ├── web/
│   │   │   │   │   │   │   │   ├── OrderController.java
│   │   │   │   │   │   │   │   ├── OrderDtos.java
│   │   │   │   │   │   │   │   └── ProblemHandler.java
│   │   │   │   │   │   │   └── workflow/
│   │   │   │   │   │   │       └── CheckoutActivitiesImpl.java
│   │   │   │   │   │   └── out/
│   │   │   │   │   │       ├── grpc/
│   │   │   │   │   │       │   ├── InventoryGrpcAdapter.java
│   │   │   │   │   │       │   ├── ProductCatalogGrpcAdapter.java
│   │   │   │   │   │       │   ├── PromotionsGrpcAdapter.java
│   │   │   │   │   │       │   ├── Protos.java
│   │   │   │   │   │       │   └── TransientFailure.java
│   │   │   │   │   │       ├── identity/
│   │   │   │   │   │       │   └── RandomOrderIdentity.java
│   │   │   │   │   │       ├── persistence/
│   │   │   │   │   │       │   ├── AddressEmbeddable.java
│   │   │   │   │   │       │   ├── OrderEntity.java
│   │   │   │   │   │       │   ├── OrderEventMapper.java
│   │   │   │   │   │       │   ├── OrderJpaRepository.java
│   │   │   │   │   │       │   ├── OrderLineEntity.java
│   │   │   │   │   │       │   └── OrderPersistenceAdapter.java
│   │   │   │   │   │       └── workflow/
│   │   │   │   │   │           └── TemporalCheckoutOrchestrator.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── port/
│   │   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   │   ├── CancelOrderUseCase.java
│   │   │   │   │   │   │   │   ├── CheckoutStepsUseCase.java
│   │   │   │   │   │   │   │   ├── OrderQueries.java
│   │   │   │   │   │   │   │   ├── PlaceOrderUseCase.java
│   │   │   │   │   │   │   │   └── Requester.java
│   │   │   │   │   │   │   └── out/
│   │   │   │   │   │   │       ├── CheckoutOrchestrator.java
│   │   │   │   │   │   │       ├── Inventory.java
│   │   │   │   │   │   │       ├── OrderIdentity.java
│   │   │   │   │   │   │       ├── OrderRepository.java
│   │   │   │   │   │   │       ├── ProductCatalog.java
│   │   │   │   │   │   │       └── Promotions.java
│   │   │   │   │   │   └── service/
│   │   │   │   │   │       ├── CheckoutRecovery.java
│   │   │   │   │   │       ├── OrderCommandService.java
│   │   │   │   │   │       └── OrderQueryService.java
│   │   │   │   │   ├── config/
│   │   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   │   └── TemporalWorkerConfig.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── event/
│   │   │   │   │   │   │   └── OrderEvent.java
│   │   │   │   │   │   ├── exception/
│   │   │   │   │   │   │   └── OrderException.java
│   │   │   │   │   │   └── model/
│   │   │   │   │   │       ├── Address.java
│   │   │   │   │   │       ├── CheckoutDetails.java
│   │   │   │   │   │       ├── Money.java
│   │   │   │   │   │       ├── Order.java
│   │   │   │   │   │       ├── OrderLine.java
│   │   │   │   │   │       ├── OrderStatus.java
│   │   │   │   │   │       └── PaymentMethod.java
│   │   │   │   │   └── OrderServiceApplication.java
│   │   │   │   └── resources/
│   │   │   │       ├── db/changelog/
│   │   │   │       │   ├── changes/
│   │   │   │       │   │   ├── 0001-orders.sql
│   │   │   │       │   │   └── 0002-shipping-fee-tax.sql
│   │   │   │       │   └── db.changelog-master.yaml
│   │   │   │       └── application.yaml
│   │   │   └── test/java/com/congthanh/orderservice/
│   │   │       ├── adapter/in/workflow/
│   │   │       ├── domain/
│   │   │       │   └── OrderTest.java
│   │   │       └── ArchitectureTest.java
│   │   ├── .env
│   │   ├── .gitignore
│   │   ├── Dockerfile
│   │   ├── Jenkinsfile
│   │   ├── build.gradle.kts
│   │   ├── catalog-info.yaml
│   │   └── lombok.config
│   ├── payment-service/
│   │   ├── bin/
│   │   │   ├── main/
│   │   │   │   ├── com/congthanh/paymentservice/
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   ├── web/
│   │   │   │   │   │   │   └── workflow/
│   │   │   │   │   │   └── out/
│   │   │   │   │   │       ├── persistence/
│   │   │   │   │   │       ├── provider/
│   │   │   │   │   │       └── workflow/
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── port/
│   │   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   └── out/
│   │   │   │   │   │   └── service/
│   │   │   │   │   ├── config/
│   │   │   │   │   └── domain/
│   │   │   │   │       ├── event/
│   │   │   │   │       ├── exception/
│   │   │   │   │       └── model/
│   │   │   │   ├── db/changelog/changes/
│   │   │   │   └── messages/
│   │   │   └── test/com/congthanh/paymentservice/domain/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/congthanh/paymentservice/
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   ├── web/
│   │   │   │   │   │   │   │   ├── PaymentController.java
│   │   │   │   │   │   │   │   ├── ProblemHandler.java
│   │   │   │   │   │   │   │   └── SandboxController.java
│   │   │   │   │   │   │   └── workflow/
│   │   │   │   │   │   │       └── PaymentActivitiesImpl.java
│   │   │   │   │   │   └── out/
│   │   │   │   │   │       ├── persistence/
│   │   │   │   │   │       │   ├── PaymentEntity.java
│   │   │   │   │   │       │   ├── PaymentJpaRepository.java
│   │   │   │   │   │       │   └── PaymentPersistenceAdapter.java
│   │   │   │   │   │       ├── provider/
│   │   │   │   │   │       │   ├── Hmac.java
│   │   │   │   │   │       │   ├── PayPalProvider.java
│   │   │   │   │   │       │   ├── ProviderRegistry.java
│   │   │   │   │   │       │   ├── SandboxProvider.java
│   │   │   │   │   │       │   └── StripeProvider.java
│   │   │   │   │   │       └── workflow/
│   │   │   │   │   │           └── TemporalCheckoutNotifier.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── port/
│   │   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   │   └── PaymentUseCases.java
│   │   │   │   │   │   │   └── out/
│   │   │   │   │   │   │       ├── CheckoutNotifier.java
│   │   │   │   │   │   │       ├── PaymentProvider.java
│   │   │   │   │   │   │       ├── PaymentProviders.java
│   │   │   │   │   │   │       └── PaymentRepository.java
│   │   │   │   │   │   └── service/
│   │   │   │   │   │       └── PaymentService.java
│   │   │   │   │   ├── config/
│   │   │   │   │   │   ├── InstallmentProperties.java
│   │   │   │   │   │   ├── PaymentConfig.java
│   │   │   │   │   │   └── SandboxConfig.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── event/
│   │   │   │   │   │   │   └── PaymentEvent.java
│   │   │   │   │   │   ├── exception/
│   │   │   │   │   │   │   └── PaymentException.java
│   │   │   │   │   │   └── model/
│   │   │   │   │   │       ├── InstallmentPolicy.java
│   │   │   │   │   │       ├── Money.java
│   │   │   │   │   │       ├── Payment.java
│   │   │   │   │   │       ├── PaymentMethod.java
│   │   │   │   │   │       └── PaymentStatus.java
│   │   │   │   │   └── PaymentServiceApplication.java
│   │   │   │   └── resources/
│   │   │   │       ├── db/changelog/
│   │   │   │       │   ├── changes/
│   │   │   │       │   │   ├── 0001-payments.sql
│   │   │   │       │   │   ├── 0002-international-methods.sql
│   │   │   │       │   │   └── 0003-installments.sql
│   │   │   │       │   └── db.changelog-master.yaml
│   │   │   │       ├── messages/
│   │   │   │       │   └── messages.properties
│   │   │   │       └── application.yaml
│   │   │   └── test/java/com/congthanh/paymentservice/
│   │   │       ├── domain/
│   │   │       │   ├── InstallmentPolicyTest.java
│   │   │       │   └── PaymentTest.java
│   │   │       └── ArchitectureTest.java
│   │   ├── .env
│   │   ├── .gitignore
│   │   ├── Dockerfile
│   │   ├── build.gradle.kts
│   │   ├── catalog-info.yaml
│   │   └── lombok.config
│   ├── pricing-service/
│   │   ├── cmd/server/
│   │   │   └── main.go
│   │   ├── internal/
│   │   │   ├── adapter/
│   │   │   │   ├── cache/
│   │   │   │   │   └── redis.go
│   │   │   │   ├── grpc/
│   │   │   │   │   └── server.go
│   │   │   │   ├── http/
│   │   │   │   │   └── handler.go
│   │   │   │   ├── messaging/
│   │   │   │   │   └── consumer.go
│   │   │   │   └── repository/postgres/
│   │   │   │       └── repository.go
│   │   │   ├── app/
│   │   │   │   └── service.go
│   │   │   ├── config/
│   │   │   │   └── config.go
│   │   │   ├── domain/
│   │   │   │   ├── pricing.go
│   │   │   │   └── pricing_test.go
│   │   │   └── integration/
│   │   │       └── pricing_test.go
│   │   ├── migrations/
│   │   │   ├── 00001_init.sql
│   │   │   ├── 00002_usd_default.sql
│   │   │   ├── 00003_processed_events_pk.sql
│   │   │   └── embed.go
│   │   ├── .env.example
│   │   ├── Dockerfile
│   │   ├── catalog-info.yaml
│   │   └── go.mod
│   ├── product-service/
│   │   ├── bin/
│   │   │   ├── main/
│   │   │   │   ├── com/congthanh/productservice/
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   ├── grpc/
│   │   │   │   │   │   │   ├── messaging/
│   │   │   │   │   │   │   └── web/
│   │   │   │   │   │   └── out/
│   │   │   │   │   │       ├── grpc/
│   │   │   │   │   │       ├── messaging/
│   │   │   │   │   │       └── readmodel/
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── dto/
│   │   │   │   │   │   │   ├── constant/
│   │   │   │   │   │   │   ├── request/
│   │   │   │   │   │   │   ├── response/
│   │   │   │   │   │   │   └── viewmodel/
│   │   │   │   │   │   ├── event/
│   │   │   │   │   │   ├── mapper/
│   │   │   │   │   │   ├── port/
│   │   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   └── out/
│   │   │   │   │   │   │       ├── attributeValue/
│   │   │   │   │   │   │       ├── product/
│   │   │   │   │   │   │       ├── productAtrributeValue/
│   │   │   │   │   │   │       ├── productAttribute/
│   │   │   │   │   │   │       ├── productCategory/
│   │   │   │   │   │   │       ├── productImage/
│   │   │   │   │   │   │       ├── productView/
│   │   │   │   │   │   │       ├── variantOptionCombination/
│   │   │   │   │   │   │       └── variantOptionValue/
│   │   │   │   │   │   ├── readmodel/
│   │   │   │   │   │   └── service/
│   │   │   │   │   ├── config/
│   │   │   │   │   └── domain/
│   │   │   │   │       ├── exception/
│   │   │   │   │       ├── model/enums/
│   │   │   │   │       └── support/
│   │   │   │   ├── db/changelog/changes/
│   │   │   │   ├── graphql/
│   │   │   │   └── messages/
│   │   │   └── test/com/congthanh/productservice/
│   │   │       ├── adapter/
│   │   │       │   ├── in/messaging/
│   │   │       │   └── out/readmodel/
│   │   │       └── application/service/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/congthanh/productservice/
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   ├── grpc/
│   │   │   │   │   │   │   │   └── ProductGrpcService.java
│   │   │   │   │   │   │   ├── messaging/
│   │   │   │   │   │   │   │   └── ProductReadModelConsumer.java
│   │   │   │   │   │   │   └── web/
│   │   │   │   │   │   │       ├── ExceptionHandlers.java
│   │   │   │   │   │   │       ├── ProductAttributeController.java
│   │   │   │   │   │   │       ├── ProductAttributeValueController.java
│   │   │   │   │   │   │       ├── ProductController.java
│   │   │   │   │   │   │       ├── ProductImageController.java
│   │   │   │   │   │   │       ├── ProductReadModelController.java
│   │   │   │   │   │   │       └── ProductViewController.java
│   │   │   │   │   │   └── out/
│   │   │   │   │   │       ├── grpc/
│   │   │   │   │   │       │   └── CategoryGrpcClient.java
│   │   │   │   │   │       ├── messaging/
│   │   │   │   │   │       │   └── ProductOutboxListener.java
│   │   │   │   │   │       └── readmodel/
│   │   │   │   │   │           └── MongoProductReadModelStore.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── dto/
│   │   │   │   │   │   │   ├── constant/
│   │   │   │   │   │   │   │   └── ResponseStatus.java
│   │   │   │   │   │   │   ├── request/
│   │   │   │   │   │   │   │   ├── AddProductViewRequest.java
│   │   │   │   │   │   │   │   ├── CreateProductRequest.java
│   │   │   │   │   │   │   │   ├── CreateVariantImageRequest.java
│   │   │   │   │   │   │   │   ├── MetadataRequest.java
│   │   │   │   │   │   │   │   ├── ProductAttributeValueRequest.java
│   │   │   │   │   │   │   │   └── ProductVariantRequest.java
│   │   │   │   │   │   │   ├── response/
│   │   │   │   │   │   │   │   ├── OptionValue.java
│   │   │   │   │   │   │   │   ├── PaginationInfo.java
│   │   │   │   │   │   │   │   ├── Response.java
│   │   │   │   │   │   │   │   ├── ResponseWithPagination.java
│   │   │   │   │   │   │   │   └── VariantValueResponse.java
│   │   │   │   │   │   │   ├── viewmodel/
│   │   │   │   │   │   │   │   ├── ProductAttributeVm.java
│   │   │   │   │   │   │   │   ├── ProductCategoryVm.java
│   │   │   │   │   │   │   │   ├── ProductDetailVm.java
│   │   │   │   │   │   │   │   ├── ProductImageVm.java
│   │   │   │   │   │   │   │   ├── ProductOptionCombinationGetVm.java
│   │   │   │   │   │   │   │   ├── ProductOptionValueGetVm.java
│   │   │   │   │   │   │   │   ├── ProductVariantVm.java
│   │   │   │   │   │   │   │   └── ProductVm.java
│   │   │   │   │   │   │   ├── ProductAttributeDTO.java
│   │   │   │   │   │   │   ├── ProductAttributeValueDTO.java
│   │   │   │   │   │   │   ├── ProductCategory.java
│   │   │   │   │   │   │   ├── ProductCategoryDTO.java
│   │   │   │   │   │   │   ├── ProductDTO.java
│   │   │   │   │   │   │   ├── ProductImageDTO.java
│   │   │   │   │   │   │   └── ProductViewDTO.java
│   │   │   │   │   │   ├── event/
│   │   │   │   │   │   │   └── ProductChanged.java
│   │   │   │   │   │   ├── mapper/
│   │   │   │   │   │   │   ├── ProductAttributeMapper.java
│   │   │   │   │   │   │   ├── ProductAttributeValueMapper.java
│   │   │   │   │   │   │   ├── ProductImageMapper.java
│   │   │   │   │   │   │   ├── ProductMapper.java
│   │   │   │   │   │   │   └── ProductViewMapper.java
│   │   │   │   │   │   ├── port/
│   │   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   │   ├── ProductAttributeService.java
│   │   │   │   │   │   │   │   ├── ProductAttributeValueService.java
│   │   │   │   │   │   │   │   ├── ProductCategoryService.java
│   │   │   │   │   │   │   │   ├── ProductImageService.java
│   │   │   │   │   │   │   │   ├── ProductReadModelUseCase.java
│   │   │   │   │   │   │   │   ├── ProductService.java
│   │   │   │   │   │   │   │   ├── ProductViewService.java
│   │   │   │   │   │   │   │   └── VariantOptionService.java
│   │   │   │   │   │   │   └── out/
│   │   │   │   │   │   │       ├── product/
│   │   │   │   │   │   │       │   ├── ProductCustomRepository.java
│   │   │   │   │   │   │       │   ├── ProductCustomRepositoryImpl.java
│   │   │   │   │   │   │       │   └── ProductRepository.java
│   │   │   │   │   │   │       ├── productAtrributeValue/
│   │   │   │   │   │   │       │   ├── ProductAttributeValueCustomRepository.java
│   │   │   │   │   │   │       │   ├── ProductAttributeValueCustomRepositoryImpl.java
│   │   │   │   │   │   │       │   └── ProductAttributeValueRepository.java
│   │   │   │   │   │   │       ├── productAttribute/
│   │   │   │   │   │   │       │   ├── ProductAttributeCustomRepository.java
│   │   │   │   │   │   │       │   ├── ProductAttributeCustomRepositoryImpl.java
│   │   │   │   │   │   │       │   └── ProductAttributeRepository.java
│   │   │   │   │   │   │       ├── productCategory/
│   │   │   │   │   │   │       │   ├── ProductCategoryCustomRepository.java
│   │   │   │   │   │   │       │   ├── ProductCategoryCustomRepositoryImpl.java
│   │   │   │   │   │   │       │   └── ProductCategoryRepository.java
│   │   │   │   │   │   │       ├── productImage/
│   │   │   │   │   │   │       │   ├── ProductImageCustomRepository.java
│   │   │   │   │   │   │       │   ├── ProductImageCustomRepositoryImpl.java
│   │   │   │   │   │   │       │   └── ProductImageRepository.java
│   │   │   │   │   │   │       ├── productView/
│   │   │   │   │   │   │       │   ├── ProductViewCustomRepository.java
│   │   │   │   │   │   │       │   ├── ProductViewCustomRepositoryImpl.java
│   │   │   │   │   │   │       │   └── ProductViewRepository.java
│   │   │   │   │   │   │       ├── variantOptionCombination/
│   │   │   │   │   │   │       │   ├── VariantOptionCombinationCustomRepository.java
│   │   │   │   │   │   │       │   ├── VariantOptionCombinationCustomRepositoryImpl.java
│   │   │   │   │   │   │       │   └── VariantOptionCombinationRepository.java
│   │   │   │   │   │   │       ├── variantOptionValue/
│   │   │   │   │   │   │       │   └── VariantOptionValueRepository.java
│   │   │   │   │   │   │       ├── CategoryCatalog.java
│   │   │   │   │   │   │       └── ProductReadModelStore.java
│   │   │   │   │   │   ├── readmodel/
│   │   │   │   │   │   │   └── ProductDocument.java
│   │   │   │   │   │   └── service/
│   │   │   │   │   │       ├── ProductAttributeServiceImpl.java
│   │   │   │   │   │       ├── ProductAttributeValueServiceImpl.java
│   │   │   │   │   │       ├── ProductCategoryServiceImpl.java
│   │   │   │   │   │       ├── ProductDocumentAssembler.java
│   │   │   │   │   │       ├── ProductImageServiceImpl.java
│   │   │   │   │   │       ├── ProductReadModelService.java
│   │   │   │   │   │       ├── ProductServiceImpl.java
│   │   │   │   │   │       ├── ProductViewServiceImpl.java
│   │   │   │   │   │       └── VariantOptionServiceImpl.java
│   │   │   │   │   ├── config/
│   │   │   │   │   │   ├── GraphQLConfig.java
│   │   │   │   │   │   ├── JpaAuditingConfig.java
│   │   │   │   │   │   ├── KafkaConfig.java
│   │   │   │   │   │   ├── ModelMapperConfig.java
│   │   │   │   │   │   ├── RedisConfig.java
│   │   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   │   └── SwaggerConfig.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── exception/
│   │   │   │   │   │   │   ├── BadRequestException.java
│   │   │   │   │   │   │   ├── InvalidParameterException.java
│   │   │   │   │   │   │   ├── NotFoundException.java
│   │   │   │   │   │   │   └── PermissionException.java
│   │   │   │   │   │   ├── model/
│   │   │   │   │   │   │   ├── enums/
│   │   │   │   │   │   │   │   └── ProductStatus.java
│   │   │   │   │   │   │   ├── Product.java
│   │   │   │   │   │   │   ├── ProductAttribute.java
│   │   │   │   │   │   │   ├── ProductAttributeValue.java
│   │   │   │   │   │   │   ├── ProductCategory.java
│   │   │   │   │   │   │   ├── ProductImage.java
│   │   │   │   │   │   │   ├── ProductView.java
│   │   │   │   │   │   │   ├── VariantOption.java
│   │   │   │   │   │   │   ├── VariantOptionCombination.java
│   │   │   │   │   │   │   └── VariantOptionValue.java
│   │   │   │   │   │   └── support/
│   │   │   │   │   │       ├── Helper.java
│   │   │   │   │   │       └── SnowflakeIdGenerator.java
│   │   │   │   │   └── ProductServiceApplication.java
│   │   │   │   └── resources/
│   │   │   │       ├── db/changelog/
│   │   │   │       │   ├── changes/
│   │   │   │       │   │   └── 0001-products.sql
│   │   │   │       │   └── db.changelog-master.yaml
│   │   │   │       ├── graphql/
│   │   │   │       │   └── schema.graphqls
│   │   │   │       ├── messages/
│   │   │   │       │   └── messages.properties
│   │   │   │       └── application.yaml
│   │   │   └── test/java/com/congthanh/productservice/
│   │   │       ├── adapter/
│   │   │       │   ├── in/messaging/
│   │   │       │   │   └── ProductReadModelConsumerTest.java
│   │   │       │   └── out/readmodel/
│   │   │       │       └── MongoProductReadModelStoreTest.java
│   │   │       ├── application/service/
│   │   │       │   └── ProductReadModelServiceTest.java
│   │   │       ├── domain/model/
│   │   │       │   └── EntityIdentityTest.java
│   │   │       └── ArchitectureTest.java
│   │   ├── .env
│   │   ├── .gitattributes
│   │   ├── .gitignore
│   │   ├── Dockerfile
│   │   ├── Entity Diagram.drawio
│   │   ├── Jenkinsfile
│   │   ├── build.gradle.kts
│   │   ├── catalog-info.yaml
│   │   ├── compose.yaml
│   │   └── lombok.config
│   ├── promotion-service/
│   │   ├── bin/
│   │   │   ├── main/
│   │   │   │   ├── com/congthanh/promotionservice/
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   ├── grpc/
│   │   │   │   │   │   │   ├── messaging/
│   │   │   │   │   │   │   └── web/
│   │   │   │   │   │   └── out/persistence/
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── port/
│   │   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   └── out/
│   │   │   │   │   │   └── service/
│   │   │   │   │   ├── config/
│   │   │   │   │   └── domain/
│   │   │   │   │       ├── event/
│   │   │   │   │       ├── exception/
│   │   │   │   │       └── model/
│   │   │   │   ├── db/changelog/changes/
│   │   │   │   └── messages/
│   │   │   └── test/com/congthanh/promotionservice/domain/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/congthanh/promotionservice/
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   ├── grpc/
│   │   │   │   │   │   │   │   └── PromotionGrpcService.java
│   │   │   │   │   │   │   ├── messaging/
│   │   │   │   │   │   │   │   └── OrderEventsConsumer.java
│   │   │   │   │   │   │   └── web/
│   │   │   │   │   │   │       ├── ProblemHandler.java
│   │   │   │   │   │   │       └── PromotionController.java
│   │   │   │   │   │   └── out/persistence/
│   │   │   │   │   │       ├── PromotionEntity.java
│   │   │   │   │   │       ├── PromotionJpaRepository.java
│   │   │   │   │   │       ├── PromotionPersistenceAdapter.java
│   │   │   │   │   │       ├── RedemptionEntity.java
│   │   │   │   │   │       └── RedemptionJpaRepository.java
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── port/
│   │   │   │   │   │   │   ├── in/
│   │   │   │   │   │   │   │   ├── CheckoutDiscounts.java
│   │   │   │   │   │   │   │   └── PromotionAdmin.java
│   │   │   │   │   │   │   └── out/
│   │   │   │   │   │   │       └── PromotionRepository.java
│   │   │   │   │   │   └── service/
│   │   │   │   │   │       └── PromotionService.java
│   │   │   │   │   ├── config/
│   │   │   │   │   │   └── PromotionConfig.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── event/
│   │   │   │   │   │   │   └── PromotionEvent.java
│   │   │   │   │   │   ├── exception/
│   │   │   │   │   │   │   └── PromotionException.java
│   │   │   │   │   │   └── model/
│   │   │   │   │   │       ├── DiscountRule.java
│   │   │   │   │   │       ├── Promotion.java
│   │   │   │   │   │       ├── PromotionType.java
│   │   │   │   │   │       ├── Quote.java
│   │   │   │   │   │       └── Redemption.java
│   │   │   │   │   └── PromotionServiceApplication.java
│   │   │   │   └── resources/
│   │   │   │       ├── db/changelog/
│   │   │   │       │   ├── changes/
│   │   │   │       │   │   └── 0001-promotions.sql
│   │   │   │       │   └── db.changelog-master.yaml
│   │   │   │       ├── messages/
│   │   │   │       │   └── messages.properties
│   │   │   │       └── application.yaml
│   │   │   └── test/java/com/congthanh/promotionservice/
│   │   │       ├── domain/
│   │   │       │   └── PromotionTest.java
│   │   │       └── ArchitectureTest.java
│   │   ├── .gitignore
│   │   ├── Dockerfile
│   │   ├── build.gradle.kts
│   │   ├── catalog-info.yaml
│   │   └── lombok.config
│   ├── recommendation-service/
│   │   ├── app/
│   │   │   ├── adapters/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── kafka.py
│   │   │   │   ├── ollama.py
│   │   │   │   └── postgres.py
│   │   │   ├── domain/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── embedding.py
│   │   │   │   └── ranking.py
│   │   │   ├── migrations/
│   │   │   │   ├── 001_init.sql
│   │   │   │   ├── 002_stream_trending.sql
│   │   │   │   └── __init__.py
│   │   │   ├── __init__.py
│   │   │   ├── __main__.py
│   │   │   ├── config.py
│   │   │   ├── main.py
│   │   │   ├── ports.py
│   │   │   └── service.py
│   │   ├── tests/
│   │   │   ├── test_api.py
│   │   │   ├── test_domain.py
│   │   │   ├── test_integration.py
│   │   │   └── test_service.py
│   │   ├── .env.example
│   │   ├── catalog-info.yaml
│   │   └── pyproject.toml
│   ├── review-service/
│   │   ├── drizzle/
│   │   │   └── 0000_init.sql
│   │   ├── src/
│   │   │   ├── adapters/
│   │   │   │   ├── in/
│   │   │   │   │   ├── graphql/
│   │   │   │   │   │   ├── review.resolver.ts
│   │   │   │   │   │   └── types.ts
│   │   │   │   │   └── kafka/
│   │   │   │   │       └── order-events.consumer.ts
│   │   │   │   └── out/
│   │   │   │       ├── persistence/
│   │   │   │       │   ├── repositories.ts
│   │   │   │       │   ├── schema.ts
│   │   │   │       │   └── unit-of-work.ts
│   │   │   │       └── product-directory.grpc.ts
│   │   │   ├── application/
│   │   │   │   ├── order-events.handler.ts
│   │   │   │   ├── ports.ts
│   │   │   │   └── review.service.ts
│   │   │   ├── config/
│   │   │   │   └── env.ts
│   │   │   ├── domain/
│   │   │   │   ├── purchase.ts
│   │   │   │   ├── rating-summary.ts
│   │   │   │   ├── review.test.ts
│   │   │   │   └── review.ts
│   │   │   ├── app.module.ts
│   │   │   ├── emit-schema.ts
│   │   │   ├── main.ts
│   │   │   └── migrate.ts
│   │   ├── test/
│   │   │   └── review.it.test.ts
│   │   ├── drizzle.config.ts
│   │   ├── package.json
│   │   ├── schema.graphql
│   │   ├── tsconfig.build.json
│   │   └── tsconfig.json
│   ├── search-service/
│   │   ├── cmd/server/
│   │   │   └── main.go
│   │   ├── internal/
│   │   │   ├── adapter/
│   │   │   │   ├── elasticsearch/
│   │   │   │   │   ├── index.go
│   │   │   │   │   └── index_test.go
│   │   │   │   ├── grpc/
│   │   │   │   │   └── server.go
│   │   │   │   ├── http/
│   │   │   │   │   └── handler.go
│   │   │   │   └── messaging/
│   │   │   │       ├── consumer.go
│   │   │   │       └── consumer_test.go
│   │   │   ├── app/
│   │   │   │   └── service.go
│   │   │   ├── config/
│   │   │   │   └── config.go
│   │   │   └── domain/
│   │   │       ├── search.go
│   │   │       └── search_test.go
│   │   ├── .env.example
│   │   ├── Dockerfile
│   │   ├── catalog-info.yaml
│   │   └── go.mod
│   ├── shipping-service/
│   │   ├── cmd/server/
│   │   │   └── main.go
│   │   ├── internal/
│   │   │   ├── adapter/
│   │   │   │   ├── carrier/
│   │   │   │   │   ├── internal.go
│   │   │   │   │   └── internal_test.go
│   │   │   │   ├── grpc/
│   │   │   │   │   └── server.go
│   │   │   │   ├── http/
│   │   │   │   │   └── handler.go
│   │   │   │   ├── messaging/
│   │   │   │   │   └── consumer.go
│   │   │   │   └── repository/postgres/
│   │   │   │       └── repository.go
│   │   │   ├── app/
│   │   │   │   └── service.go
│   │   │   ├── config/
│   │   │   │   └── config.go
│   │   │   ├── domain/
│   │   │   │   ├── shipping.go
│   │   │   │   └── shipping_test.go
│   │   │   └── integration/
│   │   │       └── shipping_test.go
│   │   ├── migrations/
│   │   │   ├── 00001_init.sql
│   │   │   ├── 00002_us_rates.sql
│   │   │   ├── 00003_processed_events_pk.sql
│   │   │   └── embed.go
│   │   ├── .env.example
│   │   ├── Dockerfile
│   │   ├── catalog-info.yaml
│   │   └── go.mod
│   ├── storefront-bff/
│   │   ├── src/
│   │   │   ├── adapters/in/graphql/
│   │   │   │   ├── cart.resolver.ts
│   │   │   │   ├── loaders.ts
│   │   │   │   ├── mappers.ts
│   │   │   │   ├── order.resolver.ts
│   │   │   │   ├── product.resolver.ts
│   │   │   │   ├── recommendation.resolver.ts
│   │   │   │   └── types.ts
│   │   │   ├── application/
│   │   │   │   ├── ports.ts
│   │   │   │   ├── storefront.service.test.ts
│   │   │   │   └── storefront.service.ts
│   │   │   ├── config/
│   │   │   │   └── env.ts
│   │   │   ├── app.module.ts
│   │   │   ├── emit-schema.ts
│   │   │   └── main.ts
│   │   ├── test/
│   │   │   ├── fakes.ts
│   │   │   └── storefront.graphql.test.ts
│   │   ├── package.json
│   │   ├── schema.graphql
│   │   ├── tsconfig.build.json
│   │   └── tsconfig.json
│   ├── supplier-service/
│   │   ├── config/
│   │   │   ├── __init__.py
│   │   │   ├── gunicorn.py
│   │   │   ├── settings.py
│   │   │   ├── settings_test.py
│   │   │   ├── urls.py
│   │   │   └── wsgi.py
│   │   ├── suppliers/
│   │   │   ├── management/
│   │   │   │   ├── commands/
│   │   │   │   │   ├── __init__.py
│   │   │   │   │   └── migrate_schema.py
│   │   │   │   └── __init__.py
│   │   │   ├── migrations/
│   │   │   │   ├── 0001_initial.py
│   │   │   │   └── __init__.py
│   │   │   ├── __init__.py
│   │   │   ├── apps.py
│   │   │   ├── auth.py
│   │   │   ├── envelope.py
│   │   │   ├── health.py
│   │   │   ├── models.py
│   │   │   ├── serializers.py
│   │   │   ├── service.py
│   │   │   ├── urls.py
│   │   │   └── views.py
│   │   ├── tests/
│   │   │   ├── __init__.py
│   │   │   └── test_api.py
│   │   ├── .env.example
│   │   ├── catalog-info.yaml
│   │   ├── manage.py
│   │   └── pyproject.toml
│   ├── tax-service/
│   │   ├── cmd/server/
│   │   │   └── main.go
│   │   ├── internal/
│   │   │   ├── adapter/
│   │   │   │   ├── grpc/
│   │   │   │   │   └── server.go
│   │   │   │   ├── http/
│   │   │   │   │   └── handler.go
│   │   │   │   └── repository/postgres/
│   │   │   │       └── repository.go
│   │   │   ├── app/
│   │   │   │   └── service.go
│   │   │   ├── config/
│   │   │   │   └── config.go
│   │   │   ├── domain/
│   │   │   │   ├── tax.go
│   │   │   │   └── tax_test.go
│   │   │   └── integration/
│   │   │       └── tax_test.go
│   │   ├── migrations/
│   │   │   ├── 00001_init.sql
│   │   │   ├── 00002_us_sales_tax.sql
│   │   │   └── embed.go
│   │   ├── .env.example
│   │   ├── Dockerfile
│   │   ├── catalog-info.yaml
│   │   └── go.mod
│   ├── translate-service/
│   │   ├── app/
│   │   │   ├── adapters/
│   │   │   │   ├── __init__.py
│   │   │   │   ├── claude.py
│   │   │   │   └── redis_cache.py
│   │   │   ├── domain/
│   │   │   │   ├── __init__.py
│   │   │   │   └── translation.py
│   │   │   ├── __init__.py
│   │   │   ├── __main__.py
│   │   │   ├── config.py
│   │   │   ├── main.py
│   │   │   ├── ports.py
│   │   │   └── service.py
│   │   ├── tests/
│   │   │   ├── test_api.py
│   │   │   ├── test_claude_adapter.py
│   │   │   └── test_service.py
│   │   ├── .env.example
│   │   ├── catalog-info.yaml
│   │   └── pyproject.toml
│   ├── webhook-service/
│   │   ├── cmd/server/
│   │   │   └── main.go
│   │   ├── internal/
│   │   │   ├── adapter/
│   │   │   │   ├── delivery/
│   │   │   │   │   ├── sender.go
│   │   │   │   │   └── sender_test.go
│   │   │   │   ├── http/
│   │   │   │   │   └── handler.go
│   │   │   │   ├── messaging/
│   │   │   │   │   ├── consumer.go
│   │   │   │   │   └── consumer_test.go
│   │   │   │   └── repository/postgres/
│   │   │   │       └── repository.go
│   │   │   ├── app/
│   │   │   │   └── service.go
│   │   │   ├── config/
│   │   │   │   └── config.go
│   │   │   ├── domain/
│   │   │   │   ├── webhook.go
│   │   │   │   └── webhook_test.go
│   │   │   └── integration/
│   │   │       └── webhook_test.go
│   │   ├── migrations/
│   │   │   ├── 00001_init.sql
│   │   │   ├── 00002_processed_events_pk.sql
│   │   │   └── embed.go
│   │   ├── .env.example
│   │   ├── Dockerfile
│   │   ├── catalog-info.yaml
│   │   └── go.mod
│   ├── node.Dockerfile
│   └── python.Dockerfile
├── tests/
│   ├── chaos/
│   │   ├── inventory-pod-kill.yaml
│   │   ├── kafka-network-delay.yaml
│   │   └── redis-failure.yaml
│   ├── contract/
│   │   └── .spectral.yaml
│   ├── e2e/
│   │   ├── graphql.hurl
│   │   └── smoke.hurl
│   └── load/k6/
│       ├── browse-and-cart.js
│       └── flash-sale.js
├── tools/
│   ├── agents/  (OTel Java agent .jar, không commit)
│   ├── backstage/templates/go-service/
│   │   ├── helm-values/
│   │   │   └── values.yaml
│   │   ├── skeleton/
│   │   │   ├── cmd/server/
│   │   │   ├── internal/
│   │   │   │   ├── adapter/
│   │   │   │   ├── app/
│   │   │   │   └── domain/
│   │   │   └── README.md
│   │   └── template.yaml
│   └── scripts/
│       ├── compose-supergraph.mjs
│       ├── gen-apisix-routes.py
│       ├── gen-asyncapi.py
│       ├── gen-kargo.py
│       ├── new-node-service.sh
│       ├── validate-hurl-graphql.mjs
│       └── validate-router-config.py
├── .dependency-cruiser.cjs
├── .dockerignore
├── .editorconfig
├── .env.example
├── .gitignore
├── .golangci.yml
├── Makefile
├── biome.json
├── buf.gen.yaml
├── buf.yaml
├── build.gradle.kts
├── catalog-info.yaml
├── go.work
├── go.work.sum
├── gradle.properties
├── package.json
├── settings.gradle.kts
├── tsconfig.base.json
├── vitest.config.ts
└── vitest.global-setup.ts
```

</details>
