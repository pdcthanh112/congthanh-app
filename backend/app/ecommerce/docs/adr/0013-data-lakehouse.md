# 0013. Data lakehouse: Kafka → Spark → Iceberg (Polaris, MinIO/S3) ← Trino, Airflow; tầng Bronze/ODS/Silver/Gold

- Trạng thái: Accepted

## Bối cảnh
- Dữ liệu giao dịch nằm rải rác trong schema của từng service; ClickHouse chỉ phục vụ dashboard, không giữ lịch sử
  thay đổi và không chia sẻ được cho ML.
- Đã có sẵn Kafka, outbox + Debezium (ADR 0003), nên lấy được mọi thay đổi mà không sửa code service.
- Chưa có dữ liệu hành vi (xem, tìm kiếm, thêm giỏ), cần cho phễu chuyển đổi và gợi ý sản phẩm.

## Quyết định
- **Định dạng bảng Iceberg** trên object storage (MinIO local, S3 trên AWS): Spark ghi, Trino đọc cùng một bảng,
  có snapshot để time travel và MERGE/DELETE theo dòng.
- **Catalog: Apache Polaris** (Iceberg REST catalog, lưu metadata trong Postgres). Chọn REST để Spark, Trino và
  sau này Flink/DuckDB dùng chung một catalog chuẩn; Polaris có RBAC theo catalog/namespace. Local dùng principal
  `root` và key MinIO tĩnh (`stsUnavailable`); trên AWS dùng IAM role + credential vending.
- **Bốn tầng**, mỗi tầng một namespace:

  | Tầng | Nguồn | Cách cập nhật | Nội dung |
  |---|---|---|---|
  | bronze | Kafka | streaming, append | event thô đúng như trên topic: outbox (protobuf), CDC (JSON), clickstream (JSON) |
  | ods | CDC Debezium bảng nghiệp vụ | streaming, MERGE theo khoá + LSN | bản sao trạng thái hiện tại của orders, order_lines, payments, products |
  | silver | ods + bronze | batch hằng giờ | đã chuẩn kiểu, khử trùng lặp theo event_id, bỏ PII, lọc bot |
  | gold | silver | batch sau silver | doanh thu ngày, bán theo sản phẩm, phễu, đặc trưng khách hàng (RFM) |

- **Giữ ODS riêng** thay vì để silver kiêm luôn: ODS là bản sao vận hành gần thời gian thực (trễ = chu kỳ micro-batch),
  giữ nguyên cấu trúc bảng nguồn, dùng để đối soát với OLTP, tra cứu và làm nguồn ổn định cho silver. Silver được
  làm lại theo lịch, đổi cấu trúc tự do mà không ảnh hưởng ai đang đọc trạng thái hiện tại. Lịch sử mọi thay đổi vẫn
  nằm ở `bronze.cdc_changes`.
- **CDC riêng cho ODS** (connector `ods-cdc`, topic `ods.<schema>.<bảng>`), tách khỏi outbox: outbox là event miền
  đã thiết kế cho consumer, CDC là trạng thái bảng. Cột nhạy cảm (payment_token, return_url, client_ip, redirect_url)
  bị loại ngay ở connector.
- **Spark 4 Structured Streaming** cho bronze/ods (micro-batch 30 giây), **Spark Connect** cho job batch để Airflow
  không cần cài Spark. Độ trễ vài chục giây là đủ; khi cần phát hiện bất thường dưới 1 giây thì thêm Flink đọc thẳng Kafka.
- **Clickstream:** service Go `clickstream-collector` nhận lô event JSON từ web/app, kiểm tra theo JSON Schema
  (`api/jsonschema/clickstream/v1`, đăng ký ở Apicurio), băm IP, ghi topic `clickstream.events` (key = session_id).
  Dùng JSON thay protobuf vì nguồn là trình duyệt; không qua outbox vì không có transaction DB.
- **Airflow** điều phối batch (silver → kiểm tra chất lượng → gold hằng giờ, bảo trì Iceberg hằng ngày).

## Hệ quả
- Bronze là at-least-once (job khởi động lại có thể ghi lại một micro-batch); ODS idempotent nhờ MERGE theo LSN,
  silver khử trùng lặp theo event_id.
- Streaming tạo nhiều file nhỏ và snapshot; ODS dùng merge-on-read nên tích file delete. Job `maintenance` gộp file
  và xoá snapshot cũ hơn 3 ngày (giữ tối thiểu 20).
- Payload outbox ở bronze/silver vẫn là protobuf; bước tiếp theo là giải mã bằng `from_protobuf` với descriptor sinh
  từ `buf build`, tách mỗi loại event thành bảng riêng.
- Silver/gold đang làm mới toàn bộ (CREATE OR REPLACE) vì dữ liệu local nhỏ; khi lớn cần chuyển sang incremental theo
  `_ods_updated_at`.
- Chạy đủ stack khá nặng (~6 GB RAM cho lakehouse + lõi); Airflow để ở profile riêng.
