# customer-service (NestJS)

Địa chỉ giao hàng, wishlist, lịch sử tìm kiếm của khách. Thông tin tài khoản (tên, email, mật khẩu) do Keycloak giữ;
`customerId` luôn là `sub` của access token, không bao giờ nhận từ client.

| Cổng                   | Dùng cho                                                                                                                            |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| HTTP `5004` `/graphql` | Subgraph: `Query.me` → `Customer { shippingAddresses, defaultShippingAddress, wishlist, recentSearches }`, góp `Product.inWishlist` |
| gRPC `19094`           | `CustomerService.GetShippingAddress` / `GetDefaultShippingAddress` cho order-service khi checkout                                   |

Quy tắc nghiệp vụ:

- Tối đa 10 địa chỉ; luôn đúng một địa chỉ mặc định (unique index một phần trong Postgres + khoá tư vấn theo khách
  để hai request song song không phá quy tắc). Xoá địa chỉ mặc định thì địa chỉ sửa gần nhất thành mặc định.
- Wishlist tối đa 200 sản phẩm, thêm trùng không lỗi. Thêm/bỏ ghi event `WishlistItemAdded/Removed` qua outbox
  (`ecom.customer.events`) để notification báo giảm giá/có hàng lại.
- Lịch sử tìm kiếm giữ 30 từ khoá gần nhất, từ khoá trùng được đưa lên đầu.

Chạy và test giống [catalog-service](../catalog-service/README.md):
`npx vitest run services/customer-service` (thêm `TEST_DATABASE_URL` để chạy test tích hợp).

So với bản Spring cũ: bỏ REST `/api/v1/shipping-address|wishlist|search-history`, bỏ Axon/Mongo read model
(địa chỉ đọc thẳng từ Postgres), bỏ bảng `wishlist` trung gian (khoá chính `(customer_id, product_id)`).
Địa chỉ dùng mã tỉnh/quận/phường thay cho `address_line_1..3`, để shipping-service tính phí.
