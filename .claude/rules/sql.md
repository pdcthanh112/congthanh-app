---
paths:
  - "backend/app/ecommerce/services/**/migrations/**"
  - "backend/app/ecommerce/services/**/db/changelog/**"
  - "backend/app/ecommerce/services/**/drizzle/**"
  - "backend/app/ecommerce/**/*.sql"
---

# Rule SQL và migration (Postgres)

Áp dụng cho migration của mọi ngôn ngữ (goose, Liquibase formatted SQL, Drizzle, SQL Python) và SQL viết tay trong
repository. Khuôn mẫu: `ledger-service/.../changes/0001-ledger.sql`, `product-service/.../changes/0001-products.sql`
(ADR 0030), `inventory-service/migrations`.

Nguồn tham khảo: PostgreSQL docs, "Don't Do This" (wiki.postgresql.org), GitLab database guidelines, sqlstyle.guide
(Simon Holywell), Liquibase best practices.

---

## 1. Quyền sở hữu

1. Mỗi service một schema (`DB_SCHEMA`); migration chỉ tạo/sửa đối tượng trong schema của service đó. Không FK, view,
   join sang schema service khác.
2. Bảng `outbox` và `processed_events` theo đúng cấu trúc thư viện nền (`libs/*/outbox`); không tự chế bản khác.

## 2. File migration

1. Đặt tên và đánh số theo công cụ: goose `NNNNN_mo_ta.sql`; Liquibase `changes/NNNN-mo-ta.sql` (đăng ký trong
   `db.changelog-master.yaml`); Drizzle sinh bằng `drizzle-kit generate`; Python `app/migrations/NNN_mo_ta.sql`.
2. Goose: mỗi file có `-- +goose Up` và `-- +goose Down`; khối có `$$` bọc `-- +goose StatementBegin/End`.
3. Liquibase: dòng đầu `--liquibase formatted sql`, mỗi changeset `--changeset <service>:<id>`; seed dùng
   `context:seed`.
4. Mặc định thêm migration mới, không sửa migration đã có. Sửa thẳng file gốc chỉ khi người dùng bảo (đã làm ở
   ADR 0028, 0030 vì chưa có dữ liệu thật) và phải nhắc reset schema local.
5. Không xoá hay viết lại file seed khi chưa hỏi người dùng.
6. Đầu file có comment tiếng Việt nói bảng dùng để làm gì và quy tắc quan trọng (chỉ ghi thêm, giữ bao lâu…).

## 3. Đặt tên

1. Tất cả `snake_case` chữ thường, không dấu ngoặc kép, không từ khoá SQL làm tên.
2. Bảng số nhiều (`journal_entries`, `postings`, `reservations`); bảng nối ghép tên hai bảng
   (`reservation_items`). Một số bảng cũ của product-service số ít: giữ nguyên.
3. Cột khoá chính `id`; khoá ngoại `<thực thể>_id` (`entry_id`, `product_id`). Boolean `is_xxx`/`has_xxx` hoặc tính từ
   rõ (`active`). Thời điểm `xxx_at` (`created_at`, `resolved_at`), ngày `xxx_date`.
4. Ràng buộc và index có tên tường minh, dạng `<bảng>_<cột>_<loại>`: `_pkey`, `_fk`, `_uq`, `_chk`, `_idx`
   (`stocks_product_warehouse_uq`, `stocks_reserved_le_quantity_chk`).

## 4. Kiểu dữ liệu

1. Id thực thể `BIGINT` (Snowflake, sinh ở ứng dụng), không `SERIAL`/`UUID` cho id mới.
2. Tiền `NUMERIC(19,4)` (hoặc `BIGINT` đơn vị nhỏ nhất ở service Go/Python), luôn có cột `currency VARCHAR(3) NOT NULL`
   cạnh nó. Không `REAL`/`DOUBLE PRECISION`/`MONEY`.
3. Thời điểm `TIMESTAMPTZ`, không `TIMESTAMP` không múi giờ.
4. Chuỗi `TEXT` hoặc `VARCHAR(n)` khi có giới hạn nghiệp vụ thật (mã ISO, mã trạng thái).
5. Tập giá trị đóng: `VARCHAR` + `CHECK (x IN (...))`, không Postgres `ENUM` (khó đổi).
6. Dữ liệu bán cấu trúc `JSONB`, không `JSON`. Không nhét vào JSONB thứ cần ràng buộc hay truy vấn thường xuyên.

## 5. Ràng buộc nằm ở DB

1. Mọi cột bắt buộc là `NOT NULL`. Quan hệ có `FOREIGN KEY`. Tính duy nhất có `UNIQUE` (kể cả unique một phần
   `WHERE deleted_at IS NULL` cho xoá mềm). Miền giá trị có `CHECK` (`amount > 0`, `reserved <= quantity`).
2. Ràng buộc phức tạp dùng FK nhiều cột, cột sinh (`GENERATED ALWAYS AS`), trigger khi cần (ví dụ ledger chặn
   `UPDATE/DELETE`).
3. So sánh không phân biệt hoa thường (SKU, email) dùng unique index trên `lower(x)` hoặc `citext`.
4. Bảng có cập nhật tranh chấp có cột `version` cho khoá lạc quan.

## 6. Index

1. Mỗi FK có index nếu được truy vấn theo cột đó. Index theo đúng thứ tự cột của truy vấn thật.
2. Không thêm index "phòng khi cần". Index trên bảng lớn đang chạy dùng `CREATE INDEX CONCURRENTLY` (migration riêng,
   không trong transaction).

## 7. Viết SQL

1. Từ khoá viết HOA (`SELECT`, `FROM`, `WHERE`), tên đối tượng chữ thường. Căn cột trong `CREATE TABLE` cho dễ đọc như
   file mẫu.
2. Liệt kê cột tường minh; không `SELECT *` trong code ứng dụng, không `INSERT` không liệt kê cột.
3. Luôn tham số bind (`:name`, `$1`, `?`); không nối chuỗi input.
4. `UPDATE`/`DELETE` luôn có `WHERE`; kiểm số dòng bị ảnh hưởng khi dùng khoá lạc quan.
5. Upsert dùng `INSERT ... ON CONFLICT (...) DO UPDATE/DO NOTHING`.
6. Hàng đợi/quét job nhiều pod dùng `SELECT ... FOR UPDATE SKIP LOCKED`.
7. Phân trang dữ liệu lớn dùng keyset (`WHERE id > :after ORDER BY id LIMIT n`) thay `OFFSET` lớn.
8. Không dùng `NOT IN (subquery)` với cột có thể `NULL`; dùng `NOT EXISTS`.
9. Đổi schema có dữ liệu thật theo từng bước tương thích (thêm cột nullable → backfill → thêm ràng buộc → đổi code →
   xoá cột cũ ở bản sau).
