/**
 * Cấu hình kết nối. Dev: `ng serve` (cổng 3001, khớp redirect URI của client Keycloak `backoffice-web`) proxy `/api` và
 * `/graphql` sang gateway APISIX (proxy.conf.json), nên gọi API cùng origin, không cần CORS.
 */
export const APP_CONFIG = {
  keycloak: {
    url: 'http://localhost:8180',
    realm: 'ecommerce',
    clientId: 'backoffice-web',
  },
  /** REST product-service qua gateway. */
  apiBase: '/api/ecommerce',
  /** GraphQL storefront (đọc danh mục, dữ liệu công khai). */
  graphqlUrl: '/graphql',
} as const;
