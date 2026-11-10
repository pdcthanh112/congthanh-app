'use server';

import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import {
  ACCESS_TOKEN_KEY,
  ACCESS_TOKEN_EXPIRED_KEY,
  REFRESH_TOKEN_KEY,
  ROLE_KEY,
  cookie_config,
} from '@/utils/constants/cookies';
import { LoginResponse } from '@/models/types/AuthModel';

export const isServer = typeof window === 'undefined';

export const getCookies = async (): Promise<string | null> => {
  if (isServer) {
    // Server: đọc từ Next.js headers cookies (App Router)
    const cookieStore = await cookies();
    return cookieStore.get('access_token')?.value || null;
  } else {
    // Client: đọc từ document.cookie
    const match = document.cookie.match(/(?:^| )access_token=([^;]+)/);
    return match ? match[1] : null;
  }
};

export const setCookies = async (res: LoginResponse, response?: NextResponse) => {
  if (response) {
    response.cookies.set(ACCESS_TOKEN_EXPIRED_KEY, `${Date.now() + res.accessTokenMaxAge * 1000}`, {
      maxAge: res.accessTokenMaxAge,
    });
    response.cookies.set(ACCESS_TOKEN_KEY, res.accessToken, {
      maxAge: res.accessTokenMaxAge,
      ...cookie_config,
    });
    response.cookies.set(REFRESH_TOKEN_KEY, res.refreshToken, {
      maxAge: res.refreshTokenMaxAge,
      ...cookie_config,
    });
    response.cookies.set(ROLE_KEY, res.user.role, {
      maxAge: res.refreshTokenMaxAge,
      ...cookie_config,
    });
    response;
  } else {
    (await cookies()).set(ACCESS_TOKEN_EXPIRED_KEY, `${Date.now() + res.accessTokenMaxAge * 1000}`, {
      maxAge: res.accessTokenMaxAge,
    });
    (await cookies()).set(ACCESS_TOKEN_KEY, res.accessToken, {
      maxAge: res.accessTokenMaxAge,
      ...cookie_config,
    });
    (await cookies()).set(REFRESH_TOKEN_KEY, res.refreshToken, {
      maxAge: res.refreshTokenMaxAge,
      ...cookie_config,
    });
    (await cookies()).set(ROLE_KEY, res.user.role, {
      maxAge: res.refreshTokenMaxAge,
      ...cookie_config,
    });
  }
};

// let getCookie: (key: string) => string | undefined = () => "";

// if (isServer) {
//   const { cookies } = require("next/headers");
//   getCookie = (key: string) => cookies()?.get(key)?.value;
// } else {
//   getCookie = (key: string) => {
//     const match = document.cookie.match(new RegExp("(^| )" + key + "=([^;]+)"));
//     if (match) return match[2];
//     return "";
//   };
// }

// export { getCookie };
