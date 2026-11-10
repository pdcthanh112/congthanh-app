# ${{ values.name }}

Khuôn hexagonal (xem docs/adr/0002):

- `internal/domain`: thực thể + quy tắc + domain event, không import hạ tầng.
- `internal/app`: use case và port (`ports.go`).
- `internal/adapter/{http,grpc,messaging,repository/postgres}`.
- `cmd/server/main.go`: composition root (sao chép từ services/cart-service/cmd/server rồi đổi tên).
- `migrations/`: goose; luôn có `00002_outbox.sql` để dùng outbox.
