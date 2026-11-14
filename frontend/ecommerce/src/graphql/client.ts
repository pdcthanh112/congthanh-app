/**
 * Client GraphQL gọi Cosmo Router (supergraph storefront).
 * - Trình duyệt: POST /api/graphql (BFF cùng origin). Route handler gắn access token Keycloak đọc từ cookie httpOnly
 *   của NextAuth, nên token không bao giờ nằm trong JS phía client.
 * - Server (RSC, route handler, server action): gọi thẳng router qua gateway kèm token đọc từ cookie.
 * Mọi operation nằm trong ./documents.ts và được kiểm với supergraph bằng `npm run graphql:check`.
 */
import type { TypedDocument } from './documents';

/** Gateway APISIX: /graphql (router storefront) và /api/ecommerce/* (REST còn lại: search, supplier, media...). */
export const GATEWAY_URL =
  process.env.GATEWAY_URL ?? process.env.NEXT_PUBLIC_GATEWAY_URL ?? 'http://localhost:9080';
export const GRAPHQL_URL = process.env.GRAPHQL_URL ?? process.env.NEXT_PUBLIC_GRAPHQL_URL ?? `${GATEWAY_URL}/graphql`;
/** Endpoint BFF mà trình duyệt gọi. */
export const BROWSER_GRAPHQL_PATH = '/api/graphql';

export type GraphQLErrorCode =
  'UNAUTHENTICATED' | 'FORBIDDEN' | 'NOT_FOUND' | 'BAD_USER_INPUT' | 'UNAVAILABLE' | string;

export class GraphQLRequestError extends Error {
  constructor(
    message: string,
    readonly code: GraphQLErrorCode | undefined,
    readonly status: number,
    readonly errors: { message: string; extensions?: { code?: string } }[] = []
  ) {
    super(message);
    this.name = 'GraphQLRequestError';
  }
}

const isBrowser = () => typeof window !== 'undefined';

type TokenProvider = () => Promise<string | undefined>;
const PROVIDER_KEY = Symbol.for('congthanh.serverTokenProvider');
type WithProvider = typeof globalThis & { [PROVIDER_KEY]?: TokenProvider };

/** Đăng ký cách đọc token phía server (xem lib/auth/register-server-token.ts). Lưu trên globalThis để mọi bundle server dùng chung. */
export function registerServerTokenProvider(provider: TokenProvider) {
  (globalThis as WithProvider)[PROVIDER_KEY] = provider;
}

/** Access token phía server (cookie NextAuth). Phía trình duyệt luôn undefined: BFF tự gắn token. */
export async function serverToken(): Promise<string | undefined> {
  if (isBrowser()) return undefined;
  return (globalThis as WithProvider)[PROVIDER_KEY]?.();
}

export type GqlOptions = {
  /** Chỉ có tác dụng khi gọi từ server: cache dữ liệu public theo giây (ISR). Mặc định không cache. */
  revalidate?: number;
  /** Bỏ token: dùng cho dữ liệu public để có thể cache chung giữa mọi người dùng. */
  anonymous?: boolean;
};

type Body<T> = { data?: T; errors?: { message: string; extensions?: { code?: string } }[] };

async function send<TResult>(query: string, variables: unknown, opts: GqlOptions): Promise<[Response, Body<TResult>]> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/graphql-response+json, application/json',
  };
  let url = BROWSER_GRAPHQL_PATH;
  let cache: RequestInit = { cache: 'no-store' };
  if (!isBrowser()) {
    url = GRAPHQL_URL;
    const token = opts.anonymous ? undefined : await serverToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (opts.revalidate !== undefined && !token) cache = { next: { revalidate: opts.revalidate } } as RequestInit;
  }
  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify({ query, variables: variables ?? {} }),
    credentials: 'same-origin',
    ...cache,
  });
  const body = (await res.json().catch(() => ({}))) as Body<TResult>;
  return [res, body];
}

const unauthenticated = (res: Response, body: Body<unknown>) =>
  res.status === 401 || body.errors?.some((e) => e.extensions?.code === 'UNAUTHENTICATED');

async function request<TResult>(query: string, variables: unknown, opts: GqlOptions): Promise<TResult> {
  let [res, body] = await send<TResult>(query, variables, opts);
  // Token trong cookie có thể vừa hết hạn: gọi /api/auth/session để NextAuth làm mới cookie rồi thử lại một lần.
  if (isBrowser() && unauthenticated(res, body)) {
    const { getSession } = await import('next-auth/react');
    const session = await getSession();
    if (session && !session.error) [res, body] = await send<TResult>(query, variables, opts);
  }
  // Router trả lỗi từng trường kèm data một phần; coi là lỗi khi có errors để UI không hiển thị dữ liệu thiếu.
  if (body.errors?.length || !body.data) {
    const first = body.errors?.[0];
    throw new GraphQLRequestError(
      first?.message ?? `GraphQL HTTP ${res.status}`,
      first?.extensions?.code ?? (res.status === 401 ? 'UNAUTHENTICATED' : undefined),
      res.status,
      body.errors
    );
  }
  return body.data;
}

type VarsArg<TVariables> = TVariables extends Record<string, never> ? [] : [TVariables];

export async function gql<TResult, TVariables>(
  document: TypedDocument<TResult, TVariables>,
  ...[variables]: VarsArg<TVariables>
): Promise<TResult> {
  return request<TResult>(document, variables, {});
}

/** Như gql() nhưng nhận tuỳ chọn cache cho truy vấn phía server. */
export async function gqlWith<TResult, TVariables>(
  opts: GqlOptions,
  document: TypedDocument<TResult, TVariables>,
  ...[variables]: VarsArg<TVariables>
): Promise<TResult> {
  return request<TResult>(document, variables, opts);
}

export const isUnauthenticated = (e: unknown) => e instanceof GraphQLRequestError && e.code === 'UNAUTHENTICATED';
