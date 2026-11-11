# 0020. Read model trang sản phẩm trên MongoDB, dựng từ event Kafka; MongoDB Atlas

- Trạng thái: Proposed
- Hoàn thiện CQRS của product-service (thay cho projection của Axon trước đây)

## Bối cảnh
product-service đã có read model MongoDB nhưng:
- Cập nhật bằng listener chạy ngay sau commit trong request. Mongo lỗi hay pod chết đúng lúc đó thì cập nhật mất
  luôn (chỉ có log warn). Đọc thẳng Postgres chỉ cứu khi document chưa có; document đã có mà cũ thì trả dữ liệu
  sai (giá, trạng thái) tới lần sửa sau.
- Document chỉ chép vài cột của bảng `products`; trang chi tiết vẫn đọc Postgres, gọi catalog-service và join ảnh,
  thuộc tính, variant. Giá thật (pricing-service), điểm review, tồn kho không có ở đâu trong product-service.
- Không có MongoDB nào trên AWS (terraform, `platform/data` đều không có).

## Quyết định
### 1. Một document cho mỗi sản phẩm, gộp sẵn mọi thứ trang chi tiết cần
Phần lõi (tên, mô tả, trạng thái, danh mục kèm tên, ảnh, thuộc tính, variant kèm tuỳ chọn) và các phần từ service
khác: `prices.<bảng giá>`, `rating`, `stock.<kho>` + `inStock`, tên thương hiệu. Trang chi tiết đọc một document
theo slug.

### 2. Projection đọc Kafka, idempotent theo mốc thời gian
- product-service tự consume (group `product-read-model`) `ecommerce.product.events`, `pricing`, `review`,
  `inventory`, `catalog`. Event cùng sản phẩm cùng partition nên xử lý tuần tự.
- Event của product-service chỉ là tín hiệu: projection đọc lại sản phẩm từ Postgres trong một transaction
  REPEATABLE READ; thời điểm snapshot (đồng hồ Postgres) là `coreVersion`. Phần lõi chỉ ghi đè khi
  `coreVersion` mới hơn, bằng upsert có điều kiện, nên event giao lại, sai thứ tự hay chạy dựng lại song song
  đều không làm lùi dữ liệu. Variant đổi thì dựng lại cả sản phẩm cha.
- Phần từ service khác cập nhật từng trường, mỗi trường một mốc (`versions.price_<list>`, `versions.stock_<kho>`,
  ...), lấy từ `effective_at`/`updated_at` của event hoặc `occurred_at` của envelope.
- Đổi ảnh, thuộc tính cũng phát `ProductChanged` (trước đây chỉ tạo/sửa/xoá sản phẩm mới phát).
- Lỗi: Mongo/catalog-service tạm sập thì thử lại mãi, giữ thứ tự, không đẩy event vô tội vào DLQ; payload hỏng vào
  thẳng `<topic>.dlq`; lỗi khác thử ~30 giây rồi vào DLQ.
- Không dùng bảng `processed_events`: mọi lệnh ghi đã idempotent.
- `POST /api/ecommerce/products/read-model/rebuild` (ADMIN) dựng lại toàn bộ từ Postgres.

### 3. Đọc
Mongo trước; chưa có document (hoặc Mongo lỗi) thì dựng tạm từ Postgres, không ghi lại (tránh ghi khi tải đọc cao).
Document chưa có phần lõi (mới có giá/tồn kho) coi như chưa có.

### 4. MongoDB Atlas qua PrivateLink
Module `mongodb-atlas`: project, cluster replica set 3 node MongoDB 8 có autoscaling, PrivateLink (project không mở
IP nào), user riêng cho product-service. Dev/test M10, staging M20, prod M30 tới M60 có backup point-in-time và
chống xoá nhầm. Không chọn DocumentDB (chỉ tương thích một phần API Mongo) hay tự chạy trên EKS (phải tự lo backup,
nâng cấp).

## Hệ quả
- Dữ liệu trang sản phẩm trễ hơn Postgres một chút (bằng consumer lag), bù lại trang chỉ cần một lần đọc và không
  phụ thuộc catalog-service hay pricing-service lúc đọc.
- Thêm một nhà cung cấp ngoài AWS (Atlas), cần service account của organization khi chạy terraform.
- Chưa có giá flash sale riêng trong document: inventory-service chưa phát event mở/đóng đợt; giá sale đã đi qua
  `PriceChanged` của pricing-service.
- Provider `mongodb/mongodbatlas` 2.x viết theo tài liệu đã biết, chưa chạy `terraform validate`; cần
  `terraform init && terraform validate && terraform plan` trước khi apply.
