# api-gateway

Edge gateway của hệ thống là **Apache APISIX** (ADR 0005). Thư mục này không chứa một gateway tự viết. Nó giữ mọi thứ
quyết định APISIX chạy thế nào: bảng route, cấu hình, plugin tuỳ biến và test.

## Cấu trúc

| Đường dẫn | Nội dung |
| --- | --- |
| `routes.yaml` | Nguồn sự thật duy nhất cho định tuyến north-south. Chỉ sửa file này. |
| `scripts/gen-routes.py` | Sinh cấu hình APISIX từ `routes.yaml`; `--check` để CI phát hiện file sinh bị cũ. |
| `apisix/config.yaml` | Cấu hình APISIX standalone cho local (danh sách plugin, OTel, Prometheus). |
| `apisix/apisix.yaml` | **File sinh**: route + upstream cho local. |
| `k8s/routes.yaml` | **File sinh**: `ApisixRoute` cho cluster. |
| `k8s/values.yaml`, `k8s/global-rules.yaml` | Helm values của chart `apisix/apisix` và plugin toàn cục. |
| `plugins/` | Plugin Lua tuỳ biến của repo (hiện chưa có plugin nào). |
| `tests/` | Kiểm tra `routes.yaml`: tên trùng, path trùng, upstream có thật, file sinh còn khớp. |
| `Dockerfile` | Image APISIX kèm plugin trong `plugins/`, dùng khi cluster cần plugin tuỳ biến. |

## Thêm hoặc sửa route

1. Sửa `routes.yaml` (chú thích các khoá nằm ở đầu file).
2. Sinh lại và kiểm tra, chạy từ `backend/app/ecommerce`:

   ```bash
   python3 services/api-gateway/scripts/gen-routes.py
   python3 -m unittest discover -s services/api-gateway/tests -v
   ```

   `make gen` và `make check` cũng gọi script này.
3. Commit `routes.yaml` cùng hai file sinh.

## Chạy

- **Local:** `deploy/local/docker-compose.yaml` chạy image `apache/apisix` và mount `apisix/config.yaml`,
  `apisix/apisix.yaml`, `plugins/`. Gateway nghe ở `http://localhost:9080`.
- **Cluster:** ArgoCD (`deploy/argocd/platform/platform-apps.yaml`) cài chart `apisix/apisix` với `k8s/values.yaml`
  (app `apisix`) và áp các manifest còn lại trong `k8s/` (app `apisix-routes`).

## Plugin tuỳ biến

Xem `plugins/README.md`.
