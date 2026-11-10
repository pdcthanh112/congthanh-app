# Smoke test GraphQL qua gateway → Cosmo Router → subgraph (Hurl):
#   hurl --test --variable base=http://localhost:9080 --variable token=$TOKEN \
#        --variable admin_token=$ADMIN_TOKEN tests/e2e/graphql.hurl
# token: khách (CUSTOMER), admin_token: ADMIN. Lấy bằng `make -s token USER=... PASS=...`.

# Danh mục (catalog) và sản phẩm (storefront-bff) là public; router ghép Product từ nhiều subgraph.
POST {{base}}/graphql
```graphql
{
  categories(parentId: null) { id slug }
  products(limit: 3) {
    totalItems
    items { id name price { amount currency } ratingSummary { average count } }
  }
}
```
HTTP 200
[Asserts]
jsonpath "$.errors" not exists
jsonpath "$.data.categories" count > 0
jsonpath "$.data.products.totalItems" >= 0

# Token sai bị router chặn trước khi tới subgraph.
POST {{base}}/graphql
Authorization: Bearer not-a-jwt
```graphql
{ myCart { itemCount } }
```
HTTP *
[Asserts]
jsonpath "$.errors" count > 0

# Chưa đăng nhập: subgraph trả UNAUTHENTICATED cho đúng trường cần đăng nhập.
POST {{base}}/graphql
```graphql
{ myCart { itemCount } }
```
HTTP 200
[Asserts]
jsonpath "$.errors[0].extensions.code" == "UNAUTHENTICATED"

# Khách: giỏ hàng, wishlist, thông báo trong một truy vấn (3 subgraph).
POST {{base}}/graphql
Authorization: Bearer {{token}}
```graphql
{
  myCart { itemCount checkoutReady subtotal { formatted } }
  me { id wishlist { total } }
  unreadNotificationCount
}
```
HTTP 200
[Asserts]
jsonpath "$.errors" not exists
jsonpath "$.data.myCart.itemCount" >= 0
jsonpath "$.data.unreadNotificationCount" >= 0

# Khách không được vào trang quản trị.
POST {{base}}/admin/graphql
Authorization: Bearer {{token}}
```graphql
{ adminProducts(limit: 1) { totalItems } }
```
HTTP 200
[Asserts]
jsonpath "$.errors[0].extensions.code" == "FORBIDDEN"

# Admin: sản phẩm kèm tồn kho qua supergraph backoffice.
POST {{base}}/admin/graphql
Authorization: Bearer {{admin_token}}
```graphql
{ adminProducts(limit: 3) { totalItems items { id sku stock { available needsReorder } } } }
```
HTTP 200
[Asserts]
jsonpath "$.errors" not exists
jsonpath "$.data.adminProducts.totalItems" >= 0
