import { type CanActivate, type ExecutionContext, Injectable, Logger } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { DomainError } from '../errors/domain-error.js';
import { IS_PUBLIC, ROLES, requestOf } from './decorators.js';
import { bearerToken, JwtVerifier } from './jwt-verifier.js';
import { hasAnyRole } from './principal.js';

/**
 * Guard toàn cục: mặc định mọi route/resolver cần token hợp lệ; @Public() mở; @Roles() giới hạn role.
 * Gateway (APISIX) và router GraphQL cũng kiểm tra token, nhưng service vẫn tự kiểm tra (zero trust).
 */
@Injectable()
export class AuthGuard implements CanActivate {
  private readonly log = new Logger(AuthGuard.name);

  constructor(
    private readonly reflector: Reflector,
    private readonly verifier: JwtVerifier,
  ) {}

  async canActivate(ctx: ExecutionContext): Promise<boolean> {
    const targets = [ctx.getHandler(), ctx.getClass()];
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC, targets) ?? false;
    const roles = this.reflector.getAllAndOverride<string[]>(ROLES, targets) ?? [];
    const req = requestOf(ctx);
    if (!req) {
      // Không phải http/graphql (ví dụ field resolver của entity reference): không chặn.
      return true;
    }

    // Bình thường PrincipalMiddleware đã xác thực; nhánh này cho ngữ cảnh không qua middleware (test, WS).
    if (!req.principal && !(req as { authError?: string }).authError) {
      const token = bearerToken(req.headers.authorization);
      if (token) {
        try {
          req.principal = await this.verifier.verify(token);
        } catch (e) {
          (req as { authError?: string }).authError = (e as Error).message;
          this.log.debug(`rejected token: ${(e as Error).message}`);
        }
      }
    }
    if (!isPublic && !req.principal && (req as { authError?: string }).authError) {
      throw DomainError.unauthenticated('invalid or expired token');
    }

    if (isPublic) return true;
    if (!req.principal) throw DomainError.unauthenticated('authentication required');
    if (roles.length > 0 && !hasAnyRole(req.principal, roles)) {
      throw DomainError.forbidden(`requires one of roles: ${roles.join(', ')}`);
    }
    return true;
  }
}
