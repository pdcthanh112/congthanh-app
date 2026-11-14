import 'server-only';
import { cookies } from 'next/headers';
import { getToken } from 'next-auth/jwt';

/**
 * Đọc access token Keycloak từ cookie JWT (httpOnly) của NextAuth, chỉ dùng phía server
 * (Route Handler BFF, Server Component, Server Action). Token không bao giờ được gửi xuống trình duyệt.
 * Token hết hạn thì trả undefined: dữ liệu public vẫn lấy được, còn client sẽ tự làm mới qua /api/auth/session.
 */
export async function serverAccessToken(): Promise<string | undefined> {
  const store = await cookies();
  const token = await getToken({
    req: { cookies: Object.fromEntries(store.getAll().map((c) => [c.name, c.value])), headers: {} } as never,
    secret: process.env.NEXTAUTH_SECRET,
  });
  if (!token?.accessToken || token.error) return undefined;
  if (token.expiresAt && Date.now() / 1000 >= token.expiresAt) return undefined;
  return token.accessToken;
}
