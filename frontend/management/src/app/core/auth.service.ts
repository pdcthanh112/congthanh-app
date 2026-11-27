import { Injectable, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { APP_CONFIG } from './config';

/** Số giây tối thiểu token còn hạn trước mỗi lời gọi API; ít hơn thì làm mới trước. */
const MIN_TOKEN_VALIDITY_SECONDS = 30;

/**
 * Đăng nhập Keycloak (Authorization Code + PKCE, client công khai `backoffice-web`). Token chỉ nằm trong bộ nhớ của
 * keycloak-js, không ghi localStorage. Chưa đăng nhập thì chuyển sang trang đăng nhập Keycloak ngay khi mở app.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = new Keycloak({ ...APP_CONFIG.keycloak });
  readonly username = signal<string | null>(null);
  readonly isAdmin = signal(false);

  async init(): Promise<void> {
    await this.keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false });
    this.username.set((this.keycloak.tokenParsed?.['preferred_username'] as string | undefined) ?? null);
    this.isAdmin.set(this.roles().includes('ADMIN'));
  }

  /** Access token còn hạn (tự làm mới khi sắp hết). */
  async token(): Promise<string | undefined> {
    try {
      await this.keycloak.updateToken(MIN_TOKEN_VALIDITY_SECONDS);
    } catch {
      // Refresh token cũng hết hạn: đăng nhập lại.
      await this.keycloak.login();
    }
    return this.keycloak.token;
  }

  logout(): Promise<void> {
    return this.keycloak.logout({ redirectUri: window.location.origin });
  }

  /** Role realm và role gán qua client scope roles-claim, viết hoa như backend so khớp. */
  private roles(): string[] {
    const parsed = this.keycloak.tokenParsed as { realm_access?: { roles?: string[] }; roles?: string[] } | undefined;
    const roles = [...(parsed?.realm_access?.roles ?? []), ...(parsed?.roles ?? [])];
    return roles.map((r) => r.toUpperCase());
  }
}
