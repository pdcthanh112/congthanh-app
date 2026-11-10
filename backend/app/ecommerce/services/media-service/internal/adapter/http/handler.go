// Package http là driving adapter REST của media-service dưới /api/ecommerce/media (cần đăng nhập,
// trừ GET /{id} để hiển thị file đã READY).
package http

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/domain"
)

type H = apperr.Handler

type Handler struct {
	svc      *app.Service
	verifier *auth.Verifier
}

func New(svc *app.Service, v *auth.Verifier) *Handler { return &Handler{svc: svc, verifier: v} }

func (h *Handler) Mount(r chi.Router) {
	r.Route("/api/ecommerce/media", func(r chi.Router) {
		r.Use(auth.Middleware(h.verifier))
		r.Method(http.MethodGet, "/{id}", H(h.get))
		r.Group(func(r chi.Router) {
			r.Use(auth.RequireAuth(h.verifier))
			r.Method(http.MethodPost, "/uploads", H(h.requestUpload))
			r.Method(http.MethodPost, "/{id}/complete", H(h.complete))
			r.Method(http.MethodGet, "/", H(h.listMine))
			r.Method(http.MethodDelete, "/{id}", H(h.delete))
		})
	})
}

// who: khi tắt xác thực (dev) dùng header X-User-Id để thử.
func (h *Handler) who(r *http.Request) (string, bool, bool) {
	if h.verifier == nil {
		return r.Header.Get("X-User-Id"), true, true
	}
	p, _ := auth.FromContext(r.Context())
	return p.Subject, p.HasRole("ADMIN"), p.HasRole("ADMIN") || p.HasRole("SELLER")
}

type uploadBody struct {
	Purpose     domain.Purpose `json:"purpose"`
	FileName    string         `json:"fileName"`
	ContentType string         `json:"contentType"`
	SizeBytes   int64          `json:"sizeBytes"`
}

func (h *Handler) requestUpload(w http.ResponseWriter, r *http.Request) error {
	var b uploadBody
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	sub, _, seller := h.who(r)
	if sub == "" {
		return apperr.Invalid("missing user")
	}
	up, err := h.svc.RequestUpload(r.Context(), domain.UploadRequest{
		OwnerID: sub, IsSeller: seller, Purpose: b.Purpose, FileName: b.FileName, ContentType: b.ContentType, SizeBytes: b.SizeBytes,
	})
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Upload URL created", up)
	return nil
}

func (h *Handler) complete(w http.ResponseWriter, r *http.Request) error {
	sub, admin, _ := h.who(r)
	m, err := h.svc.Complete(r.Context(), chi.URLParam(r, "id"), sub, admin)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Upload completed", m)
	return nil
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) error {
	m, err := h.svc.Get(r.Context(), chi.URLParam(r, "id"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get media successfully", m)
	return nil
}

func (h *Handler) listMine(w http.ResponseWriter, r *http.Request) error {
	sub, _, _ := h.who(r)
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	list, err := h.svc.ListMine(r.Context(), sub, limit)
	if err != nil {
		return err
	}
	if list == nil {
		list = []domain.Media{}
	}
	httpx.OK(w, http.StatusOK, "Get media successfully", list)
	return nil
}

func (h *Handler) delete(w http.ResponseWriter, r *http.Request) error {
	sub, admin, _ := h.who(r)
	if err := h.svc.Delete(r.Context(), chi.URLParam(r, "id"), sub, admin); err != nil {
		return err
	}
	httpx.OK[any](w, http.StatusOK, "Delete media successfully", nil)
	return nil
}
