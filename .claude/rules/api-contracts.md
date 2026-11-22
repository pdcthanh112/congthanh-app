---
paths:
  - "backend/app/ecommerce/api/**"
  - "backend/app/ecommerce/services/**/adapter/**"
  - "backend/app/ecommerce/services/**/adapters/**"
  - "backend/app/ecommerce/services/**/*.graphql"
  - "backend/app/ecommerce/services/api-gateway/**"
  - "backend/app/ecommerce/platform/messaging/**"
---

# Rule contract: Protobuf, event, REST, GraphQL

Contract-first: `api/` là nguồn sự thật (ADR 0002, 0003, 0011). Đổi contract trước, sinh code, rồi mới sửa service.

Nguồn tham khảo: Google AIP (api.dev), Protobuf Style Guide và Proto Best Practices, Buf style guide, Zalando RESTful
API Guidelines, Microsoft REST API Guidelines, RFC 9457 (Problem Details), CloudEvents, Apollo Federation docs.

---

## 1. Protobuf

1. File ở `api/proto/ecommerce/<miền>/v1/`, package `ecommerce.<miền>.v1`, có
   `option java_multiple_files = true;` và `option java_package = "com.congthanh.ecommerce.<miền>.v1";`.
   File event tên `<miền>_events.proto`, service gRPC `<miền>_service.proto`.
2. Message `PascalCase`; field **`lower_snake_case`** cho field mới (buf đang tạm bỏ qua field camelCase cũ, không thêm
   mới); enum value `UPPER_SNAKE_CASE` có tiền tố tên enum và giá trị 0 là `<ENUM>_UNSPECIFIED`.
3. RPC mới dùng request/response riêng: `GetXxxRequest`/`GetXxxResponse`, không dùng chung message giữa các RPC.
4. Tiền dùng `ecommerce.common.v1.Money`; thời điểm `google.protobuf.Timestamp` (một số event cũ dùng epoch `int64`,
   giữ nguyên); id là `string` chứa số Snowflake.
5. **Tương thích ngược** (buf breaking `FILE`, Schema Registry `BACKWARD`): chỉ thêm field với số mới; không đổi số,
   kiểu, tên field đang dùng. Bỏ field thì `reserved` cả số và tên, đánh dấu `[deprecated = true]` trước một bản.
6. Mỗi message và field không hiển nhiên có comment tiếng Việt (đơn vị, định dạng, ai đặt giá trị, khi nào trống).
7. Sau khi sửa: `make proto` rồi `make gen`, commit kèm code sinh.

## 2. Event (Kafka)

1. Ghi qua outbox trong cùng transaction; Debezium bọc bằng `ecommerce.common.v1.EventEnvelope` (`id`, `type`,
   `source`, `aggregate_type`, `aggregate_id`, `occurred_at`, `traceparent`, `payload`).
2. Topic `ecommerce.<miền>.events`, DLQ `<topic>.dlq`, khai bằng `KafkaTopic` trong
   `platform/messaging/kafka/topics.yaml`. Comment đầu file proto ghi topic và message key.
3. Message key là id aggregate để giữ thứ tự theo aggregate.
4. Tên event là sự việc đã xảy ra, thì quá khứ: `OrderPlaced`, `PaymentCaptured`, `JournalEntryPosted`,
   `ProductUpserted`. Không đặt tên như lệnh (`CreateOrder`).
5. Event mang đủ dữ liệu để consumer không phải gọi ngược (event-carried state), gồm `currency` cho mọi số tiền.
6. Consumer idempotent theo `envelope.id` (`processed_events`), chịu được thứ tự lệch và event trùng; event không hiểu
   thì bỏ qua có log, không làm dừng partition.

## 3. REST

1. Path `/api/ecommerce/<miền>/<tài-nguyên>`; tài nguyên danh từ số nhiều, kebab-case (`/manual-entries`,
   `/trial-balance`); id trong path (`/entries/{id}`). Hành động không phải CRUD dùng sub-resource danh từ hoặc động từ
   rõ: `POST /entries/{id}/reversal`, `POST /manual-entries/{id}/approve`.
2. Method đúng nghĩa: `GET` đọc (không side effect), `POST` tạo/hành động (trả `201` khi tạo), `PUT` thay toàn bộ,
   `PATCH` sửa một phần, `DELETE` xoá (`204`).
3. JSON field `camelCase`. Thời gian ISO 8601 UTC. Tiền là chuỗi/số thập phân kèm `currency`, không float tính toán.
4. Lỗi: service Java trả `ProblemDetail` (RFC 9457) qua `CommonProblemHandler`; service Go trả phong bì
   `{status, message, errorCode}` của `httpx`. Mã lỗi nghiệp vụ `UPPER_SNAKE_CASE`. Status: `400` input sai, `401`
   chưa xác thực, `403` không đủ quyền, `404` không có, `409` xung đột/giá đổi, `422` vi phạm quy tắc nghiệp vụ khi đã
   có tiền lệ, `503` + `Retry-After` khi quá tải.
5. Danh sách có phân trang (`page`/`size` hoặc cursor) và giới hạn `size` tối đa.
6. Thao tác có tác động tiền/hàng nhận header `Idempotency-Key`.
7. Route mới phải khai trong `services/api-gateway/routes.yaml` rồi `make gen`; đường gọi đồng bộ mới giữa service phải
   mở trong `platform/mesh/istio/authorization-policies.yaml`.
8. OpenAPI (`api/openapi/*.yaml`) cập nhật cùng thay đổi; Spectral lint và oasdiff chặn breaking.

## 4. GraphQL (Federation v2)

1. Type `PascalCase`, field `camelCase`, enum value `UPPER_SNAKE_CASE`, input `XxxInput`, mutation động từ + danh từ
   (`createCategory`, `moveCategory`).
2. Entity chia sẻ khai `@key`, resolve bằng `@ResolveReference`; tránh N+1 bằng DataLoader.
3. Không xoá/đổi kiểu field đang dùng; đánh dấu `@deprecated(reason: "...")` trước.
4. Sau khi sửa resolver/type: `npm run schema:emit` và `npm run supergraph`, commit `schema.graphql` và supergraph.

## 5. gRPC

1. Server ở adapter vào, map proto ↔ domain tại adapter; không để kiểu proto lọt vào domain.
2. Lỗi đổi sang status code chuẩn (`INVALID_ARGUMENT`, `NOT_FOUND`, `ALREADY_EXISTS`/`ABORTED`, `PERMISSION_DENIED`,
   `UNAVAILABLE`).
3. Client luôn đặt deadline; chỉ retry RPC idempotent.
