import { NextRequest, NextResponse } from 'next/server';
import { GATEWAY_URL } from '@/graphql/client';
import { serverAccessToken } from '@/lib/auth/server-token';

/**
 * BFF REST: /api/gateway/<path> → {GATEWAY_URL}/api/ecommerce/<path>, gắn access token từ cookie NextAuth.
 * Dùng cho các API chưa nằm trong supergraph (search suggest, supplier, media, payment...).
 */
type Ctx = { params: Promise<{ path: string[] }> };

async function proxy(req: NextRequest, { params }: Ctx) {
  const { path } = await params;
  // Chặn path traversal: chỉ cho phép các đoạn path thường.
  if (path.some((p) => p === '..' || p === '.' || p.includes('/'))) {
    return NextResponse.json({ error: 'Bad path' }, { status: 400 });
  }
  const target = new URL(`${GATEWAY_URL}/api/ecommerce/${path.map(encodeURIComponent).join('/')}`);
  target.search = req.nextUrl.search;

  const token = await serverAccessToken();
  const headers: Record<string, string> = { Accept: req.headers.get('accept') ?? 'application/json' };
  const contentType = req.headers.get('content-type');
  if (contentType) headers['Content-Type'] = contentType;
  if (token) headers.Authorization = `Bearer ${token}`;

  const hasBody = !['GET', 'HEAD'].includes(req.method);
  let upstream: Response;
  try {
    upstream = await fetch(target, {
      method: req.method,
      headers,
      body: hasBody ? await req.arrayBuffer() : undefined,
      cache: 'no-store',
    });
  } catch {
    return NextResponse.json({ message: 'Upstream unavailable' }, { status: 502 });
  }
  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: { 'Content-Type': upstream.headers.get('content-type') ?? 'application/json' },
  });
}

export { proxy as GET, proxy as POST, proxy as PUT, proxy as PATCH, proxy as DELETE };
