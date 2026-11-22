import { Code, ConnectError, type ConnectRouter, type Interceptor } from "@connectrpc/connect";
import { connectNodeAdapter } from "@connectrpc/connect-node";
import { Logger, type OnApplicationBootstrap, type OnApplicationShutdown } from "@nestjs/common";
import { createServer, type Http2Server } from "node:http2";
import { DomainError, type DomainErrorKind } from "../errors/domain-error.js";

const GRPC_CODE: Record<DomainErrorKind, Code> = {
  INVALID: Code.InvalidArgument,
  NOT_FOUND: Code.NotFound,
  CONFLICT: Code.AlreadyExists,
  FORBIDDEN: Code.PermissionDenied,
  UNAUTHENTICATED: Code.Unauthenticated,
  UNAVAILABLE: Code.Unavailable,
};

export const domainErrorInterceptor: Interceptor = (next) => async (req) => {
  try {
    return await next(req);
  } catch (e) {
    if (e instanceof ConnectError) throw e;
    if (e instanceof DomainError) throw new ConnectError(e.message, GRPC_CODE[e.kind]);
    new Logger("GrpcServer").error(e);
    throw new ConnectError("internal error", Code.Internal);
  }
};

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
