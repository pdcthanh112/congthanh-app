import { cookies } from 'next/headers';
import { ACCESS_TOKEN_KEY } from '@/utils/constants/cookies';

export const isClient = () => typeof window !== 'undefined';

const getRequestURL = (url: string): string => {
  if (!url.startsWith('/')) {
    url = `/${url}`;
  }
  return `${process.env.NEXT_PUBLIC_APP_API_URL || process.env.NEXT_APP_API_URL}${url}`;
};

const refreshAccessToken = async (): Promise<string> => {
  const res = await fetch('/api/auth/refresh-token', {
    method: 'POST',
    credentials: 'include', // Bắt buộc để browser gửi cookie kèm request (cho phía client side)
    headers: { 'Content-Type': 'application/json' },
  });

  if (!res.ok) {
    throw new Error('Failed to refresh token');
  }

  const data = await res.json();
  return data.accessToken;
};

let isRefreshing = false;
let refreshPromise: Promise<string> | null = null;

interface RequestData {
  [key: string]: unknown;
}

interface ResponseData {
  [key: string]: unknown;
}

const getAccessToken = async (): Promise<string | null> => {
  if (!isClient()) {
    const cookieStore = await cookies();
    return cookieStore.get(ACCESS_TOKEN_KEY)?.value || null;
  } else {
    const res = await fetch('/api/auth/token');
    if (!res.ok) {
      return null;
    }
    const { accessToken } = await res.json();
    return accessToken;
  }
};

const httpRequest = async <TResponse extends ResponseData>(
  url: string,
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  data?: RequestData,
  options: RequestInit = {}
): Promise<TResponse> => {
  let headers: Record<string, string> = {};

  if (options.headers) {
    if (Array.isArray(options.headers)) {
      headers = Object.fromEntries(options.headers);
    } else if (options.headers instanceof Headers) {
      headers = Object.fromEntries(options.headers.entries());
    } else {
      headers = options.headers as Record<string, string>;
    }
  }

  const accessToken = await getAccessToken();

  if (accessToken) {
    headers = {
      ...headers,
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    };
  } else {
    headers = {
      ...headers,
      Accept: 'application/json',
    };
  }

  if (method !== 'GET' && !headers['Content-Type']) {
    headers = {
      ...headers,
      'Content-Type': 'application/json',
    };
  }

  const requestOptions: RequestInit = {
    method,
    ...(method !== 'GET' && { cache: 'no-store' }),
    ...(data && { body: JSON.stringify(data) }),
    ...options,
    headers,
  };

  const res = await fetch(getRequestURL(url), requestOptions);

  if (!res.ok) {
    const errorText = await res.text();
    if (res.status === 401 && accessToken && !isRefreshing) {
      isRefreshing = true;

      refreshPromise = refreshAccessToken()
        .then((newAccessToken) => {
          isRefreshing = false;
          refreshPromise = null;
          return newAccessToken;
        })
        .catch((err) => {
          isRefreshing = false;
          refreshPromise = null;
          throw err;
        });

      const newAccessToken = await refreshPromise;
      headers.Authorization = `Bearer ${newAccessToken}`;
      return httpRequest(url, method, data, { ...options, headers });
    }

    if (res.status >= 500) {
      throw new Error(`Server error calling API: ${url} - ${errorText}`);
    }
    try {
      const errorBody = JSON.parse(errorText) as { detail?: string };
      throw new Error(errorBody.detail || errorText);
    } catch {
      throw new Error(`API error: ${errorText}`);
    }
  }

  const response = await res.json();
  return response as TResponse;
};

export const http = {
  get: <TResponse extends ResponseData>(url: string, options?: RequestInit) =>
    httpRequest<TResponse>(url, 'GET', undefined, options),
  post: <TResponse extends ResponseData, TEntity extends RequestData>(
    url: string,
    data: TEntity,
    options?: RequestInit
  ) => httpRequest<TResponse>(url, 'POST', data, options),
  put: <TResponse extends ResponseData, TEntity extends RequestData>(
    url: string,
    data: TEntity,
    options?: RequestInit
  ) => httpRequest<TResponse>(url, 'PUT', data, options),
  patch: <TResponse extends ResponseData, TEntity extends RequestData>(
    url: string,
    data: TEntity,
    options?: RequestInit
  ) => httpRequest<TResponse>(url, 'PATCH', data, options),
  delete: <TResponse extends ResponseData>(url: string, options?: RequestInit) =>
    httpRequest<TResponse>(url, 'DELETE', undefined, options),
};
