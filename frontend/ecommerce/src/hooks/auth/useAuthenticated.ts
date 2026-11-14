import { useSession } from 'next-auth/react';
import { login, logout } from '@/lib/auth/actions';

/** Trạng thái đăng nhập + thao tác Keycloak dùng chung cho header/sidebar. */
export function useAuthenticated() {
  const { data: session, status } = useSession();

  return {
    user: session?.user,
    isAuthenticated: status === 'authenticated',
    login,
    logout,
  };
}
