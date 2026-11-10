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
