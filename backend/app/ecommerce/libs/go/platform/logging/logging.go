// Package logging dựng slog logger dạng JSON (prod) hoặc text (dev), tự gắn
// trace_id/span_id của OpenTelemetry để Loki ↔ Tempo nhảy qua lại được.
package logging

import (
	"context"
	"log/slog"
	"os"
	"strings"

	"go.opentelemetry.io/otel/trace"
)

func New(service, format, level string) *slog.Logger {
	var lv slog.Level
	_ = lv.UnmarshalText([]byte(strings.ToUpper(level)))
	opts := &slog.HandlerOptions{Level: lv}
	var h slog.Handler
	if strings.EqualFold(format, "text") {
		h = slog.NewTextHandler(os.Stdout, opts)
	} else {
		h = slog.NewJSONHandler(os.Stdout, opts)
	}
	l := slog.New(traceHandler{h}).With("service", service)
	slog.SetDefault(l)
	return l
}

type traceHandler struct{ slog.Handler }

func (h traceHandler) Handle(ctx context.Context, r slog.Record) error {
	if sc := trace.SpanContextFromContext(ctx); sc.IsValid() {
		r.AddAttrs(slog.String("trace_id", sc.TraceID().String()), slog.String("span_id", sc.SpanID().String()))
	}
	return h.Handler.Handle(ctx, r)
}

func (h traceHandler) WithAttrs(a []slog.Attr) slog.Handler {
	return traceHandler{h.Handler.WithAttrs(a)}
}
func (h traceHandler) WithGroup(n string) slog.Handler { return traceHandler{h.Handler.WithGroup(n)} }
