import { useSession, signIn, signOut } from 'next-auth/react';

export function useAuthenticated() {
  const { data: session, status } = useSession();

  return {
    user: session?.user,
    isAuthenticated: status === 'authenticated',
    login: (provider: any) => signIn(provider),
    logout: () => signOut(),
  };
}
