# Contract tests

- **gRPC + event (Protobuf)**: `buf breaking --against '.git#branch=main,subdir=backend/app/ecommerce'` chạy trong
  workflow `proto.yml`; mọi thay đổi phá tương thích bị chặn trước khi merge.
- **Schema Registry**: rule BACKWARD trên Apicurio chặn ở runtime nếu producer đăng ký schema không tương thích.
- **REST/OpenAPI**: `api/openapi/*.yaml` được lint bằng Spectral (`.spectral.yaml`) và so sánh breaking bằng `oasdiff`.
- **GraphQL**: `rover subgraph check` (giai đoạn 3).
