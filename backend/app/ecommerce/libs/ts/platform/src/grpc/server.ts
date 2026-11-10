import { createServer, type Http2Server } from 'node:http2';
import { Code, ConnectError, type ConnectRouter, type Interceptor } from '@connectrpc/connect';
import { connectNodeAdapter } from '@connectrpc/connect-node';
import { Logger, type OnApplicationBootstrap, type OnApplicationShutdown } from '@nestjs/common';
import { DomainError, type DomainErrorKind } from '../errors/domain-error.js';

const GRPC_CODE: Record<DomainErrorKind, Code> = {
  INVALID: Code.InvalidArgument,
  NOT_FOUND: Code.NotFound,
  CONFLICT: Code.AlreadyExists,
  FORBIDDEN: Code.PermissionDenied,
  UNAUTHENTICATED: Code.Unauthenticated,
  UNAVAILABLE: Code.Unavailable,
};

/** DomainError → mã gRPC chuẩn; lỗi khác → Internal (không lộ chi tiết). */
export const domainErrorInterceptor: Interceptor = (next) => async (req) => {
  try {
    return await next(req);
  } catch (e) {
    if (e instanceof ConnectError) throw e;
    if (e instanceof DomainError) throw new ConnectError(e.message, GRPC_CODE[e.kind]);
    new Logger('GrpcServer').error(e);
    throw new ConnectError('internal error', Code.Internal);
  }
};

/**
 * Server gRPC thật (HTTP/2 cleartext, giao thức gRPC) bằng Connect-ES, để service Java/Go gọi vào như mọi
 * server gRPC khác. Chạy trên cổng riêng (GRPC_PORT), tách khỏi HTTP/GraphQL.
 */
export class GrpcServer implements OnApplicationBootstrap, OnApplicationShutdown {
  private readonly log = new Logger(GrpcServer.name);
  private server?: Http2Server;

  constructor(
    private readonly port: number,
    private readonly routes: (router: ConnectRouter) => void,
  ) {}

  async onApplicationBootstrap(): Promise<void> {
    const handler = connectNodeAdapter({
      routes: this.routes,
      grpc: true,
      grpcWeb: false,
      connect: true,
      interceptors: [domainErrorInterceptor],
    });
    this.server = createServer(handler);
    await new Promise<void>((resolve) => this.server?.listen(this.port, resolve));
    this.log.log(`gRPC listening on :${this.port}`);
  }

  async onApplicationShutdown(): Promise<void> {
    await new Promise<void>((resolve) => (this.server ? this.server.close(() => resolve()) : resolve()));
  }
}
