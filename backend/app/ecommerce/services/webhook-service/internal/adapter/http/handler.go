// Package http: API quản trị webhook dưới /api/ecommerce/webhooks (chỉ ADMIN).
package http

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/domain"
)

type H = apperr.Handler

type Handler struct {
	svc      *app.Service
	verifier *auth.Verifier
}

func New(svc *app.Service, v *auth.Verifier) *Handler { return &Handler{svc: svc, verifier: v} }

func (h *Handler) Mount(r chi.Router) {
	r.Route("/api/ecommerce/webhooks", func(r chi.Router) {
		r.Use(auth.Middleware(h.verifier), auth.RequireAuth(h.verifier), h.requireAdmin)
		r.Method(http.MethodGet, "/events", H(h.events))
		r.Method(http.MethodGet, "/", H(h.list))
		r.Method(http.MethodPost, "/", H(h.create))
		r.Method(http.MethodGet, "/{id}", H(h.get))
		r.Method(http.MethodPut, "/{id}", H(h.update))
		r.Method(http.MethodDelete, "/{id}", H(h.delete))
		r.Method(http.MethodPost, "/{id}/ping", H(h.ping))
		r.Method(http.MethodGet, "/{id}/deliveries", H(h.deliveries))
		r.Method(http.MethodPost, "/deliveries/{deliveryId}/retry", H(h.retry))
	})
}

func (h *Handler) requireAdmin(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if h.verifier != nil {
			if p, _ := auth.FromContext(r.Context()); !p.HasRole("ADMIN") {
				httpx.Fail(w, r, httpx.Forbidden("ADMIN role required"))
				return
			}
		}
		next.ServeHTTP(w, r)
	})
}

func (h *Handler) events(w http.ResponseWriter, _ *http.Request) error {
	httpx.OK(w, http.StatusOK, "Get events successfully", domain.AllEvents)
	return nil
}

func (h *Handler) list(w http.ResponseWriter, r *http.Request) error {
	list, err := h.svc.List(r.Context())
	if err != nil {
		return err
	}
	if list == nil {
		list = []domain.Webhook{}
	}
	httpx.OK(w, http.StatusOK, "Get webhooks successfully", list)
	return nil
}

type body struct {
	Name   string   `json:"name"`
	URL    string   `json:"url"`
	Secret string   `json:"secret"`
	Events []string `json:"events"`
	Active *bool    `json:"active"`
}

func (b body) toDomain(id string) domain.Webhook {
	active := true
	if b.Active != nil {
		active = *b.Active
	}
	return domain.Webhook{ID: id, Name: b.Name, URL: b.URL, Secret: b.Secret, Events: b.Events, Active: active}
}

func (h *Handler) create(w http.ResponseWriter, r *http.Request) error {
	var b body
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	hook, err := h.svc.Create(r.Context(), b.toDomain(""))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Webhook created. Store the secret now: it will not be shown again", hook)
	return nil
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) error {
	hook, err := h.svc.Get(r.Context(), chi.URLParam(r, "id"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get webhook successfully", hook)
	return nil
}

func (h *Handler) update(w http.ResponseWriter, r *http.Request) error {
	var b body
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	hook, err := h.svc.Update(r.Context(), b.toDomain(chi.URLParam(r, "id")))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Update webhook successfully", hook)
	return nil
}

func (h *Handler) delete(w http.ResponseWriter, r *http.Request) error {
	if err := h.svc.Delete(r.Context(), chi.URLParam(r, "id")); err != nil {
		return err
	}
	httpx.OK[any](w, http.StatusOK, "Delete webhook successfully", nil)
	return nil
}

func (h *Handler) ping(w http.ResponseWriter, r *http.Request) error {
	d, err := h.svc.Ping(r.Context(), chi.URLParam(r, "id"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusAccepted, "Ping queued", d)
	return nil
}

func (h *Handler) deliveries(w http.ResponseWriter, r *http.Request) error {
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	list, err := h.svc.Deliveries(r.Context(), chi.URLParam(r, "id"), limit)
	if err != nil {
		return err
	}
	if list == nil {
		list = []domain.Delivery{}
	}
	httpx.OK(w, http.StatusOK, "Get deliveries successfully", list)
	return nil
}

func (h *Handler) retry(w http.ResponseWriter, r *http.Request) error {
	if err := h.svc.Retry(r.Context(), chi.URLParam(r, "deliveryId")); err != nil {
		return err
	}
	httpx.OK[any](w, http.StatusAccepted, "Retry queued", nil)
	return nil
}
