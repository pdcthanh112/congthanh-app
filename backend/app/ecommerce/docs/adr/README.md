# Architecture Decision Records

Mỗi quyết định kiến trúc quan trọng là một file `NNNN-tieu-de.md` (định dạng MADR rút gọn: Bối cảnh → Quyết định → Hệ quả).
Không sửa ADR đã "Accepted"; muốn đổi thì viết ADR mới và đánh dấu ADR cũ "Superseded by".

| # | Quyết định | Trạng thái |
|---|---|---|
| 0001 | [Polyglot: Go cho service nóng, Java cho miền phức tạp, NestJS cho BFF/CRUD](0001-polyglot-services.md) | Accepted |
| 0002 | [Monorepo platform + kiến trúc hexagonal cho mọi service](0002-monorepo-hexagonal.md) | Accepted |
| 0003 | [Event-driven với Transactional Outbox + Debezium, Protobuf + Schema Registry](0003-outbox-debezium-protobuf.md) | Accepted |
| 0004 | [Saga checkout bằng Temporal thay Axon](0004-temporal-saga.md) | Accepted |
| 0005 | [APISIX làm edge gateway thay api-gateway Go tự viết](0005-apisix-gateway.md) | Accepted |
| 0006 | [Istio Ambient cho mTLS và zero trust](0006-istio-ambient.md) | Accepted |
| 0007 | [Flash sale: giữ hàng trong Postgres, phân phối bằng Redis Lua](0007-flash-sale.md) | Accepted |
| 0008 | [Observability: OpenTelemetry + Grafana LGTM + Pyroscope, SLO burn-rate](0008-observability.md) | Accepted |
| 0009 | [GitOps với ArgoCD + Argo Rollouts, supply chain ký bằng Cosign](0009-gitops-supply-chain.md) | Accepted |
| 0010 | [Phân quyền: Keycloak (authn) + OpenFGA (ReBAC)](0010-authz.md) | Accepted |
| 0011 | [Service NestJS, GraphQL Federation v2 và Cosmo Router](0011-nestjs-graphql-federation.md) | Accepted |
| 0012 | [Tách checkout, pricing, fraud, recommendation](0012-checkout-pricing-fraud-recommendation.md) | Accepted |
| 0013 | [Data lakehouse: Iceberg + Polaris, Spark, Trino, Airflow; tầng Bronze/ODS/Silver/Gold](0013-data-lakehouse.md) | Accepted |
| 0014 | [Stream processing (Flink), canary theo SLO + Kargo, SLSA, eBPF, MCP cho AI agent, Velero/Chaos/Pyrra](0014-streaming-delivery-runtime.md) | Proposed |
| 0016 | [Pay later: BNPL qua Affirm/Klarna/Afterpay (Stripe) và PayPal Pay Later, chừa cổng cho tín dụng nội bộ](0016-pay-later-installments.md) | Proposed |
| 0017 | [Dùng Redis thay Valkey](0017-redis-instead-of-valkey.md) | Proposed |
| 0018 | [Flash sale: từ suất đã giành tới đơn hàng, phòng chờ ảo, scale trước theo lịch](0018-flash-sale-orders-waiting-room-prescaling.md) | Proposed |
| 0019 | [Elasticsearch trên Elastic Cloud; CloudFront + WAF trước API; AWS Backup và GuardDuty](0019-elasticsearch-edge-backup-guardduty.md) | Proposed |
| 0020 | [Read model trang sản phẩm trên MongoDB, dựng từ event Kafka; MongoDB Atlas](0020-product-read-model-mongodb.md) | Proposed |
