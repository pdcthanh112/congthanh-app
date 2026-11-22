---
paths:
  - "backend/app/ecommerce/services/*-service/src/**/*.ts"
  - "backend/app/ecommerce/services/*-service/test/**/*.ts"
  - "backend/app/ecommerce/services/*-bff/**/*.ts"
  - "backend/app/ecommerce/libs/ts/**/*.ts"
---

# Rule TypeScript backend (NestJS, BFF, `libs/ts`)

Khuôn mẫu: `services/catalog-service` (GraphQL subgraph + gRPC + Drizzle), `services/storefront-bff` (BFF, không DB).
Format và lint bằng Biome (`biome.json`), luật tầng bằng dependency-cruiser (`.dependency-cruiser.cjs`), compiler
`tsconfig.base.json` (`strict`, `noUncheckedIndexedAccess`, `noImplicitOverride`, `useUnknownInCatchVariables`).

Nguồn tham khảo: Google TypeScript Style Guide, TypeScript Handbook (Do's and Don'ts), NestJS docs (providers, custom
providers, exception filters), typescript-eslint strict rules, Node.js best practices (goldbergyoni).

---

## 1. Định dạng

1. 2 space, dòng tối đa 120, **nháy đơn**, có chấm phẩy, trailing comma ở mọi chỗ (Biome). Vài file cũ còn nháy kép:
   file nào mình sửa thì chạy `npx biome check --write <file>`.
2. Import nội bộ ghi rõ đuôi **`.js`** (ESM NodeNext): `from '../domain/brand.js'`. Package nội bộ dùng tên workspace
   (`@ecommerce/platform`, `@ecommerce/clients`, `@ecommerce/shared`), không đường dẫn tương đối xuyên package.
3. Import chỉ để lấy kiểu dùng `type` (`import { type Clock, CLOCK } from './ports.js'` hoặc `import type { … }`).
4. Thứ tự import do Biome sắp; không sắp tay khác đi.
5. Tên file **kebab-case kèm vai trò**: `catalog.service.ts`, `catalog.resolver.ts`, `catalog.grpc.ts`,
   `unit-of-work.ts`, `delivery.worker.ts`, `env.ts`. Test `*.test.ts` cạnh file, tích hợp `test/*.it.test.ts`.
6. **Không `export default`**; luôn named export.

## 2. Đặt tên

1. Class, interface, type, enum: `PascalCase`. Biến, hàm, method, property: `camelCase`. Hằng số module-level và token
   DI: `UPPER_SNAKE_CASE` (`MAX_TAGS_PER_PRODUCT`, `UNIT_OF_WORK`).
2. Interface không có tiền tố `I`. Hậu tố theo vai trò: `XxxService` (use case), `XxxResolver`, `XxxRepository`,
   `XxxProps` (dữ liệu đầy đủ của thực thể), `XxxDraft` (dữ liệu tạo/sửa), `XxxModel`/`XxxInput` (kiểu GraphQL),
   `XxxFilter`.
3. Từ viết tắt viết như một từ: `HttpClient`, `GraphqlModule` theo thư viện; tự đặt thì `userId`, `apiUrl`.
4. Private field không tiền tố `_`; dùng `private readonly`.

## 3. Kiểu

1. Cấm `any` (Biome `noExplicitAny: error`). Không biết kiểu thì `unknown` rồi thu hẹp. Không `as unknown as X`.
2. Ép kiểu `as` chỉ khi đã kiểm; dữ liệu từ ngoài (HTTP, Kafka, env, JSON) kiểm bằng **`zod`** rồi dùng kiểu suy ra.
3. `interface` cho hình dạng object (`BrandProps`, `CategoryFilter`); `type` cho union, alias, mapped type
   (`type TagStatus = 'ACTIVE' | 'HIDDEN'`). Tập giá trị đóng ưu tiên union chuỗi; `enum` chỉ khi cần đăng ký với
   GraphQL (`registerEnumType`).
4. Tham số mảng/object không bị sửa khai báo `readonly` (`ids: readonly string[]`). Field không đổi sau khởi tạo là
   `readonly`.
5. Hàm public ghi rõ kiểu trả về (`Promise<Category>`); hàm nội bộ ngắn để suy kiểu.
6. Phân biệt `undefined` (không có/không gửi) và `null` (xoá giá trị), đúng như doc của port ghi. Tham số tuỳ chọn
   dùng `?:`.
