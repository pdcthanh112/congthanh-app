# 0002. Monorepo platform + hexagonal

- Trạng thái: Accepted

## Quyết định
Gốc platform là `backend/app/ecommerce`:
```
api/        contract (proto, AsyncAPI, OpenAPI, GraphQL)  — nguồn sự thật duy nhất
libs/       thư viện nền theo ngôn ngữ + code sinh từ proto
services/   mỗi service một thư mục, cấu trúc hexagonal
platform/   cấu hình hạ tầng nền (gateway, mesh, messaging, observability, security, data)
deploy/     Helm chart, ArgoCD, compose local, legacy
infra/      Terraform AWS
docs/ tests/ tools/
```
Mỗi service theo ports & adapters:
```
internal/domain     thực thể, quy tắc nghiệp vụ, domain event — không import hạ tầng
internal/app        use case + port (interface) mà adapter hiện thực
internal/adapter    driving (http, grpc, messaging consumer) và driven (postgres, redis, grpcclient, kafka)
cmd/server          composition root: chỉ nơi này biết mọi adapter
```

## Hệ quả
- Domain test chạy không cần DB; integration test chạy use case thật với adapter thật.
- Java: ArchUnit kiểm tra quy tắc phụ thuộc (đợt 2). Go: package `domain` không được import `adapter` (review + `go list` trong CI).
