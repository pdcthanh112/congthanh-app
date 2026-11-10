import { Inject, Injectable } from '@nestjs/common';
import { createRemoteJWKSet, type JWTPayload, jwtVerify } from 'jose';
import type { Principal } from './principal.js';

export interface JwtOptions {
  issuer: string;
  jwksUrl: string;
  audience?: string | undefined;
}
export const JWT_OPTIONS = Symbol('JWT_OPTIONS');

interface KeycloakClaims extends JWTPayload {
  preferred_username?: string;
  email?: string;
  name?: string;
  realm_access?: { roles?: string[] };
}

/** Kiểm tra chữ ký (JWKS của Keycloak, có cache + tự xoay khoá), issuer, hạn dùng, audience. */
@Injectable()
export class JwtVerifier {
  private readonly jwks: ReturnType<typeof createRemoteJWKSet>;

  constructor(@Inject(JWT_OPTIONS) private readonly opts: JwtOptions) {
    this.jwks = createRemoteJWKSet(new URL(opts.jwksUrl), { cooldownDuration: 30_000, cacheMaxAge: 600_000 });
  }

  async verify(token: string): Promise<Principal> {
    const { payload } = await jwtVerify<KeycloakClaims>(token, this.jwks, {
      issuer: this.opts.issuer,
      ...(this.opts.audience ? { audience: this.opts.audience } : {}),
      clockTolerance: 5,
    });
    if (!payload.sub) {
      throw new Error('token has no subject');
    }
    return {
      id: payload.sub,
      ...(payload.preferred_username ? { username: payload.preferred_username } : {}),
      ...(payload.email ? { email: payload.email } : {}),
      ...(payload.name ? { name: payload.name } : {}),
      roles: new Set(payload.realm_access?.roles ?? []),
      token,
    };
  }
}

export function bearerToken(header: string | string[] | undefined): string | undefined {
  const value = Array.isArray(header) ? header[0] : header;
  if (!value) return undefined;
  const [scheme, token] = value.split(' ');
  return scheme?.toLowerCase() === 'bearer' && token ? token : undefined;
}
