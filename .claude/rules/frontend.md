---
paths:
  - "frontend/ecommerce/**/*.ts"
  - "frontend/ecommerce/**/*.tsx"
  - "frontend/ecommerce/**/*.css"
---

# Rule frontend storefront (`frontend/ecommerce`, Next.js 15 App Router + React 19)

Format bằng Prettier (`.prettierrc`), lint bằng ESLint flat config (`next/core-web-vitals`, `next/typescript`,
`storybook`). `next build` chạy lint, lỗi lint làm hỏng build. Stack: TanStack Query, next-intl, NextAuth, Zustand,
Tailwind + shadcn/ui (`@/components/ui`), zod + react-hook-form, react-toastify, Sentry.

Nguồn tham khảo: React docs (Rules of React, You Might Not Need an Effect), Next.js docs (Server/Client Components,
data fetching, caching), Airbnb React/JSX Style Guide, TanStack Query docs, Google TypeScript Style Guide,
WAI-ARIA Authoring Practices.

---

## 1. Định dạng

1. 2 space, dòng 120, nháy đơn trong TS, nháy kép trong thuộc tính JSX, có chấm phẩy, trailing comma `es5`, luôn có
   ngoặc quanh tham số arrow.
2. Import theo alias `@/…` (`@/api/orderApi`, `@/components/ui`, `@/i18n/routing`); không `../../..`.
3. Không `console.log`; chỉ `console.warn`/`console.error` cho lỗi thật (ESLint chặn). Biến/tham số không dùng bắt đầu
   bằng `_`.
4. Thứ tự import: React/Next → thư viện ngoài → `@/…` → tương đối → style.

## 2. Tổ chức thư mục và đặt tên

| Thứ | Ở đâu | Tên |
|---|---|---|
| Route, layout, page | `src/app/[locale]/…` | `page.tsx`, `layout.tsx`, `loading.tsx`, `error.tsx` (quy ước Next) |
| Component dùng chung | `src/components/<Ten>/` + `index.ts` re-export | `PascalCase.tsx` |
| Primitive UI (shadcn) | `src/components/ui/` | kebab-case theo shadcn (`button.tsx`), không sửa tuỳ tiện |
| Component theo nghiệp vụ | `src/features/<miền>/` | `PascalCase.tsx`, hook của miền ở `hooks.ts` |
| Hook dùng chung | `src/hooks/` | `useXxx.ts` |
| Gọi API | `src/api/<miền>Api.ts` | hàm `camelCase` động từ (`getMyCart`, `addProductToCart`) |
| GraphQL | `src/graphql/{client,documents,types}` | document `XxxQuery`, `XxxMutation` |
| Schema form | `src/models/schema/<ten>Schema.ts` | zod |
| State client toàn cục | `src/store/` | `useXxxStore` (Zustand) |
| Hằng số | `src/utils/constants/` | `UPPER_SNAKE_CASE` (`CART_KEY`) |
| Chuỗi dịch | `src/locales/<lang>/` | key theo namespace |

1. Component: `export default function TenComponent(props)`; một component chính mỗi file. Props kiểu
   `interface XxxProps` (hoặc inline khi chỉ 1–2 prop).
2. Hook bắt đầu bằng `use`, trả object khi trả nhiều giá trị.
3. Handler đặt tên `handleXxx` trong component, prop callback tên `onXxx`.

## 3. Server và Client Component

1. Mặc định là Server Component. Chỉ thêm `'use client';` ở dòng đầu khi cần state, effect, event handler, hook trình
   duyệt, hoặc thư viện chỉ chạy client.
2. Đẩy `'use client'` xuống lá nhỏ nhất có thể; không đánh dấu cả trang là client chỉ vì một nút.
3. Không import code server (secret, token server) vào Client Component. Biến môi trường cho client phải có tiền tố
   `NEXT_PUBLIC_` và không chứa bí mật.

## 4. Dữ liệu và state

1. Component không gọi `fetch` trực tiếp. Mọi lời gọi backend qua `src/api/*Api.ts`, đi supergraph bằng
   `gql(Document, vars)`; kiểu lấy từ `@/graphql/types`.
2. Dữ liệu server dùng **TanStack Query**: `useQuery({ queryKey: [CART_KEY, …], queryFn })`, `useMutation` với
   `onSuccess` invalidate/refresh. Query key là hằng trong `utils/constants/queryKey.ts` + tham số.
3. Không chép dữ liệu server vào `useState`/Zustand. Zustand chỉ cho state UI toàn cục (modal, …).
4. Không dùng `useEffect` để tính dữ liệu suy ra từ props/state; tính trực tiếp khi render (`useMemo` khi tốn kém).
   `useEffect` chỉ để đồng bộ với hệ thống bên ngoài.
5. Form dùng react-hook-form + zod resolver; schema ở `models/schema`.
6. Thông báo kết quả bằng `toast` (react-toastify), chuỗi qua i18n.

## 5. React

1. Tuân thủ Rules of Hooks: chỉ gọi hook ở top-level component/hook, không trong điều kiện/vòng lặp.
2. `key` của danh sách là id ổn định, không dùng index khi danh sách có thể đổi thứ tự.
3. Không sửa trực tiếp state/props; cập nhật bất biến.
4. Component quá 200 dòng hoặc nhiều trách nhiệm thì tách component con.
5. Điều kiện render: `cond ? <A /> : null` hoặc `cond && <A />` với `cond` là boolean thật (tránh in `0`).
6. Không định nghĩa component bên trong component khác.

## 6. Giao diện, i18n, a11y

1. **Mọi chữ hiển thị qua next-intl** (`useTranslations('namespace')` / `getTranslations` ở server). Thêm key vào
   `src/locales` cho **mọi ngôn ngữ đang có**; không viết cứng chữ trong JSX.
2. Điều hướng dùng `Link`/`useRouter`/`redirect` từ `@/i18n/routing` (giữ locale). `next/navigation` chỉ cho thứ
   routing của next-intl không có (`useSearchParams`, `notFound`).
3. Style bằng Tailwind class; gộp điều kiện bằng `cn(...)`. Không CSS inline trừ giá trị động.
4. Ảnh dùng `next/image` với `alt` có nghĩa; không `<img>`.
5. Phần tử bấm được là `<button>`/`<Link>`, không `<div onClick>`. Input có `<Label>`. Dialog dùng primitive có sẵn
   trong `@/components/ui` (đã lo focus và aria).
6. Tiền hiển thị theo `currency` đi kèm dữ liệu (Intl.NumberFormat theo locale), không giả định USD. Ngày giờ format
   theo locale.
7. Tập giá trị cố định khai báo `as const`, suy kiểu từ đó: `(typeof REASONS)[number]`.

## 7. Bảo mật

1. Không `dangerouslySetInnerHTML` với dữ liệu chưa làm sạch; dùng `@/lib/sanitize`.
2. Token/phiên lấy qua NextAuth (`useSession`/server session), không lưu token vào `localStorage`.
3. CSP khai trong `csp.config.ts`; thêm domain ngoài phải cập nhật đó.

## 8. Test

1. Unit/component: Vitest + Testing Library (`npm test`); truy vấn theo vai trò/nhãn (`getByRole`), không theo class.
2. Story cho component dùng chung trong Storybook khi thêm component mới ở `components/`.
3. E2E Playwright (`npm run test:e2e`) chỉ chạy khi người dùng yêu cầu.
4. Trước khi báo xong: `npm run check-types && npm run lint && npm test`.
