# Management (backoffice)

Trang quản trị của CongThanh Shop, Angular 17 (standalone component, không SSR vì mọi trang đều sau đăng nhập).
Hiện có phần quản trị thông số sản phẩm (ADR 0033): thuộc tính và giá trị cho phép, nhóm hiển thị, mẫu theo danh mục,
thông số tự do của sản phẩm.

## Chạy local

Cần stack local đang chạy (gateway APISIX `localhost:9080`, Keycloak `localhost:8180`) và một tài khoản có role `ADMIN`.

```bash
npm ci
npm start
```

Mở http://localhost:3001 (cổng này khớp redirect URI của client Keycloak `backoffice-web`). App chuyển sang trang đăng
nhập Keycloak (Authorization Code + PKCE). `ng serve` proxy `/api` và `/graphql` sang gateway (`proxy.conf.json`) nên
gọi API cùng origin. Khi deploy, đặt app sau cùng domain với gateway hoặc bật CORS cho origin của backoffice ở APISIX.

Tài khoản không có role `ADMIN` thấy trang "Không có quyền". Đây chỉ là ẩn giao diện: product-service tự kiểm role ở
mọi API ghi.

## Kiểm tra

```bash
npx ng build
npx ng test --watch=false --browsers=ChromeHeadless
```

## Cấu trúc

- `src/app/core`: đăng nhập Keycloak (`auth.service.ts`), interceptor gắn token, guard ADMIN, thông báo lỗi.
- `src/app/catalog`: gọi API product-service (`catalog-api.service.ts`) và các trang quản trị thông số.
