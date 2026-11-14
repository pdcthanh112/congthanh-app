# 0022. common-core là nền chung của service Java: mô hình lỗi, access log, auditing

- Trạng thái: Proposed
- Bổ sung cho 0002 (kiến trúc hexagonal) và 0003 (outbox, `libs/java/platform`); không thay thế

## Bối cảnh
`libs/java/common-core` được gắn vào cả 5 service Java nhưng không service nào scan package của nó, nên exception
handler, logging và CORS trong đó chưa từng chạy. Mỗi service tự viết `ProblemHandler` gần giống nhau, còn
product-service trả lỗi theo một khuôn khác (`{message, status}`). Code logging cũ ghi toàn bộ header và body
(lộ token), có `Thread.sleep` trong đường ghi log SQL, và CORS truyền nhầm danh sách origin vào method và header.

## Quyết định
- common-core thành auto-configuration (`CommonCoreAutoConfiguration`), tự bật ở mọi service Java. Mỗi bean nhường
  chỗ khi service tự khai báo (`@ConditionalOnMissingBean`); phần phụ thuộc JPA, Resilience4j, gRPC chỉ bật khi
  thư viện đó có trên classpath.
- Một mô hình lỗi: `DomainException` (thuần Java, domain kế thừa được) mang `ErrorKind` và `code`. `ErrorKind`
  gắn sẵn HTTP status và gRPC status. HTTP trả RFC 9457 với `type`, `code`, `detail`; lỗi validation thêm `errors`.
  Lỗi không lường trước trả 500 với thông điệp cố định, chi tiết chỉ nằm trong log.
- Lỗi hạ tầng dùng chung một chỗ: circuit mở, bulkhead đầy (503 + `Retry-After`), quá rate (429), optimistic lock
  và vi phạm ràng buộc dữ liệu (409).
- Access log một dòng mỗi request (method, path, status, thời gian, người gọi), đứng trước Spring Security để ghi
  được cả 401/403. Không ghi header, query string, body.
- Câu SQL chậm dùng `hibernate.log_slow_query` (mặc định 500ms) thay cho logger tự viết.
- CORS mặc định tắt vì APISIX đã xử lý; chỉ bật khi đặt `common.cors.allowed-origins`.
- JPA auditing bật chung; `created_by` / `updated_by` lấy từ người đăng nhập, không có thì ghi `system`.

## Hệ quả
- order, checkout, payment, promotion bỏ `ProblemHandler` riêng (checkout giữ phần `CHECKOUT_BUSY`). Body lỗi của
  payment và promotion có thêm `type`; thông điệp optimistic lock thống nhất một câu.
- product-service đổi body lỗi từ `{message, status}` sang RFC 9457. Frontend đã đọc `detail` nên không phải sửa.
  Người gọi nào còn đọc `message` của product-service thì phải chuyển sang `detail`.
- Bản ghi tạo bởi job nền của product-service ghi `created_by = system` thay cho giá trị tạm trước đây.
- 401/403 do filter của Spring Security trả vẫn chưa có body; muốn thống nhất thì thêm entry point dùng `Problems`.
