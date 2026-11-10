# 0001. Polyglot services

- Trạng thái: Accepted
- Ngày: 2026-11-08

## Bối cảnh
Toàn bộ backend là Spring Boot. Một số service có tải đọc/ghi rất cao và logic đơn giản (giỏ hàng, tồn kho), một số có miền phức tạp (đơn hàng, thanh toán, khuyến mãi), một số chủ yếu là CRUD và tổng hợp dữ liệu cho frontend.

## Quyết định
- **Go 1.26**: cart-service, inventory-service (độ trễ thấp, ít RAM, khởi động nhanh, hợp autoscale theo Kafka lag).
- **Java 25 + Spring Boot 4**: product, order, payment, promotion (hệ sinh thái giao dịch, Temporal Java SDK, team đã quen).
- **NestJS (TypeScript)**: catalog, customer, review, notification, message và hai BFF (storefront, backoffice) với GraphQL Federation.
- Giao tiếp đồng bộ bằng gRPC (contract trong `api/proto`), bất đồng bộ bằng Kafka.

## Hệ quả
- Cần thư viện nền cho từng ngôn ngữ (`libs/go`, `libs/java`, `libs/ts`) để giữ chuẩn chung: log, trace, auth, outbox.
- CI tách theo ngôn ngữ, chỉ chạy khi thư mục liên quan đổi.
