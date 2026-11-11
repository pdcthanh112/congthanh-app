package http

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"slices"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/data/ingestion/clickstream-collector/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/data/ingestion/clickstream-collector/internal/domain"
)

const maxBody = 512 << 10 
type Handler struct {
	svc     *app.Service
	origins []string
}

func New(svc *app.Service, allowedOrigins []string) *Handler {
	return &Handler{svc: svc, origins: allowedOrigins}
}

func (h *Handler) Mount(r chi.Router) {
	r.Group(func(r chi.Router) {
		r.Use(h.cors)
		r.Options("/v1/events", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) })
		r.Post("/v1/events", h.collect)
	})
}

type request struct {
	Events []domain.Event `json:"events"`
}

func (h *Handler) collect(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, maxBody)
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	var req request
	if err := dec.Decode(&req); err != nil {
		var tooBig *http.MaxBytesError
		if errors.As(err, &tooBig) {
			writeError(w, http.StatusRequestEntityTooLarge, "body quá 512 KB")
			return
		}
		writeError(w, http.StatusBadRequest, "JSON không hợp lệ: "+err.Error())
		return
	}
	if len(req.Events) == 0 || len(req.Events) > app.MaxBatch {
		writeError(w, http.StatusBadRequest, "events phải có từ 1 đến 100 phần tử")
		return
	}
	res, err := h.svc.Collect(r.Context(), req.Events, app.Client{UserAgent: r.UserAgent(), IP: clientIP(r)})
	if err != nil {
		slog.ErrorContext(r.Context(), "publish clickstream", "err", err)
		writeError(w, http.StatusServiceUnavailable, "tạm thời không ghi được event, thử lại sau")
		return
	}
	writeJSON(w, http.StatusAccepted, res)
}

func clientIP(r *http.Request) string {
	if host, _, err := net.SplitHostPort(r.RemoteAddr); err == nil {
		return host
	}
	return r.RemoteAddr
}

func (h *Handler) cors(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if o := r.Header.Get("Origin"); o != "" && (slices.Contains(h.origins, "*") || slices.Contains(h.origins, o)) {
			w.Header().Set("Access-Control-Allow-Origin", o)
			w.Header().Set("Vary", "Origin")
			w.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
			w.Header().Set("Access-Control-Max-Age", "600")
		}
		next.ServeHTTP(w, r)
	})
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}
