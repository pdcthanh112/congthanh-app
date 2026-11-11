# 0017. Dùng Redis thay Valkey

- Trạng thái: Proposed
- Thay thế phần "Valkey" trong ADR 0005, 0007, 0011 (các quyết định còn lại của những ADR đó giữ nguyên)

## Bối cảnh
Kho key-value trong bộ nhớ của hệ thống (cache giá và bản dịch, lịch sử chatbot, velocity của fraud, bộ đếm
flash sale bằng Lua, rate limit của APISIX và message-service, pub/sub cho GraphQL subscription) đang chạy Valkey
8.1. Valkey và Redis nói chung một giao thức, code chỉ dùng lệnh cơ bản (GET/SET/INCR/EXPIRE, EVAL, PUBLISH/
SUBSCRIBE) nên không có gì gắn chặt với Valkey. Yêu cầu là quay về Redis để dùng đúng sản phẩm gốc, tài liệu và
hệ sinh thái (Redis Insight, Redis Cloud, module JSON/Search/Bloom có sẵn trong Redis 8).

## Quyết định
- **Local và CI**: image `redis:8.10-alpine`, service compose tên `redis`, healthcheck bằng `redis-cli`.
- **Kubernetes ngoài AWS**: `platform/data/redis.yaml` (StatefulSet một primary, AOF, `noeviction`), Service
  `redis.data.svc.cluster.local`.
- **AWS**: ElastiCache engine `redis` (Redis OSS) bản `7.1`, là bản cao nhất ElastiCache hỗ trợ. Module Terraform
  đổi tên `module.redis`.
- **Thư viện**: TypeScript đổi `iovalkey` sang `ioredis` 6 (API giống nhau). Python (`redis-py`) và Go
  (`go-redis`) vốn đã dùng client Redis nên không đổi.
- **Cấu hình**: biến môi trường `VALKEY_URL` đổi thành `REDIS_URL`, biến test `TEST_VALKEY_URL` thành
  `TEST_REDIS_URL`. Tên file, class, adapter có chữ "valkey" đổi sang "redis".

## Hệ quả
- **Lệch phiên bản**: local/CI chạy Redis 8.10, ElastiCache chỉ có 7.1. Code không được dùng lệnh chỉ có ở Redis 8
  (hash field TTL kiểu `HSETEX`, vector set, module tích hợp) nếu vẫn chạy trên ElastiCache. Muốn Redis 8 managed
  thì phải sang Redis Cloud.
- **Chi phí**: ElastiCache tính giá node Redis OSS cao hơn Valkey (Valkey rẻ hơn khoảng 20% cùng loại node) và
  Serverless cho Redis OSS cũng đắt hơn.
- **Giấy phép**: Redis 8 là RSALv2/SSPLv1/AGPLv3 (Valkey là BSD). Tự chạy nội bộ thì không ảnh hưởng; nếu sau này
  bán Redis như một dịch vụ cho bên thứ ba thì phải xem lại.
- **Chuyển đổi trên AWS không làm tại chỗ được**: đổi engine `valkey` → `redis` khiến Terraform tạo lại
  replication group, mất toàn bộ dữ liệu trong bộ nhớ. Cache tự ấm lại; bộ đếm flash sale thì phải đóng mọi đợt
  sale đang mở trước khi apply (đóng sale sẽ đối soát lại với Postgres). Làm ngoài giờ cao điểm.
- **Deploy**: phải đổi `VALKEY_URL` → `REDIS_URL` trong Vault/secret và values cùng lúc với bản image mới, nếu
  không service sẽ rơi về mặc định `redis://localhost:6379`.
- **ioredis 6** mặc định nói RESP3 và cần Node 20 trở lên (đang chạy Node 24). Pub/sub và rate limiter đã thử lại
  với Redis thật.
