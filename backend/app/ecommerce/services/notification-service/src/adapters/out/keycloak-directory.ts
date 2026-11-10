import { DomainError, resiliencePolicy } from '@ecom/platform';
import { Logger } from '@nestjs/common';
import type { Recipient, RecipientDirectory } from '../../application/ports.js';

export interface KeycloakAdminOptions {
  /** http://keycloak:8080 */
  baseUrl: string;
  realm: string;
  /** Client confidential có service account với role realm-management/view-users. */
  clientId: string;
  clientSecret: string;
}

interface KcUser {
  id: string;
  email?: string;
  emailVerified?: boolean;
  firstName?: string;
  lastName?: string;
  enabled: boolean;
  attributes?: Record<string, string[]>;
}

/** Tra email/số điện thoại qua Keycloak Admin REST API (client credentials), cache 5 phút. */
export class KeycloakRecipientDirectory implements RecipientDirectory {
  private readonly log = new Logger(KeycloakRecipientDirectory.name);
  private token?: { value: string; expiresAt: number };
  private readonly cache = new Map<string, { value: Recipient | undefined; at: number }>();
  private readonly policy = resiliencePolicy({ timeoutMs: 3_000 });

  constructor(private readonly opts: KeycloakAdminOptions) {}

  async find(userId: string): Promise<Recipient | undefined> {
    const hit = this.cache.get(userId);
    if (hit && Date.now() - hit.at < 300_000) return hit.value;
    const res = await this.call(`/users/${encodeURIComponent(userId)}`);
    const value = res.status === 404 ? undefined : toRecipient((await res.json()) as KcUser);
    this.cache.set(userId, { value, at: Date.now() });
    if (this.cache.size > 10_000) this.cache.delete(this.cache.keys().next().value!);
    return value;
  }

  async withRole(role: string): Promise<string[]> {
    const res = await this.call(`/roles/${encodeURIComponent(role)}/users?max=500`);
    if (res.status === 404) return [];
    return ((await res.json()) as KcUser[]).filter((u) => u.enabled).map((u) => u.id);
  }

  private async call(path: string): Promise<Response> {
    return this.policy.execute(async ({ signal }) => {
      const res = await fetch(`${this.opts.baseUrl}/admin/realms/${this.opts.realm}${path}`, {
        headers: { authorization: `Bearer ${await this.accessToken()}` },
        signal,
      });
      if (res.status === 401) this.token = undefined;
      if (!res.ok && res.status !== 404) throw DomainError.unavailable(`keycloak admin ${res.status}`);
      return res;
    });
  }

  private async accessToken(): Promise<string> {
    if (this.token && this.token.expiresAt > Date.now() + 10_000) return this.token.value;
    const res = await fetch(`${this.opts.baseUrl}/realms/${this.opts.realm}/protocol/openid-connect/token`, {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'client_credentials',
        client_id: this.opts.clientId,
        client_secret: this.opts.clientSecret,
      }),
    });
    if (!res.ok) {
      this.log.error(`keycloak token request failed: ${res.status}`);
      throw DomainError.unavailable('cannot authenticate to keycloak');
    }
    const body = (await res.json()) as { access_token: string; expires_in: number };
    this.token = { value: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
    return this.token.value;
  }
}

function toRecipient(u: KcUser): Recipient {
  const phone = u.attributes?.phoneNumber?.[0] ?? u.attributes?.phone?.[0];
  const name = [u.lastName, u.firstName].filter(Boolean).join(' ');
  return {
    id: u.id,
    enabled: u.enabled,
    ...(u.email && u.emailVerified !== false ? { email: u.email } : {}),
    ...(phone ? { phone } : {}),
    ...(name ? { name } : {}),
  };
}
