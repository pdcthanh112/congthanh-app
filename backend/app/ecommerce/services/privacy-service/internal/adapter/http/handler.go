// Package http là REST của privacy-service: /api/ecommerce/privacy.
package http

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/app"
)

type H = apperr.Handler

// staffRoles: nhóm xử lý quyền riêng tư (PRIVACY) và quản trị.
var staffRoles = []string{"ADMIN", "PRIVACY"}

type Handler struct {
	svc      *app.Service
	verifier *auth.Verifier
}

func New(svc *app.Service, v *auth.Verifier) *Handler { return &Handler{svc: svc, verifier: v} }

func (h *Handler) Mount(r chi.Router) {
	r.Route("/api/ecommerce/privacy", func(r chi.Router) {
		r.Use(auth.Middleware(h.verifier))
		r.Group(func(r chi.Router) {
			r.Use(auth.RequireAuth(h.verifier))
			r.Method(http.MethodGet, "/consents", H(h.consents))
			r.Method(http.MethodPut, "/consents/{purpose}", H(h.setConsent))
			r.Method(http.MethodPost, "/requests", H(h.openRequest))
			r.Method(http.MethodGet, "/requests", H(h.myRequests))
			r.Method(http.MethodGet, "/requests/{id}", H(h.getRequest))
		})
		r.Group(func(r chi.Router) {
			r.Use(auth.RequireRole(h.verifier, staffRoles...))
			r.Method(http.MethodGet, "/admin/requests", H(h.byStatus))
			r.Method(http.MethodPost, "/admin/requests/{id}/extend", H(h.extend))
			r.Method(http.MethodPost, "/admin/requests/{id}/tasks/{processor}/complete", H(h.completeTask))
		})
	})
}

func principal(r *http.Request) auth.Principal {
	p, _ := auth.FromContext(r.Context())
	return p
}

func (h *Handler) consents(w http.ResponseWriter, r *http.Request) error {
	gpc := r.Header.Get("Sec-GPC") == "1"
	cs, err := h.svc.Consents(r.Context(), principal(r).Subject, gpc)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get consents successfully", cs)
	return nil
}

type consentBody struct {
	Granted *bool  `json:"granted"`
	Source  string `json:"source"`
}

func (h *Handler) setConsent(w http.ResponseWriter, r *http.Request) error {
	var b consentBody
	if err := httpx.Decode(r, &b); err != nil {
		return apperr.Invalid("invalid body: %v", err)
	}
	if b.Granted == nil {
		return apperr.Invalid("granted is required")
	}
	c, err := h.svc.SetConsent(r.Context(), principal(r).Subject, chi.URLParam(r, "purpose"), *b.Granted, b.Source)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Consent recorded", c)
	return nil
}

type requestBody struct {
	Type  string `json:"type"`
	Email string `json:"email"`
}

func (h *Handler) openRequest(w http.ResponseWriter, r *http.Request) error {
	var b requestBody
	if err := httpx.Decode(r, &b); err != nil {
		return apperr.Invalid("invalid body: %v", err)
	}
	req, err := h.svc.OpenRequest(r.Context(), principal(r).Subject, b.Type, b.Email)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Privacy request opened", req)
	return nil
}

func (h *Handler) myRequests(w http.ResponseWriter, r *http.Request) error {
	rs, err := h.svc.MyRequests(r.Context(), principal(r).Subject)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "List privacy requests successfully", rs)
	return nil
}

func (h *Handler) getRequest(w http.ResponseWriter, r *http.Request) error {
	p := principal(r)
	req, err := h.svc.GetRequest(r.Context(), chi.URLParam(r, "id"), p.Subject, p.HasAnyRole(staffRoles...))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get privacy request successfully", req)
	return nil
}

func (h *Handler) byStatus(w http.ResponseWriter, r *http.Request) error {
	status := r.URL.Query().Get("status")
	if status == "" {
		status = "IN_PROGRESS"
	}
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit == 0 {
		limit = 100
	}
	rs, err := h.svc.RequestsByStatus(r.Context(), status, limit)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "List privacy requests successfully", rs)
	return nil
}

type reasonBody struct {
	Reason string `json:"reason"`
}

func (h *Handler) extend(w http.ResponseWriter, r *http.Request) error {
	var b reasonBody
	if err := httpx.Decode(r, &b); err != nil {
		return apperr.Invalid("invalid body: %v", err)
	}
	req, err := h.svc.Extend(r.Context(), chi.URLParam(r, "id"), b.Reason)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Privacy request extended", req)
	return nil
}

type completeBody struct {
	Retained bool   `json:"retained"`
	Reason   string `json:"reason"`
}

func (h *Handler) completeTask(w http.ResponseWriter, r *http.Request) error {
	var b completeBody
	if err := httpx.Decode(r, &b); err != nil {
		return apperr.Invalid("invalid body: %v", err)
	}
	if err := h.svc.CompleteTaskManually(r.Context(), chi.URLParam(r, "id"), chi.URLParam(r, "processor"), b.Retained, b.Reason); err != nil {
		return err
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}
