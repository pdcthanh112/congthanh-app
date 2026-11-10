/**
 * Client GraphQL gọi Cosmo Router qua gateway (`/graphql`, supergraph storefront).
 * Token Keycloak lấy từ session NextAuth: phía server qua getServerSession, phía trình duyệt qua getSession.
 * Mọi operation nằm trong ./documents.ts và được kiểm với supergraph bằng `npm run graphql:check`.
 */
import type { TypedDocument } from './documents';

/** Gateway APISIX: /graphql (router storefront) và /api/ecommerce/* (REST còn lại: search, supplier, media...). */
export const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL ?? 'http://localhost:9080';
export const GRAPHQL_URL = process.env.NEXT_PUBLIC_GRAPHQL_URL ?? `${GATEWAY_URL}/graphql`;

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

export async function accessToken(): Promise<string | undefined> {
  if (typeof window === 'undefined') {
    const [{ getServerSession }, { authOptions }] = await Promise.all([
      import('next-auth/next'),
      import('@/app/api/auth/[...nextauth]/route'),
    ]);
    return (await getServerSession(authOptions))?.accessToken;
  }
  const { getSession } = await import('next-auth/react');
  return (await getSession())?.accessToken;
}

export async function gql<TResult, TVariables>(
  document: TypedDocument<TResult, TVariables>,
  ...[variables]: TVariables extends Record<string, never> ? [] : [TVariables]
): Promise<TResult> {
  const token = await accessToken();
  const res = await fetch(GRAPHQL_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/graphql-response+json, application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ query: document, variables: variables ?? {} }),
    cache: 'no-store',
  });
  const body = (await res.json().catch(() => ({}))) as {
    data?: TResult;
    errors?: { message: string; extensions?: { code?: string } }[];
  };
  // Router trả lỗi từng trường kèm data một phần; coi là lỗi khi có errors để UI không hiển thị dữ liệu thiếu.
  if (body.errors?.length || !body.data) {
    const first = body.errors?.[0];
    throw new GraphQLRequestError(
      first?.message ?? `GraphQL HTTP ${res.status}`,
      first?.extensions?.code,
      res.status,
      body.errors
    );
  }
  return body.data;
}

export const isUnauthenticated = (e: unknown) => e instanceof GraphQLRequestError && e.code === 'UNAUTHENTICATED';
