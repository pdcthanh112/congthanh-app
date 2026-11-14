import { NextRequest, NextResponse } from 'next/server';
import { GRAPHQL_URL } from '@/graphql/client';
import { serverAccessToken } from '@/lib/auth/server-token';

/**
 * BFF GraphQL: trình duyệt gọi cùng origin, route này gắn access token (đọc từ cookie httpOnly của NextAuth)
 * rồi chuyển tiếp tới router. Nhờ vậy token Keycloak không bao giờ lộ ra JavaScript phía client.
 */
export async function POST(req: NextRequest) {
  const token = await serverAccessToken();
  let upstream: Response;
  try {
    upstream = await fetch(GRAPHQL_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: req.headers.get('accept') ?? 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: await req.text(),
    cache: 'no-store',
    });
  } catch {
    // Router/gateway không kết nối được: trả lỗi đúng định dạng GraphQL để UI hiện thông báo "dịch vụ tạm gián đoạn".
    return NextResponse.json(
      { data: null, errors: [{ message: 'Upstream unavailable', extensions: { code: 'UNAVAILABLE' } }] },
      { status: 502 }
    );
  }
  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: { 'Content-Type': upstream.headers.get('content-type') ?? 'application/json' },
  });
}
