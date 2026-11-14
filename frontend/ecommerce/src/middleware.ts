import createMiddleware from 'next-intl/middleware';
import { NextRequest, NextResponse } from 'next/server';
import { getToken } from 'next-auth/jwt';
import { routing } from '@/i18n/routing';
import { defaultLocale, isValidLocale, stripLocale } from '@/lib/locales';
import { isProtectedPath, LOGIN } from '@/lib/routes';

const i18nMiddleware = createMiddleware(routing);

export default async function middleware(request: NextRequest): Promise<NextResponse> {
  const { pathname, search } = request.nextUrl;
  const path = stripLocale(pathname);

  if (isProtectedPath(path)) {
    const token = await getToken({ req: request });
    // Token làm mới thất bại cũng coi như chưa đăng nhập.
    if (!token || token.error) {
      const first = pathname.split('/')[1] ?? '';
      const locale = isValidLocale(first) ? first : defaultLocale;
      const login = new URL(`/${locale}${LOGIN}`, request.url);
      login.searchParams.set('callbackUrl', `${pathname}${search}`);
      return NextResponse.redirect(login);
    }
  }

  return i18nMiddleware(request);
}

export const config = {
  // Bỏ qua API, file tĩnh, nội bộ Next và tunnel Sentry (/monitoring).
  matcher: ['/((?!api|_next|_vercel|monitoring|.*\\..*).*)'],
};
