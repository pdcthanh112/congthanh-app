# GraphQL supergraph

Mỗi service NestJS là một subgraph Federation v2 (code-first) và tự ghi SDL ra `services/<svc>/schema.graphql`
(`npm run schema:emit --workspaces --if-present`). Hai supergraph được khai báo ở `platform/graphql/`:

| Graph | Subgraph | Router | Đường dẫn qua APISIX |
|---|---|---|---|
| storefront | storefront-bff, catalog, customer, review, notification, message | graphql-router :4000 | `/graphql` |
| backoffice | backoffice-bff, catalog, review, notification, message | graphql-router-backoffice :4001 | `/admin/graphql` |

```bash
npm run supergraph         # wgc router compose → platform/graphql/<graph>/config.json + supergraph.graphql
npm run supergraph:check   # CI: composition phải thành công và supergraph.graphql đã commit phải khớp
```

`supergraph.graphql` là schema client nhìn thấy; review diff của file này để phát hiện thay đổi phá tương thích
(xoá/đổi kiểu trường). Muốn bỏ trường thì thêm `@deprecated` trước, xoá ở bản sau.

Router dùng chung `platform/graphql/router/config.yaml` (JWT Keycloak, giới hạn độ sâu/số trường, WebSocket
cho subscription, OTel). Quyết định: [ADR 0011](../../docs/adr/0011-nestjs-graphql-federation.md).
