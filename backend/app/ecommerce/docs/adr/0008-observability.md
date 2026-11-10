# 0008. Observability

- Trạng thái: Accepted

Mọi service xuất trace/metric/log qua OTLP tới OpenTelemetry Collector. Collector sinh RED metrics từ span (spanmetrics) và service graph, tail-sampling giữ 100% trace lỗi/chậm. Lưu trữ: Tempo (trace), Mimir (metric), Loki (log), Pyroscope (profile, thu bằng Alloy eBPF). Log có trace_id/span_id để nhảy qua lại. SLO định nghĩa trong `platform/observability/prometheus/slo-rules.yaml`, cảnh báo multi-window burn-rate; Argo Rollouts dùng cùng metric để tự rollback canary.
