/**
 * Khởi động OpenTelemetry trước khi nạp app: `node --import @ecom/platform/telemetry dist/main.js`.
 * Trace và metric xuất OTLP/HTTP tới collector (OTEL_EXPORTER_OTLP_ENDPOINT, mặc định http://localhost:4318).
 * Tắt hẳn bằng OTEL_SDK_DISABLED=true (test, chạy local không có collector).
 */
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-proto';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-proto';
import { PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';
import { NodeSDK } from '@opentelemetry/sdk-node';

if (process.env.OTEL_SDK_DISABLED !== 'true') {
  const sdk = new NodeSDK({
    serviceName: process.env.OTEL_SERVICE_NAME ?? process.env.SERVICE_NAME ?? 'unknown-service',
    traceExporter: new OTLPTraceExporter(),
    metricReaders: [
      new PeriodicExportingMetricReader({ exporter: new OTLPMetricExporter(), exportIntervalMillis: 15_000 }),
    ],
    instrumentations: [
      getNodeAutoInstrumentations({
        // Ồn và ít giá trị: mỗi lần đọc file/DNS sinh một span.
        '@opentelemetry/instrumentation-fs': { enabled: false },
        '@opentelemetry/instrumentation-dns': { enabled: false },
        '@opentelemetry/instrumentation-net': { enabled: false },
        '@opentelemetry/instrumentation-http': {
          ignoreIncomingRequestHook: (req) => req.url?.startsWith('/health') ?? false,
        },
      }),
    ],
  });
  sdk.start();
  const shutdown = () => {
    sdk.shutdown().finally(() => process.exit(0));
  };
  process.once('SIGTERM', shutdown);
  process.once('SIGINT', shutdown);
}
