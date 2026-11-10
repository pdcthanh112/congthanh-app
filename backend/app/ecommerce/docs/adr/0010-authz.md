# 0010. Xác thực và phân quyền

- Trạng thái: Accepted

- **Keycloak** (realm `ecommerce`): OIDC + PKCE cho web, client credentials cho service, role thô (CUSTOMER, SELLER, ADMIN, SUPPORT) trong claim `roles`.
- **APISIX** chặn request thiếu/sai token ở các route bắt buộc đăng nhập; service vẫn tự xác thực JWT (defense in depth).
- **OpenFGA** cho quyền theo quan hệ (ai xem được đơn nào, seller sửa được sản phẩm nào) — model ở `platform/authz/openfga/model.fga`.
- Service-to-service: danh tính mTLS của Istio + AuthorizationPolicy.
