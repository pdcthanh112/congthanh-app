import {
  ConsecutiveBreaker,
  circuitBreaker,
  ExponentialBackoff,
  handleWhen,
  type IPolicy,
  retry,
  TimeoutStrategy,
  timeout,
  wrap,
} from 'cockatiel';
import { z } from 'zod';
import { DomainError } from '../errors/domain-error.js';

/** Lỗi HTTP tạm thời (đáng thử lại): 502/503/504, 429, lỗi mạng. */
class TransientHttpError extends Error {}

export interface RestClientOptions {
  baseUrl: string;
  /** Tên service đích để ghi vào thông báo lỗi. */
  name: string;
  timeoutMs?: number;
  retries?: number;
}

export interface RestCall {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  query?: Record<string, string | number | boolean | undefined>;
  body?: unknown;
  /** Token người dùng chuyển tiếp xuống (token relay). */
  token?: string | undefined;
  headers?: Record<string, string>;
  /** Chỉ thử lại cho lệnh an toàn (GET) hoặc có Idempotency-Key. */
  idempotent?: boolean;
}

/**
 * Client REST tới service Java/Go: timeout, retry (chỉ khi idempotent), circuit breaker, đổi lỗi HTTP
 * (gồm Problem Details RFC 9457 và envelope {data,status,message} cũ) sang DomainError, kiểm tra kiểu
 * dữ liệu trả về bằng zod (sai hợp đồng thì báo lỗi rõ thay vì lỗi undefined ở resolver).
 */
export class RestClient {
  private readonly safe: IPolicy;
  private readonly unsafe: IPolicy;

  constructor(private readonly opts: RestClientOptions) {
    const transient = handleWhen((e) => e instanceof TransientHttpError);
    const breaker = circuitBreaker(transient, { halfOpenAfter: 15_000, breaker: new ConsecutiveBreaker(5) });
    const t = timeout(opts.timeoutMs ?? 5_000, TimeoutStrategy.Aggressive);
    this.safe = wrap(
      retry(transient, {
        maxAttempts: opts.retries ?? 2,
        backoff: new ExponentialBackoff({ initialDelay: 100, maxDelay: 1_000 }),
      }),
      breaker,
      t,
    );
    this.unsafe = wrap(breaker, t);
  }

  async call<S extends z.ZodType>(path: string, schema: S, c: RestCall = {}): Promise<z.infer<S>> {
    const method = c.method ?? 'GET';
    const policy = (c.idempotent ?? method === 'GET') ? this.safe : this.unsafe;
    let raw: unknown;
    try {
      raw = await policy.execute(({ signal }) => this.send(path, method, c, signal));
    } catch (e) {
      // Hết lượt retry, timeout hoặc circuit đang mở: báo UNAVAILABLE thay vì lỗi 500 chung chung.
      if (e instanceof DomainError) throw e;
      throw DomainError.unavailable(`${this.opts.name} unavailable: ${(e as Error).message}`);
    }
    const parsed = schema.safeParse(raw);
    if (!parsed.success) {
      throw DomainError.unavailable(
        `${this.opts.name} returned an unexpected payload: ${parsed.error.issues[0]?.message}`,
      );
    }
    return parsed.data;
  }

  /** Như call nhưng bóc envelope cũ `{ data, status, message }` của service Spring/Go. */
  async data<S extends z.ZodType>(path: string, schema: S, c: RestCall = {}): Promise<z.infer<S>> {
    const env = (await this.call(path, z.object({ data: schema }), c)) as { data: z.infer<S> };
    return env.data;
  }

  private async send(path: string, method: string, c: RestCall, signal: AbortSignal): Promise<unknown> {
    const url = new URL(path, this.opts.baseUrl);
    for (const [k, v] of Object.entries(c.query ?? {})) if (v !== undefined) url.searchParams.set(k, String(v));
    const headers: Record<string, string> = { accept: 'application/json', ...c.headers };
    if (c.token) headers.authorization = `Bearer ${c.token}`;
    if (c.body !== undefined) headers['content-type'] = 'application/json';
    let res: Response;
    try {
      res = await fetch(url, {
        method,
        headers,
        body: c.body === undefined ? undefined : JSON.stringify(c.body),
        signal,
      });
    } catch (e) {
      if (signal.aborted) throw e;
      throw new TransientHttpError(`${this.opts.name} unreachable: ${(e as Error).message}`);
    }
    if ([429, 502, 503, 504].includes(res.status)) throw new TransientHttpError(`${this.opts.name} ${res.status}`);
    const text = await res.text();
    const body: unknown = text ? safeJson(text) : undefined;
    if (res.ok) return body;
    throw toDomainError(res.status, body, this.opts.name);
  }
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function toDomainError(status: number, body: unknown, name: string): DomainError {
  const b = (typeof body === 'object' && body) || {};
  const detail = String(
    (b as { detail?: unknown }).detail ?? (b as { message?: unknown }).message ?? `${name} ${status}`,
  );
  const code = (b as { code?: unknown }).code;
  const reason = typeof code === 'string' ? code : undefined;
  switch (status) {
    case 400:
    case 422:
      return DomainError.invalid(detail, reason);
    case 401:
      return DomainError.unauthenticated(detail);
    case 403:
      return DomainError.forbidden(detail, reason);
    case 404:
      return DomainError.notFound(detail, reason);
    case 409:
      return DomainError.conflict(detail, reason);
    default:
      return DomainError.unavailable(`${name} ${status}: ${detail}`);
  }
}
