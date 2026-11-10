// Package http là driving adapter REST của shipping-service dưới /api/ecommerce/shipping.
package http

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/domain"
)

type H = apperr.Handler

type Handler struct {
	svc      *app.Service
	verifier *auth.Verifier
}

func New(svc *app.Service, v *auth.Verifier) *Handler { return &Handler{svc: svc, verifier: v} }

func (h *Handler) Mount(r chi.Router) {
	r.Route("/api/ecommerce/shipping", func(r chi.Router) {
		r.Use(auth.Middleware(h.verifier))
		r.Method(http.MethodPost, "/quotes", H(h.quote))
		r.Method(http.MethodGet, "/track/{tracking}", H(h.track))

		r.Group(func(r chi.Router) {
			r.Use(auth.RequireAuth(h.verifier))
			r.Method(http.MethodGet, "/shipments/order/{orderId}", H(h.forOrder))
		})
		// Quản trị và hãng vận chuyển (vai trò SHIPPER) cập nhật hành trình.
		r.Group(func(r chi.Router) {
			r.Use(auth.RequireAuth(h.verifier), h.requireRole("ADMIN", "SHIPPER"))
			r.Method(http.MethodGet, "/shipments/{id}", H(h.get))
			r.Method(http.MethodPost, "/shipments/{id}/status", H(h.status))
		})
		r.Group(func(r chi.Router) {
			r.Use(auth.RequireAuth(h.verifier), h.requireRole("ADMIN"))
			r.Method(http.MethodPost, "/shipments/{id}/label", H(h.label))
			r.Method(http.MethodGet, "/rates", H(h.listRates))
			r.Method(http.MethodPost, "/rates", H(h.createRate))
			r.Method(http.MethodPut, "/rates/{id}", H(h.updateRate))
			r.Method(http.MethodDelete, "/rates/{id}", H(h.deleteRate))
		})
	})
}

func (h *Handler) requireRole(roles ...string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if h.verifier != nil {
				p, _ := auth.FromContext(r.Context())
				ok := false
				for _, role := range roles {
					ok = ok || p.HasRole(role)
				}
				if !ok {
					httpx.Fail(w, r, httpx.Forbidden("requires one of roles: "+strings.Join(roles, ", ")))
					return
				}
			}
			next.ServeHTTP(w, r)
		})
	}
}

type quoteBody struct {
	Destination   domain.Address `json:"destination"`
	WeightGrams   int            `json:"weightGrams"`
	DeclaredValue int64          `json:"declaredValueMinor"`
	Currency      string         `json:"currency"`
}

func (h *Handler) quote(w http.ResponseWriter, r *http.Request) error {
	var b quoteBody
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	q, err := h.svc.Quote(r.Context(), b.Destination, domain.Parcel{WeightGrams: b.WeightGrams, DeclaredValue: b.DeclaredValue, Currency: strings.ToUpper(b.Currency)})
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get shipping quotes successfully", q)
	return nil
}

func (h *Handler) track(w http.ResponseWriter, r *http.Request) error {
	s, err := h.svc.Track(r.Context(), chi.URLParam(r, "tracking"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Track shipment successfully", s)
	return nil
}

func (h *Handler) forOrder(w http.ResponseWriter, r *http.Request) error {
	sub, admin := "", true
	if h.verifier != nil {
		p, _ := auth.FromContext(r.Context())
		sub, admin = p.Subject, p.HasRole("ADMIN")
	}
	s, err := h.svc.ForOrder(r.Context(), chi.URLParam(r, "orderId"), sub, admin)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get shipment successfully", s)
	return nil
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) error {
	s, err := h.svc.Get(r.Context(), chi.URLParam(r, "id"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get shipment successfully", s)
	return nil
}

func (h *Handler) label(w http.ResponseWriter, r *http.Request) error {
	var b struct {
		Carrier     string `json:"carrier"`
		ServiceCode string `json:"serviceCode"`
	}
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	s, err := h.svc.CreateLabel(r.Context(), chi.URLParam(r, "id"), b.Carrier, b.ServiceCode)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Label created", s)
	return nil
}

func (h *Handler) status(w http.ResponseWriter, r *http.Request) error {
	var b struct {
		Status   domain.Status `json:"status"`
		Note     string        `json:"note"`
		Location string        `json:"location"`
	}
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	s, err := h.svc.UpdateStatus(r.Context(), chi.URLParam(r, "id"), domain.Status(strings.ToUpper(string(b.Status))), b.Note, b.Location)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Shipment status updated", s)
	return nil
}

func (h *Handler) listRates(w http.ResponseWriter, r *http.Request) error {
	list, err := h.svc.ListRates(r.Context())
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get rates successfully", list)
	return nil
}

func (h *Handler) createRate(w http.ResponseWriter, r *http.Request) error {
	var b domain.Rate
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	b.ID = 0
	x, err := h.svc.CreateRate(r.Context(), b)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Create rate successfully", x)
	return nil
}

func (h *Handler) updateRate(w http.ResponseWriter, r *http.Request) error {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		return apperr.Invalid("invalid id")
	}
	var b domain.Rate
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	b.ID = id
	if err := h.svc.UpdateRate(r.Context(), b); err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Update rate successfully", b)
	return nil
}

func (h *Handler) deleteRate(w http.ResponseWriter, r *http.Request) error {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		return apperr.Invalid("invalid id")
	}
	if err := h.svc.DeleteRate(r.Context(), id); err != nil {
		return err
	}
	httpx.OK[any](w, http.StatusOK, "Delete rate successfully", nil)
	return nil
}
