import { type DynamicModule, Module } from '@nestjs/common';
import { trace } from '@opentelemetry/api';
import { LoggerModule } from 'nestjs-pino';

export interface LoggingOptions {
  serviceName: string;
  level: string;
  format: 'json' | 'pretty';
}

/** Log JSON (pino) kèm trace_id/span_id để Loki nối sang Tempo; bỏ log request của health check. */
@Module({})
export class PlatformLoggerModule {
  static forRoot(opts: LoggingOptions): DynamicModule {
    return {
      module: PlatformLoggerModule,
      imports: [
        LoggerModule.forRoot({
          pinoHttp: {
            level: opts.level,
            base: { service: opts.serviceName },
            messageKey: 'message',
            timestamp: () => `,"@timestamp":"${new Date().toISOString()}"`,
            formatters: { level: (label) => ({ level: label }) },
            mixin() {
              const ctx = trace.getActiveSpan()?.spanContext();
              return ctx ? { trace_id: ctx.traceId, span_id: ctx.spanId } : {};
            },
            redact: ['req.headers.authorization', 'req.headers.cookie', 'req.headers["idempotency-key"]'],
            autoLogging: { ignore: (req) => req.url?.startsWith('/health') ?? false },
            ...(opts.format === 'pretty' ? { transport: { target: 'pino-pretty' } } : {}),
          },
        }),
      ],
      exports: [LoggerModule],
    };
  }
}
