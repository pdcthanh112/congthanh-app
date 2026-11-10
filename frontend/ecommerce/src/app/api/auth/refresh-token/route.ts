import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY } from '@/utils/constants/cookies';

export async function POST() {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(REFRESH_TOKEN_KEY)?.value;

  if (!refreshToken) {
    return NextResponse.json({ error: 'Refresh token missing' }, { status: 401 });
  }

  const response = await fetch(`${process.env.NEXT_APP_API_URL}/auth/refresh-token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });

  if (!response.ok) {
    return NextResponse.json({ error: 'Failed to refresh token' }, { status: 401 });
  }

  const { accessToken: newAccessToken } = await response.json();

  cookieStore.set(ACCESS_TOKEN_KEY, newAccessToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'strict',
    path: '/',
    maxAge: 15 * 60,
  });

  return NextResponse.json({ accessToken: newAccessToken });
}
