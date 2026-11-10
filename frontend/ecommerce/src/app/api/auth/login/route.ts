import { NextResponse } from 'next/server';

export async function POST(request: Request) {
  const { email, password } = await request.json();

  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    return NextResponse.json({ error: 'Login failed' }, { status: 401 });
  }

  const { accessToken, refreshToken } = await response.json();

  return NextResponse.json({ message: 'Login successful', data: { accessToken, refreshToken } });
}
