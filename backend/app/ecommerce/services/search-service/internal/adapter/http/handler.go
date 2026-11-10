// Package http là driving adapter REST public của search-service dưới /api/ecommerce/search.
package http

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/domain"
)

type H = apperr.Handler

type Handler struct{ svc *app.Service }

func New(svc *app.Service) *Handler { return &Handler{svc: svc} }

func (h *Handler) Mount(r chi.Router) {
	r.Route("/api/ecommerce/search", func(r chi.Router) {
		r.Method(http.MethodGet, "/products", H(h.search))
		r.Method(http.MethodGet, "/suggest", H(h.suggest))
	})
}

func num(r *http.Request, k string) (float64, error) {
	v := r.URL.Query().Get(k)
	if v == "" {
		return 0, nil
	}
	f, err := strconv.ParseFloat(v, 64)
	if err != nil {
		return 0, apperr.Invalid("invalid %s", k)
	}
	return f, nil
}

// search: GET /products?q=&categoryId=&minPrice=&maxPrice=&sort=&page=&size=
func (h *Handler) search(w http.ResponseWriter, r *http.Request) error {
	q := r.URL.Query()
	minP, err := num(r, "minPrice")
	if err != nil {
		return err
	}
	maxP, err := num(r, "maxPrice")
	if err != nil {
		return err
	}
	page, _ := strconv.Atoi(q.Get("page"))
	size, _ := strconv.Atoi(q.Get("size"))
	res, err := h.svc.Search(r.Context(), domain.Query{
		Text: q.Get("q"), CategoryID: q.Get("categoryId"), MinPrice: minP, MaxPrice: maxP,
		Sort: domain.Sort(q.Get("sort")), Page: page, Size: size,
	})
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Search products successfully", res)
	return nil
}

func (h *Handler) suggest(w http.ResponseWriter, r *http.Request) error {
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	out, err := h.svc.Suggest(r.Context(), r.URL.Query().Get("q"), limit)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Suggest successfully", out)
	return nil
}
