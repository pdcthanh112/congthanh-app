import type { DescService } from "@bufbuild/protobuf";
import { type CallOptions, type Client, Code, ConnectError, createClient, type Interceptor } from "@connectrpc/connect";
import { createGrpcTransport } from "@connectrpc/connect-node";
import { circuitBreaker, ConsecutiveBreaker, ExponentialBackoff, handleWhen, type IPolicy, retry, timeout, TimeoutStrategy, wrap } from "cockatiel";
import { DomainError } from "../errors/domain-error.js";

export const isTransient = (e: unknown): boolean => e instanceof ConnectError && [Code.Unavailable, Code.DeadlineExceeded, Code.ResourceExhausted, Code.Aborted].includes(e.code);

export interface ResilienceOptions {
  timeoutMs?: number;
  retries?: number;
  breakerThreshold?: number;
  halfOpenAfterMs?: number;
}

export function resiliencePolicy(opts: ResilienceOptions = {}): IPolicy {
  const transient = handleWhen(isTransient);
  return wrap(
    retry(transient, {
      maxAttempts: opts.retries ?? 2,
      backoff: new ExponentialBackoff({ initialDelay: 100, maxDelay: 2_000 }),
    }),
    circuitBreaker(transient, {
      halfOpenAfter: opts.halfOpenAfterMs ?? 15_000,
      breaker: new ConsecutiveBreaker(opts.breakerThreshold ?? 5),
    }),
    timeout(opts.timeoutMs ?? 3_000, TimeoutStrategy.Aggressive),
  );
}

export interface GrpcClientOptions {
  address: string;
  interceptors?: Interceptor[];
}

export function grpcClient<T extends DescService>(service: T, opts: GrpcClientOptions): Client<T> {
  const baseUrl = opts.address.startsWith("http") ? opts.address : `http://${opts.address}`;
  return createClient(service, createGrpcTransport({ baseUrl, interceptors: opts.interceptors ?? [] }));
}

export function bearer(token: string | undefined, extra: CallOptions = {}): CallOptions {
  if (!token) return extra;
  const headers = new Headers(extra.headers);
  headers.set("authorization", `Bearer ${token}`);
  return { ...extra, headers };
}

export function fromGrpcError(e: unknown, what: string): DomainError {
  if (e instanceof DomainError) return e;
  if (e instanceof ConnectError) {
    switch (e.code) {
      case Code.NotFound:
        return DomainError.notFound(`${what}: ${e.rawMessage}`);
      case Code.InvalidArgument:
      case Code.FailedPrecondition:
      case Code.OutOfRange:
        return DomainError.invalid(`${what}: ${e.rawMessage}`);
      case Code.AlreadyExists:
      case Code.Aborted:
        return DomainError.conflict(`${what}: ${e.rawMessage}`);
      case Code.PermissionDenied:
        return DomainError.forbidden(`${what}: ${e.rawMessage}`);
      case Code.Unauthenticated:
        return DomainError.unauthenticated(`${what}: ${e.rawMessage}`);
      default:
        return DomainError.unavailable(`${what} is unavailable`);
    }
  }
  return DomainError.unavailable(`${what} is unavailable`);
}
