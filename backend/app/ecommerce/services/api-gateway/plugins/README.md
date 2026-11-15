# Plugin tuỳ biến của APISIX

Đặt mỗi plugin vào một file `<tên-plugin>.lua` trong thư mục này. APISIX nạp chúng qua
`extra_lua_path: /usr/local/apisix/custom/?.lua` (đã khai trong `../apisix/config.yaml`); thư mục này được mount vào
`/usr/local/apisix/custom/apisix/plugins`.

Để bật một plugin mới:

1. Viết `plugins/<tên>.lua` theo khung plugin của APISIX (`schema`, `priority`, `name`, các phase `access`,
   `header_filter`...).
2. Thêm `<tên>` vào danh sách `plugins:` trong `../apisix/config.yaml`. APISIX chỉ nạp plugin có tên trong danh sách.
3. Dùng plugin:
   - cho mọi route: thêm vào `../k8s/global-rules.yaml`;
   - cho từng route: thêm khoá vào `../routes.yaml` và dạy `../scripts/gen-routes.py` sinh cấu hình plugin đó. Nhớ thêm
     khoá mới vào `ALLOWED_KEYS` trong `../tests/test_routes.py`.
4. Trên cluster, plugin phải có trong image: build `../Dockerfile` rồi đặt image đó vào `../k8s/values.yaml`
   (`image.repository`, `image.tag`), kèm `apisix.customPlugins` hoặc `extra_lua_path` tương ứng của chart.

Hiện chưa có plugin tuỳ biến nào; mọi route dùng plugin có sẵn của APISIX.
