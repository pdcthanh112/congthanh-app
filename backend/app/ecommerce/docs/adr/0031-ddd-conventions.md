# 0031. Quy ước Domain-Driven Design cho các service

- Trạng thái: Proposed

## Bối cảnh
ADR 0002 chọn kiến trúc lục giác, nhưng chưa nói rõ phần DDD nên mỗi service làm một kiểu:
- Các service Java (order, payment, ledger...) và Go (inventory, cart) có aggregate giàu hành vi, value object và
  domain event.
- Nhiều service NestJS, Go nhỏ và Python có lớp domain mỏng, còn event thì sinh ở tầng use case.
- supplier-service (Django) chưa có lớp domain.
- product-service dùng entity JPA làm luôn domain model.
- Tầng app của webhook-service import thẳng `pgx`, vi phạm luật phụ thuộc.

## Quyết định
- **Bounded context = service.** Mỗi service sở hữu một schema riêng và một ngôn ngữ nghiệp vụ riêng. Service khác
  chỉ thấy service đó qua contract trong `api/`: gRPC, REST, hoặc event Protobuf. Một khái niệm có thể mang nghĩa khác
  nhau ở từng context (ví dụ "product" ở catalog khác ở order). Bản sao dữ liệu dựng từ event, không dùng chung kiểu.
- **Aggregate** là đơn vị nhất quán và khoá lạc quan (`version`). Một transaction chỉ sửa một aggregate, trừ khi có
  bảng phụ thuộc trong cùng context (ví dụ tổng điểm đánh giá). Aggregate tự giữ bất biến qua method có tên nghiệp vụ
  (`confirm`, `cancel`, `change_status`). Không có setter công khai, và không để entity ORM làm domain model.
- **Value object** là bất biến: Java `record`, Go struct truyền theo giá trị, TS `readonly`, Python
  `@dataclass(frozen=True)`.
- **Domain event**:
  - Mẫu chuẩn: aggregate gom event khi đổi trạng thái (`OrderEvent`, `events.go` của inventory), adapter persistence
    chuyển sang Protobuf rồi ghi outbox (ADR 0003).
  - Mẫu chấp nhận được cho service nhỏ: use case gọi port `XxxEvents` ngay sau khi aggregate đổi trạng thái, trong
    cùng unit of work. Đây là cách catalog-service (service mẫu NestJS) và review-service đang làm. Không bắt buộc
    chuyển sang mẫu chuẩn.
- **Domain mỏng là được** với service chủ yếu CRUD hoặc tính toán thuần (tax, pricing, search, media, translate...).
  Dù mỏng, vẫn phải giữ đúng luật phụ thuộc.
- **Transaction của adapter** (ví dụ `pgx.Tx` của consumer) đi vào use case qua một port, như `app.DeliveryScope`
  của webhook-service; tầng app không import driver DB.
- **supplier-service (Django)** giữ khung Django nhưng tách lớp như sau: `domain.py` (aggregate `Supplier` thuần
  Python, có vòng đời trạng thái), `ports.py`, `service.py` (use case), `repository.py` (adapter Django ORM),
  `container.py` (composition root). View và serializer là adapter vào.

## Hệ quả
- Trạng thái gian hàng của supplier-service giờ theo vòng đời PENDING → ACTIVE ⇄ SUSPENDED → CLOSED. Chuyển trạng
  thái sai trả 409, trước đây thì chuyển kiểu gì cũng được. Sửa thông tin gian hàng giờ có cập nhật `updated_at`.
- product-service vẫn vi phạm luật này (entity JPA nằm trong `domain/model`). Sẽ tách ở một thay đổi riêng, sau khi
  xong đợt sửa đang làm theo ADR 0030.
