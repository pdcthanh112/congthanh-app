# 0019. Elasticsearch trên Elastic Cloud; CloudFront + WAF trước API; AWS Backup và GuardDuty

- Trạng thái: Proposed
- Thay phần OpenSearch trong các ADR trước (search-service và module terraform `opensearch`)

## Bối cảnh
- search-service dùng OpenSearch Service. Nhóm muốn dùng Elasticsearch (tính năng mới hơn, Kibana, ES|QL) và
  không muốn tự vận hành cụm.
- API đi thẳng từ Internet vào NLB public của APISIX: không có WAF, không chặn bot, không chống DDoS tầng 7,
  không cache được các API đọc nhiều như danh sách sản phẩm. Chứng chỉ media phải tạo tay (`media_certificate_arn`).
- Không có kế hoạch sao lưu tập trung ngoài snapshot tự động của Aurora; không có phát hiện xâm nhập.
- Env test và staging đang dùng nhầm tên và state key của dev và prod (copy-paste), apply sẽ đè lên nhau.

## Quyết định
### 1. Elasticsearch 9 trên Elastic Cloud, nối qua PrivateLink
- Module `elastic-cloud` (provider `elastic/ec`): deployment autoscale ổ hot, Kibana, traffic filter chỉ nhận
  từ VPC endpoint của mình; VPC endpoint + private zone `vpce.<region>.aws.elastic-cloud.com`. Không có đường
  public vào cụm.
- search-service đổi adapter sang `elasticsearch`, xác thực bằng API key (`ELASTICSEARCH_API_KEY`), vẫn hỗ trợ
  basic auth. API giữ nguyên (`_search`, `_bulk`, completion suggester), mapping không đổi.
- Local: image `elasticsearch:9.5.3`, tắt security.

### 2. Route 53 + ACM, CloudFront + WAF + Shield trước API và media
- `dns`: hosted zone, chứng chỉ ACM ở us-east-1 (CloudFront) và ở region (dùng cho ALB/NLB sau này), CAA.
- `waf` (scope CLOUDFRONT): rate limit theo IP, rate limit chặt hơn cho flash sale và checkout, các bộ managed
  rule của AWS (IP reputation, bad inputs, SQLi, common), Bot Control tuỳ env, chặn quốc gia tuỳ chọn; log
  request bị chặn.
- `edge`: distribution cho `api.<domain>` dùng **CloudFront VPC origin** trỏ vào NLB **nội bộ** của APISIX
  (đổi scheme từ internet-facing sang internal). Mặc định không cache; vài đường GET công khai (sản phẩm, tìm
  kiếm, giá, tồn kho, gợi ý) cache theo `Cache-Control` của service. Shield Standard mặc định; Shield Advanced
  bật bằng biến khi đã đăng ký.
- CDN media dùng chứng chỉ từ `dns` và gắn cùng WAF.

### 3. AWS Backup (staging, prod) và GuardDuty (prod)
- Vault mã hoá bằng KMS riêng, chọn tài nguyên theo ARN (Aurora) và tag `Backup=<name>`. Prod: giữ hằng ngày 35
  ngày, hằng tháng 365 ngày (sang cold sau 30 ngày), vault lock, copy sang us-east-1. Báo lỗi qua SNS.
- GuardDuty với S3, EKS audit log, runtime monitoring (addon do GuardDuty quản lý), malware EBS, RDS login; cảnh
  báo từ mức 7 trở lên gửi email qua EventBridge + SNS.

### 4. Sửa env test/staging
Mỗi env có tên, state key và tag `Environment` của riêng mình.

## Hệ quả
- Phải apply hai lần khi dựng env mới (NLB do Kubernetes tạo nên chỉ biết ARN sau khi ArgoCD đồng bộ).
- Chi phí thêm: Elastic Cloud (theo dung lượng), WAF + Bot Control theo request, Shield Advanced $3000/tháng nếu
  bật, GuardDuty theo lượng log/runtime, dung lượng backup.
- Elastic Cloud là nhà cung cấp thứ hai ngoài AWS: cần API key tổ chức (`EC_API_KEY`) và hợp đồng riêng.
- Chưa chạy `terraform validate` (môi trường soạn không có terraform); cú pháp HCL và tham chiếu biến/output
  giữa module đã được kiểm bằng script. Cần `terraform init && terraform validate && terraform plan` trước khi apply.
