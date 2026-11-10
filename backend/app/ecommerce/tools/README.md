# tools/

- `scripts/gen-apisix-routes.py` — sinh cấu hình APISIX từ `platform/gateway/routes.yaml` (`--check` trong CI).
- `scripts/gen-asyncapi.py` — sinh `api/asyncapi/asyncapi.yaml` từ proto event (`--check` trong CI).
- `backstage/templates` — Software Template tạo service mới đúng khuôn.
- `agents/` — Java agent (OpenTelemetry) dùng khi chạy service Java ngoài K8s; file .jar không commit.
