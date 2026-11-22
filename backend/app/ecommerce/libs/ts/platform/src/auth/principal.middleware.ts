import { Injectable, Logger, type NestMiddleware } from "@nestjs/common";
import type { RequestWithPrincipal } from "./decorators.js";
import { bearerToken, JwtVerifier } from "./jwt-verifier.js";

@Injectable()
export class PrincipalMiddleware implements NestMiddleware {
  private readonly log = new Logger(PrincipalMiddleware.name);

  constructor(private readonly verifier: JwtVerifier) {}

  async use(req: RequestWithPrincipal & { authError?: string }, _res: unknown, next: (err?: unknown) => void) {
    const token = bearerToken(req.headers.authorization);
    if (token) {
      try {
        req.principal = await this.verifier.verify(token);
      } catch (e) {
        req.authError = (e as Error).message;
        this.log.debug(`rejected token: ${req.authError}`);
      }
    }
    next();
  }
}