7. Với `noUncheckedIndexedAccess`, phần tử lấy theo index có thể `undefined`: kiểm trước khi dùng; `!` chỉ khi đã
   chứng minh được.
8. `catch (e)` nhận `unknown`; kiểm `e instanceof X` trước khi đọc thuộc tính.

## 4. NestJS và lục giác

1. Port là `interface` trong `application/ports.ts`, kèm token `export const CLOCK = Symbol('Clock');`. Inject trong
   constructor bằng `@Inject(TOKEN) private readonly x: X`. Binding provider ↔ port khai trong `app.module.ts`.
2. Use case là class `@Injectable()` trong `application/`. Ghi dữ liệu trong `this.uow.run(async (tx) => { … })`; phát
   event qua `tx.events.*` (outbox) trong cùng transaction.
3. `domain/` là hàm/class thuần: không import NestJS, Drizzle, gRPC, `@ecommerce/clients`. Kiểm bằng
   dependency-cruiser.
4. Lỗi nghiệp vụ ném `DomainError.invalid(msg)`, `.notFound(msg)`, `.conflict(msg, 'CODE')`, … từ
   `@ecommerce/platform/errors`; filter chung đổi sang GraphQL/HTTP/gRPC. Không ném `HttpException` từ use case/domain.
5. Adapter vào: `adapters/in/graphql` (resolver + `types.ts` + `mappers.ts`), `adapters/in/grpc`. Adapter ra:
   `adapters/out/persistence` (Drizzle `schema.ts`, `repositories.ts`, `unit-of-work.ts`), client gRPC.
6. Quyền: decorator của `@ecommerce/platform` (`@Public()`, `@Roles(Role.ADMIN)`, `@CurrentPrincipal()`); mặc định
   mọi resolver cần đăng nhập.
7. BFF không có DB; gọi service qua `@ecommerce/clients`, gom N+1 bằng DataLoader (`adapters/in/graphql/loaders.ts`).
8. Thay đổi resolver/type GraphQL phải chạy `schema:emit` và `supergraph` để cập nhật `schema.graphql`.

## 5. Async và luồng điều khiển

1. Luôn `await` hoặc trả Promise; không để Promise trôi (floating). Cố ý chạy nền thì ghi rõ và bắt lỗi.
2. Chạy song song việc độc lập bằng `Promise.all`; tuần tự có `await` thì `for (const x of xs)`. Không `forEach` với
   hàm async.
3. `if` một dòng chỉ để thoát sớm thì được bỏ ngoặc:
   `if (!name) throw DomainError.invalid('brand name is required');`. Thân có hơn một câu lệnh thì luôn có `{}`.
4. Dùng `??` và `?.` thay kiểm tra null dài; `||` chỉ khi muốn coi chuỗi rỗng/0 là thiếu (có chủ đích).
5. Biến đổi dữ liệu dùng `map`/`filter`/`Set`/`Map`; không `reduce` khó đọc khi vòng lặp rõ hơn.
6. Nhóm method trong class lớn bằng comment phân đoạn: `// ---------- danh mục ----------`.

## 6. Dữ liệu

1. Truy vấn qua Drizzle (`schema.ts`); SQL thô chỉ khi Drizzle không diễn đạt được, luôn có tham số.
2. Migration sinh bằng `drizzle-kit generate` vào `drizzle/NNNN_*.sql`; CI kiểm migration không bị quên.
3. Cập nhật có tranh chấp dùng `version` (khoá lạc quan), như `update(category, expectedVersion)`.
4. Id thực thể sinh qua port `ID_GENERATOR` (Snowflake), thời gian qua port `CLOCK`.

## 7. Log và cấu hình

1. Log: `private readonly log = new Logger(X.name);` (NestJS). Không `console.*`.
2. Cấu hình đọc và kiểm trong `config/env.ts` (zod), inject nơi cần; không đọc `process.env` rải rác.

## 8. Test

1. Vitest, `describe`/`it`, mô tả `it` bằng **tiếng Việt** (`it('từ chối tên rỗng', …)`).
2. Test domain không cần DB. Test use case dùng port giả tự viết.
3. Test tích hợp `test/*.it.test.ts` dùng Postgres thật (`TEST_DATABASE_URL` hoặc Testcontainers), không mock DB.
4. Chạy: `npm run typecheck && npm run lint && npx vitest run services/<svc>` trong `backend/app/ecommerce`.
