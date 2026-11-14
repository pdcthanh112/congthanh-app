import { AxiosError } from 'axios';
import { GraphQLRequestError } from '@/graphql/client';

/** Khoá dịch (namespace "error") tương ứng với một lỗi API, để hiển thị thông điệp thân thiện thay vì lỗi thô. */
export type ErrorKey = 'generic' | 'unauthenticated' | 'forbidden' | 'not_found' | 'bad_input' | 'unavailable' | 'network';

export function errorKey(error: unknown): ErrorKey {
  if (error instanceof GraphQLRequestError) {
    switch (error.code) {
      case 'UNAUTHENTICATED':
        return 'unauthenticated';
      case 'FORBIDDEN':
        return 'forbidden';
      case 'NOT_FOUND':
        return 'not_found';
      case 'BAD_USER_INPUT':
        return 'bad_input';
      case 'UNAVAILABLE':
        return 'unavailable';
    }
    if (error.status >= 500) return 'unavailable';
    return 'generic';
  }
  if (error instanceof AxiosError) {
    if (!error.response) return 'network';
    const status = error.response.status;
    if (status === 401) return 'unauthenticated';
    if (status === 403) return 'forbidden';
    if (status === 404) return 'not_found';
    if (status === 400 || status === 422) return 'bad_input';
    if (status >= 500) return 'unavailable';
  }
  if (error instanceof TypeError) return 'network';
  return 'generic';
}
