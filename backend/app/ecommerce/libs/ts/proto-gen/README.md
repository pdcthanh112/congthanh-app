# @ecom/proto

Code TypeScript sinh từ `api/proto` bằng Buf + `protoc-gen-es` (Protobuf-ES v2). Không commit: `npm run proto`
(hoặc `npm run build -w @ecom/proto`) sinh vào `src/gen/` rồi biên dịch ra `dist/`.

- Message: `create(OrderPlacedSchema, {...})`, `toBinary`, `fromBinary` của `@bufbuild/protobuf`.
- Service gRPC: dùng với Connect-ES (`@connectrpc/connect`, `createClient(ProductService, transport)`), nói được
  gRPC thật (HTTP/2) với server Java/Go.
