package server

import (
	"context"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

// NewRouter tạo chi router với middleware chuẩn và các endpoint vận hành:
// /health/live, /health/ready (gọi ready), /metrics.
func NewRouter(service string, ready func(ctx context.Context) error) chi.Router {
	r := chi.NewRouter()
	r.Use(middleware.RequestID, middleware.RealIP, otelhttp.NewMiddleware(service), accessLog, middleware.Recoverer)
	r.Get("/health/live", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusOK) })
	r.Get("/health/ready", func(w http.ResponseWriter, req *http.Request) {
		ctx, cancel := context.WithTimeout(req.Context(), 2*time.Second)
		defer cancel()
		if ready != nil {
			if err := ready(ctx); err != nil {
				http.Error(w, err.Error(), http.StatusServiceUnavailable)
				return
			}
		}
		w.WriteHeader(http.StatusOK)
	})
	r.Handle("/metrics", promhttp.Handler())
	return r
}

func accessLog(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
		next.ServeHTTP(ww, r)
		if r.URL.Path == "/health/live" || r.URL.Path == "/health/ready" || r.URL.Path == "/metrics" {
			return
		}
		slog.InfoContext(r.Context(), "http",
			"method", r.Method, "path", r.URL.Path, "status", ww.Status(),
			"bytes", ww.BytesWritten(), "duration", time.Since(start),
			"request_id", middleware.GetReqID(r.Context()))
	})
}
