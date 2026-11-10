# Smoke test qua gateway sau mỗi lần deploy (Hurl):
#   hurl --test --variable base=http://localhost:9080 --variable token=$TOKEN tests/e2e/smoke.hurl

# Đọc tồn kho công khai
GET {{base}}/api/ecommerce/warehouses/
HTTP 200
[Asserts]
jsonpath "$.status" == "Success"

# Route giỏ hàng bắt buộc đăng nhập: thiếu token → gateway chặn
GET {{base}}/api/ecommerce/carts/customer/me
HTTP 401

# Có token: tạo giỏ, thêm hàng, validate
POST {{base}}/api/ecommerce/carts/
Authorization: Bearer {{token}}
{"name": "smoke"}
HTTP 201
[Captures]
cart_id: jsonpath "$.data.id"

POST {{base}}/api/ecommerce/carts/{{cart_id}}/validate
Authorization: Bearer {{token}}
HTTP 200
[Asserts]
jsonpath "$.data.valid" == false

DELETE {{base}}/api/ecommerce/carts/{{cart_id}}
Authorization: Bearer {{token}}
HTTP 200
