/** Lỗi nghiệp vụ dùng chung; adapter HTTP/GraphQL/gRPC dịch sang mã của giao thức tương ứng. */
export type DomainErrorKind = 'INVALID' | 'NOT_FOUND' | 'CONFLICT' | 'FORBIDDEN' | 'UNAUTHENTICATED' | 'UNAVAILABLE';

export class DomainError extends Error {
  constructor(
    readonly kind: DomainErrorKind,
    message: string,
    /** Mã ổn định cho client (ví dụ REVIEW_ALREADY_EXISTS); mặc định trùng kind. */
    readonly code: string = kind,
  ) {
    super(message);
    this.name = 'DomainError';
  }

  static invalid(message: string, code?: string) {
    return new DomainError('INVALID', message, code);
  }
  static notFound(message: string, code?: string) {
    return new DomainError('NOT_FOUND', message, code);
  }
  static conflict(message: string, code?: string) {
    return new DomainError('CONFLICT', message, code);
  }
  static forbidden(message: string, code?: string) {
    return new DomainError('FORBIDDEN', message, code);
  }
  static unauthenticated(message: string) {
    return new DomainError('UNAUTHENTICATED', message);
  }
  static unavailable(message: string, code?: string) {
    return new DomainError('UNAVAILABLE', message, code);
  }
}

export const HTTP_STATUS: Record<DomainErrorKind, number> = {
  INVALID: 400,
  UNAUTHENTICATED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  UNAVAILABLE: 503,
};

/** Mã lỗi GraphQL (extensions.code) theo quy ước Apollo. */
export const GRAPHQL_CODE: Record<DomainErrorKind, string> = {
  INVALID: 'BAD_USER_INPUT',
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  FORBIDDEN: 'FORBIDDEN',
  NOT_FOUND: 'NOT_FOUND',
  CONFLICT: 'CONFLICT',
  UNAVAILABLE: 'SERVICE_UNAVAILABLE',
};
