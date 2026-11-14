'use client';

import { useEffect } from 'react';
import { signIn, useSession } from 'next-auth/react';

/**
 * Refresh token hết hạn hoặc bị thu hồi (session.error = RefreshAccessTokenError): đưa người dùng đăng nhập lại
 * qua Keycloak, quay về đúng trang đang xem, thay vì để mọi request 401 âm thầm.
 */
export default function SessionGuard() {
  const { data: session } = useSession();

  useEffect(() => {
    if (session?.error === 'RefreshAccessTokenError') {
      signIn('keycloak', { callbackUrl: window.location.href });
    }
  }, [session?.error]);

  return null;
}
