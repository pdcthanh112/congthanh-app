# 0005. APISIX làm edge gateway

- Trạng thái: Accepted (thay api-gateway Go ở giai đoạn 2)

## Quyết định
Apache APISIX: xác thực OIDC với Keycloak, rate limit dùng Valkey, circuit breaker, OTel, Prometheus. Định tuyến khai báo một lần ở `services/api-gateway/routes.yaml`, script `services/api-gateway/scripts/gen-routes.py` sinh cấu hình standalone (local) và ApisixRoute (K8s); CI chạy `--check` để không lệch.

## Hệ quả
- Bỏ code gateway tự viết (proxy, rate limit, JWT) — ít code phải bảo trì hơn.
- GraphQL đi qua APISIX tới Apollo Router (`/graphql`).
