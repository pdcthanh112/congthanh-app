import type { ProblemDetails } from '@ecommerce/shared';
import { type ArgumentsHost, Catch, type ExceptionFilter, HttpException, Logger } from '@nestjs/common';
import type { Response } from 'express';
import { DomainError, HTTP_STATUS } from './domain-error.js';

/** HTTP: mọi lỗi trả về RFC 9457 application/problem+json. GraphQL tự xử lý qua formatError. */
@Catch()
export class ProblemDetailsFilter implements ExceptionFilter {
  private readonly log = new Logger(ProblemDetailsFilter.name);

  catch(error: unknown, host: ArgumentsHost): void {
    if (host.getType() !== 'http') {
      throw error;
    }
    const res = host.switchToHttp().getResponse<Response>();
    let problem: ProblemDetails;
    if (error instanceof DomainError) {
      problem = { title: error.kind, status: HTTP_STATUS[error.kind], detail: error.message, code: error.code };
    } else if (error instanceof HttpException) {
      problem = { title: error.name, status: error.getStatus(), detail: error.message };
    } else {
      this.log.error(error);
      problem = { title: 'INTERNAL', status: 500, detail: 'unexpected error' };
    }
    res.status(problem.status).type('application/problem+json').json(problem);
  }
}
