# Tổng quan kiến trúc

```mermaid
flowchart LR
  web[Storefront / Backoffice] -->|HTTPS| apisix[APISIX gateway]
  apisix -->|OIDC| kc[Keycloak]
  apisix -->|/graphql, /admin/graphql| router[Cosmo Router x2]
  router --> sbff[storefront-bff] & bbff[backoffice-bff]
  router --> nest[catalog / customer / review / notification / message NestJS]
  sbff & bbff -->|gRPC + REST, token relay| cart & inv & order & product
  apisix --> cart[cart-service Go] & inv[inventory-service Go] & order[order-service Java] & product[product-service Java]
  cart -->|gRPC| inv
  cart -->|gRPC| product
  order -->|Temporal saga| temporal[(Temporal)]
  order -->|gRPC| inv
  order -->|gRPC| payment[payment-service Java]
  subgraph data[Data]
    pg[(Postgres + outbox)]
    redis[(Redis)]
  end
  cart & inv & order & product & payment --> pg
  inv -->|Lua flash sale| redis
  pg -->|WAL| dbz[Debezium] --> kafka[(Kafka)]
  kafka --> cart & inv & order
  kafka --> ch[(ClickHouse)] & lake[(Iceberg / S3)]
```

## Luồng checkout ([ADR 0012](../adr/0012-checkout-pricing-fraud-recommendation.md))

```mermaid
sequenceDiagram
  participant C as Client (storefront-bff)
  participant CK as checkout-service
  participant PR as pricing / promotion / shipping / tax
  participant F as fraud-service
  participant O as order-service
  participant T as Temporal
  participant I as inventory-service
  participant P as payment-service
  participant K as Kafka
  C->>CK: POST /checkout/sessions (giỏ, địa chỉ, mã giảm giá)
  CK->>PR: báo giá (gRPC)
  C->>CK: POST /sessions/{id}/place (Idempotency-Key)
  CK->>PR: báo giá lại (giá đổi → 409 PRICE_CHANGED)
  CK->>F: POST /internal/fraud/assess
  alt REJECT
    CK-->>C: 422 FRAUD_REJECTED
  else REVIEW
    CK-->>C: 202 PENDING_REVIEW
    F-->>K: FraudReviewDecided (sau khi duyệt tay)
    K-->>CK: đặt tiếp hoặc huỷ phiên
  else APPROVE
    CK->>O: gRPC PlaceOrder (giá đã chốt, order id sinh trước)
    O->>T: start CheckoutWorkflow (queue checkout-workflow, worker ở checkout-service)
    T->>I: ReserveInventory
    T->>P: AuthorizePayment
    alt thanh toán lỗi
      T->>I: ReleaseInventory (compensation)
    else thành công
      T->>I: CommitReservation
      O-->>K: OrderPlaced (outbox)
      K-->>cart: xoá sản phẩm đã đặt khỏi giỏ
    end
  end
```

Chi tiết từng quyết định: `docs/adr`. Contract: `api/proto` (gRPC + event), `api/asyncapi`, `api/openapi`, `api/graphql`.
