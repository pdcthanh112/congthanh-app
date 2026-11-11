// Package http là driving adapter REST của pricing-service dưới /api/ecommerce/pricing.
package http

import (
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/domain"
)

type H = apperr.Handler

type Handler struct {
	svc      *app.Service
	verifier *auth.Verifier
}

func New(svc *app.Service, v *auth.Verifier) *Handler { return &Handler{svc: svc, verifier: v} }

func (h *Handler) Mount(r chi.Router) {
	r.Route("/api/ecommerce/pricing", func(r chi.Router) {
		r.Use(auth.Middleware(h.verifier))
		// Công khai: khách đăng nhập nhận thêm giá theo nhóm (vai trò trong token trùng customerGroup của bảng giá).
		r.Method(http.MethodGet, "/products", H(h.prices))
		r.Method(http.MethodPost, "/quote", H(h.quote))

		r.Group(func(r chi.Router) {
			r.Use(auth.RequireAuth(h.verifier), h.requireAdmin)
			r.Method(http.MethodGet, "/price-lists", H(h.lists))
			r.Method(http.MethodPost, "/price-lists", H(h.createList))
			r.Method(http.MethodPut, "/price-lists/{code}", H(h.updateList))
			r.Method(http.MethodGet, "/price-lists/{code}/prices", H(h.listPrices))
			r.Method(http.MethodPut, "/price-lists/{code}/prices", H(h.upsertPrices))
			r.Method(http.MethodDelete, "/price-lists/{code}/prices/{productId}", H(h.deletePrice))
			r.Method(http.MethodGet, "/schedules", H(h.schedules))
			r.Method(http.MethodPost, "/schedules", H(h.createSchedules))
			r.Method(http.MethodPost, "/schedules/{id}/cancel", H(h.cancelSchedule))
			r.Method(http.MethodGet, "/products/{productId}/history", H(h.history))
		})
	})
}

func (h *Handler) requireAdmin(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if h.verifier != nil {
			if p, _ := auth.FromContext(r.Context()); !p.HasRole("ADMIN") {
				httpx.Fail(w, r, httpx.Forbidden("requires role ADMIN"))
				return
			}
		}
		next.ServeHTTP(w, r)
	})
}

func subject(r *http.Request) string {
	p, _ := auth.FromContext(r.Context())
	return p.Subject
}

// pricingContext: nhóm khách lấy từ token, không nhận từ query để khách không tự gán giá sỉ.
func pricingContext(r *http.Request, currency string) app.Context {
	p, _ := auth.FromContext(r.Context())
	return app.Context{Currency: currency, Groups: p.Roles}
}

func (h *Handler) prices(w http.ResponseWriter, r *http.Request) error {
	q := r.URL.Query()
	var ids []string
	for _, v := range q["ids"] {
		ids = append(ids, strings.Split(v, ",")...)
	}
	pc := pricingContext(r, q.Get("currency"))
	if at := q.Get("at"); at != "" {
		t, err := time.Parse(time.RFC3339, at)
		if err != nil {
			return apperr.Invalid("at must be RFC 3339")
		}
		pc.At = t
	}
	res, err := h.svc.Prices(r.Context(), ids, pc)
	if err != nil {
		return err
	}
	out := make([]domain.Resolved, 0, len(res))
	for _, id := range ids {
		if p, ok := res[strings.TrimSpace(id)]; ok {
			out = append(out, p)
			delete(res, strings.TrimSpace(id))
		}
	}
	httpx.OK(w, http.StatusOK, "Get prices successfully", out)
	return nil
}

type quoteBody struct {
	Currency string     `json:"currency"`
	Lines    []app.Line `json:"lines"`
}

func (h *Handler) quote(w http.ResponseWriter, r *http.Request) error {
	var b quoteBody
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	q, err := h.svc.QuoteLines(r.Context(), b.Lines, pricingContext(r, b.Currency))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Quote lines successfully", q)
	return nil
}

func (h *Handler) lists(w http.ResponseWriter, r *http.Request) error {
	ls, err := h.svc.PriceLists(r.Context())
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get price lists successfully", ls)
	return nil
}

func (h *Handler) createList(w http.ResponseWriter, r *http.Request) error {
	var l domain.PriceList
	if err := httpx.Decode(r, &l); err != nil {
		return err
	}
	out, err := h.svc.CreatePriceList(r.Context(), l)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Create price list successfully", out)
	return nil
}

func (h *Handler) updateList(w http.ResponseWriter, r *http.Request) error {
	var l domain.PriceList
	if err := httpx.Decode(r, &l); err != nil {
		return err
	}
	l.Code = chi.URLParam(r, "code")
	if err := h.svc.UpdatePriceList(r.Context(), l); err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Update price list successfully", l)
	return nil
}

func intParam(r *http.Request, name string) int {
	n, _ := strconv.Atoi(r.URL.Query().Get(name))
	return n
}

func (h *Handler) listPrices(w http.ResponseWriter, r *http.Request) error {
	ps, err := h.svc.PricesInList(r.Context(), chi.URLParam(r, "code"), r.URL.Query().Get("productId"), intParam(r, "limit"), intParam(r, "offset"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get prices successfully", ps)
	return nil
}

type upsertBody struct {
	Prices []domain.Price `json:"prices"`
}

func (h *Handler) upsertPrices(w http.ResponseWriter, r *http.Request) error {
	var b upsertBody
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	n, err := h.svc.UpsertPrices(r.Context(), chi.URLParam(r, "code"), b.Prices, subject(r))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Update prices successfully", map[string]int{"changed": n})
	return nil
}

func (h *Handler) deletePrice(w http.ResponseWriter, r *http.Request) error {
	if err := h.svc.DeletePrice(r.Context(), chi.URLParam(r, "code"), chi.URLParam(r, "productId"), subject(r)); err != nil {
		return err
	}
	httpx.OK[any](w, http.StatusOK, "Delete price successfully", nil)
	return nil
}

func (h *Handler) schedules(w http.ResponseWriter, r *http.Request) error {
	q := r.URL.Query()
	ss, err := h.svc.Schedules(r.Context(), app.ScheduleFilter{ProductID: q.Get("productId"),
		Status: domain.ScheduleStatus(strings.ToUpper(q.Get("status"))), Limit: intParam(r, "limit")})
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get schedules successfully", ss)
	return nil
}

func (h *Handler) createSchedules(w http.ResponseWriter, r *http.Request) error {
	var b app.NewSchedule
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	b.Kind = domain.SaleKind(strings.ToUpper(string(b.Kind)))
	ss, err := h.svc.CreateSchedules(r.Context(), b, subject(r))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Create schedules successfully", ss)
	return nil
}

func (h *Handler) cancelSchedule(w http.ResponseWriter, r *http.Request) error {
	s, err := h.svc.CancelSchedule(r.Context(), chi.URLParam(r, "id"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Cancel schedule successfully", s)
	return nil
}

func (h *Handler) history(w http.ResponseWriter, r *http.Request) error {
	hs, err := h.svc.History(r.Context(), chi.URLParam(r, "productId"), intParam(r, "limit"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get price history successfully", hs)
	return nil
}
