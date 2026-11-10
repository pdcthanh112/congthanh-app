// Package http là driving adapter REST của tax-service dưới /api/ecommerce/tax.
// Đọc (loại thuế, thuế suất, tra cứu, tính thuế) là public; ghi cần vai trò ADMIN.
package http

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/money"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/domain"
)

type H = apperr.Handler

type Handler struct {
	svc      *app.Service
	verifier *auth.Verifier
}

func New(svc *app.Service, v *auth.Verifier) *Handler { return &Handler{svc: svc, verifier: v} }

func (h *Handler) Mount(r chi.Router) {
	r.Route("/api/ecommerce/tax", func(r chi.Router) {
		r.Use(auth.Middleware(h.verifier))
		r.Method(http.MethodGet, "/classes", H(h.listClasses))
		r.Method(http.MethodGet, "/classes/{id}", H(h.getClass))
		r.Method(http.MethodGet, "/rates", H(h.listRates))
		r.Method(http.MethodGet, "/rates/{id}", H(h.getRate))
		r.Method(http.MethodGet, "/lookup", H(h.lookup))
		r.Method(http.MethodPost, "/calculate", H(h.calculate))

		r.Group(func(r chi.Router) {
			r.Use(auth.RequireAuth(h.verifier), h.requireAdmin)
			r.Method(http.MethodPost, "/classes", H(h.createClass))
			r.Method(http.MethodPut, "/classes/{id}", H(h.updateClass))
			r.Method(http.MethodDelete, "/classes/{id}", H(h.deleteClass))
			r.Method(http.MethodPost, "/rates", H(h.createRate))
			r.Method(http.MethodPut, "/rates/{id}", H(h.updateRate))
			r.Method(http.MethodDelete, "/rates/{id}", H(h.deleteRate))
		})
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

func pathID(r *http.Request) (int64, error) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil || id <= 0 {
		return 0, apperr.Invalid("invalid id")
	}
	return id, nil
}

func (h *Handler) listClasses(w http.ResponseWriter, r *http.Request) error {
	list, err := h.svc.ListClasses(r.Context())
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get tax classes successfully", list)
	return nil
}

func (h *Handler) getClass(w http.ResponseWriter, r *http.Request) error {
	id, err := pathID(r)
	if err != nil {
		return err
	}
	c, err := h.svc.GetClass(r.Context(), id)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get tax class successfully", c)
	return nil
}

type classBody struct {
	Name string `json:"name"`
}

func (h *Handler) createClass(w http.ResponseWriter, r *http.Request) error {
	var b classBody
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	c, err := h.svc.CreateClass(r.Context(), b.Name)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Create tax class successfully", c)
	return nil
}

func (h *Handler) updateClass(w http.ResponseWriter, r *http.Request) error {
	id, err := pathID(r)
	if err != nil {
		return err
	}
	var b classBody
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	c := domain.TaxClass{ID: id, Name: b.Name}
	if err := h.svc.UpdateClass(r.Context(), c); err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Update tax class successfully", c)
	return nil
}

func (h *Handler) deleteClass(w http.ResponseWriter, r *http.Request) error {
	id, err := pathID(r)
	if err != nil {
		return err
	}
	if err := h.svc.DeleteClass(r.Context(), id); err != nil {
		return err
	}
	httpx.OK[any](w, http.StatusOK, "Delete tax class successfully", nil)
	return nil
}

type page[T any] struct {
	Items      []T `json:"items"`
	Page       int `json:"page"`
	Size       int `json:"size"`
	TotalItems int `json:"totalItems"`
}

func (h *Handler) listRates(w http.ResponseWriter, r *http.Request) error {
	p, _ := strconv.Atoi(r.URL.Query().Get("page"))
	size, _ := strconv.Atoi(r.URL.Query().Get("size"))
	list, total, err := h.svc.ListRates(r.Context(), p, size)
	if err != nil {
		return err
	}
	if size <= 0 || size > 100 {
		size = 20
	}
	httpx.OK(w, http.StatusOK, "Get tax rates successfully", page[domain.TaxRate]{Items: list, Page: max(p, 0), Size: size, TotalItems: total})
	return nil
}

func (h *Handler) getRate(w http.ResponseWriter, r *http.Request) error {
	id, err := pathID(r)
	if err != nil {
		return err
	}
	t, err := h.svc.GetRate(r.Context(), id)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get tax rate successfully", t)
	return nil
}

func (h *Handler) createRate(w http.ResponseWriter, r *http.Request) error {
	var b domain.TaxRate
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	b.ID = 0
	t, err := h.svc.CreateRate(r.Context(), b)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Create tax rate successfully", t)
	return nil
}

func (h *Handler) updateRate(w http.ResponseWriter, r *http.Request) error {
	id, err := pathID(r)
	if err != nil {
		return err
	}
	var b domain.TaxRate
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	b.ID = id
	if err := h.svc.UpdateRate(r.Context(), b); err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Update tax rate successfully", b.Normalize())
	return nil
}

func (h *Handler) deleteRate(w http.ResponseWriter, r *http.Request) error {
	id, err := pathID(r)
	if err != nil {
		return err
	}
	if err := h.svc.DeleteRate(r.Context(), id); err != nil {
		return err
	}
	httpx.OK[any](w, http.StatusOK, "Delete tax rate successfully", nil)
	return nil
}

// lookup: GET /lookup?taxClassIds=1,2&country=VN&region=HCM&postalCode=700000
func (h *Handler) lookup(w http.ResponseWriter, r *http.Request) error {
	q := r.URL.Query()
	var ids []int64
	for _, s := range strings.Split(q.Get("taxClassIds"), ",") {
		if s = strings.TrimSpace(s); s == "" {
			continue
		}
		id, err := strconv.ParseInt(s, 10, 64)
		if err != nil {
			return apperr.Invalid("invalid taxClassIds")
		}
		ids = append(ids, id)
	}
	rates, err := h.svc.Rates(r.Context(), ids, domain.Address{CountryCode: q.Get("country"), RegionCode: q.Get("region"), PostalCode: q.Get("postalCode")})
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get tax rates successfully", rates)
	return nil
}

type calcLine struct {
	LineID        string  `json:"lineId"`
	TaxClassID    int64   `json:"taxClassId"`
	TaxableAmount float64 `json:"taxableAmount"`
}

type calcBody struct {
	Address  domain.Address `json:"address"`
	Currency string         `json:"currency"`
	Lines    []calcLine     `json:"lines"`
}

type calcLineOut struct {
	LineID      string  `json:"lineId"`
	RatePercent float64 `json:"ratePercent"`
	Tax         float64 `json:"tax"`
}

type calcOut struct {
	Currency string        `json:"currency"`
	Lines    []calcLineOut `json:"lines"`
	TotalTax float64       `json:"totalTax"`
}

// calculate: số tiền REST là số thập phân theo đơn vị chính (199000 VND, 19.99 USD).
func (h *Handler) calculate(w http.ResponseWriter, r *http.Request) error {
	var b calcBody
	if err := httpx.Decode(r, &b); err != nil {
		return err
	}
	cur := strings.ToUpper(b.Currency)
	if len(cur) != 3 {
		return apperr.Invalid("currency must be ISO 4217")
	}
	scale := pow10(money.MinorDigits(cur))
	lines := make([]domain.Line, 0, len(b.Lines))
	for _, l := range b.Lines {
		lines = append(lines, domain.Line{LineID: l.LineID, TaxClassID: l.TaxClassID, TaxableMinor: roundHalfUp(l.TaxableAmount * scale), Currency: cur})
	}
	out, total, err := h.svc.Calculate(r.Context(), b.Address, lines)
	if err != nil {
		return err
	}
	res := calcOut{Currency: cur, TotalTax: float64(total) / scale, Lines: make([]calcLineOut, 0, len(out))}
	for _, l := range out {
		res.Lines = append(res.Lines, calcLineOut{LineID: l.LineID, RatePercent: l.RatePercent, Tax: float64(l.TaxMinor) / scale})
	}
	httpx.OK(w, http.StatusOK, "Calculate tax successfully", res)
	return nil
}

func pow10(n int) float64 {
	f := 1.0
	for range n {
		f *= 10
	}
	return f
}

func roundHalfUp(f float64) int64 {
	if f < 0 {
		return -int64(-f + 0.5)
	}
	return int64(f + 0.5)
}
