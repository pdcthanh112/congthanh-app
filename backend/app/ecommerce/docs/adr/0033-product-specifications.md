# 0033. Thông số sản phẩm: nhiều giá trị, giá trị cho phép, nhóm hiển thị, mẫu theo danh mục

- Trạng thái: Proposed
- Bổ sung cho ADR 0030 (không thay thế)

## Bối cảnh
ADR 0030 tách hai khái niệm: **trục biến thể** (`variant_option`, ví dụ Màu, Size: mỗi tổ hợp là một biến thể có
SKU, giá, tồn kho riêng, khách phải chọn) và **thuộc tính mô tả** (`product_attribute` + `product_attribute_value`,
EAV có kiểu: Chất liệu, Xuất xứ, Cổ áo... dùng chung cho mọi biến thể, khách không chọn). Bảng thông số của các sàn
lớn còn cần ba thứ mà mô hình 0030 chưa có:

1. Một thuộc tính mang nhiều giá trị ("Phong cách: Thể thao, Cơ bản, Hàn Quốc"). 0030 unique `(product_id,
   product_attribute_id)` và chỉ có TEXT/NUMBER/BOOLEAN.
2. Danh sách giá trị cho phép, để seller chọn thay vì gõ ("Cổ tròn" / "cổ tròn" / "Cổ Tròn" thành ba facet).
3. Nhóm hiển thị ("Màn hình", "Pin") và mẫu thông số theo danh mục (Áo Thun có Cổ áo, Laptop có RAM) để form của
   seller biết hiện ô nào và trang danh mục biết hiện bộ lọc nào.

## Quyết định
- **MULTI_SELECT**: thêm vào `product_attribute.data_type`. Giá trị lưu mỗi giá trị một dòng; unique đổi thành
  `(product_id, product_attribute_id, lower(value))`. Constraint trigger (DEFERRABLE, kiểm lúc commit) bảo đảm thuộc
  tính không phải MULTI_SELECT chỉ có một giá trị mỗi sản phẩm, và chặn đổi một thuộc tính khỏi MULTI_SELECT khi đã có
  sản phẩm mang nhiều giá trị. Domain kiểm trước để trả 400 rõ ràng; trần 20 giá trị một thuộc tính một sản phẩm.
- **Giá trị cho phép**: bảng `product_attribute_option(product_attribute_id, value, display_order)`, unique theo
  `lower(value)`, chỉ cho TEXT/MULTI_SELECT. Có danh sách thì giá trị phải nằm trong đó (domain viết lại đúng chính tả
  của danh sách; trigger ở DB chặn lần nữa). Danh sách rỗng là nhập tự do.
- **Nhóm hiển thị**: bảng `attribute_group(name, display_order)`, `product_attribute.group_id` FK `ON DELETE SET NULL`.
  Thứ tự trên trang chi tiết: nhóm theo `display_order` (không nhóm xếp cuối), rồi `product_attribute.display_order`.
- **Mẫu theo danh mục**: bảng `category_attribute(category_id, product_attribute_id, is_required, display_order)`.
  Danh mục thuộc catalog-service nên `category_id` không có FK (database per service); service kiểm danh mục tồn tại
  qua gRPC trước khi ghi, ngoài transaction.
- API dưới `/api/ecommerce/product-attribute` (gateway đã route về product-service; đọc công khai, ghi cần ADMIN):
  `/groups`, `/categories/{categoryId}`. `attribute-value` nhận `values` (mảng) bên cạnh `value`; tạo/sửa trả mọi giá
  trị của thuộc tính.
- Phía đọc không đổi hợp đồng: event `ProductUpserted.attributes` vốn là `repeated`, MULTI_SELECT gửi nhiều phần tử cùng
  `code`; Elasticsearch lưu `attributes` dạng nested nên lọc và facet theo từng giá trị chạy như cũ. Read model Mongo và
  BFF thêm tên nhóm; GraphQL `ProductAttribute` thêm `group`, BFF gộp nhiều giá trị thành một dòng.
- **Thông số tự do (JSONB)**: `products.specifications jsonb NOT NULL DEFAULT '[]'` dạng
  `[{"name": "Màn hình" | null, "items": [{"name", "value"}]}]`, chỉ ở sản phẩm gốc (CHECK), tối đa 64 KB. Domain
  (`Specifications`) kiểm cấu trúc: tối đa 30 nhóm/300 dòng, tên/giá trị không rỗng, tên dòng không trùng trong nhóm.
  Ghi bằng `PUT /api/ecommerce/products/{id}/specifications` kèm `version` (khoá lạc quan). Cột đọc/ghi bằng JdbcClient,
  không qua entity JPA. Trang chi tiết hiện sau thông số EAV, bỏ dòng trùng tên một thuộc tính.
- **Ranh giới với JSONB**: thông số cần lọc, so sánh, dịch hoặc kiểm giá trị đi qua EAV ở trên; JSONB chỉ cho phần ngoài
  mẫu, chỉ để hiển thị, không đưa vào event hay Elasticsearch.
- **Giao diện quản trị** ở `frontend/management` (Angular, không SSR, cổng dev 3001 khớp client Keycloak
  `backoffice-web`, đăng nhập PKCE bằng keycloak-js). Chỉ ADMIN thấy menu; API vẫn tự kiểm role. Dev proxy `/api` và
  `/graphql` sang gateway; khi deploy cần cùng domain với gateway hoặc bật CORS cho origin backoffice ở APISIX.

## Hệ quả
- Migration mới `0005-attribute-groups-options-templates.sql` và `0006-product-specifications.sql`, không sửa 0001;
  schema local chạy tiếp, không cần reset.
- Ràng buộc qua nhiều dòng nằm trong trigger plpgsql: ghi thẳng bằng SQL vẫn bị chặn, nhưng lỗi chỉ hiện lúc commit.
- Đổi danh sách giá trị cho phép không sửa giá trị cũ đã lưu trên sản phẩm; giá trị cũ ngoài danh sách vẫn hiển thị cho
  tới khi sản phẩm được sửa lại.
- `is_required` trong mẫu chưa được ép khi kích hoạt sản phẩm: cần quyết định riêng (chặn ACTIVE hay chỉ cảnh báo) khi
  làm form seller, vì sản phẩm cũ chưa điền đủ.
