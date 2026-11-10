import { type DynamicModule, Global, type MiddlewareConsumer, Module, type NestModule } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { AuthGuard } from './auth.guard.js';
import { JWT_OPTIONS, type JwtOptions, JwtVerifier } from './jwt-verifier.js';
import { PrincipalMiddleware } from './principal.middleware.js';

@Global()
@Module({})
export class PlatformAuthModule implements NestModule {
  static forRoot(opts: JwtOptions): DynamicModule {
    return {
      module: PlatformAuthModule,
      providers: [
        { provide: JWT_OPTIONS, useValue: opts },
        JwtVerifier,
        PrincipalMiddleware,
        { provide: APP_GUARD, useClass: AuthGuard },
      ],
      exports: [JwtVerifier],
    };
  }

  configure(consumer: MiddlewareConsumer) {
    consumer.apply(PrincipalMiddleware).forRoutes('*path');
  }
}
