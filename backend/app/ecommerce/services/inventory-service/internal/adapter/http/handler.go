// Package http là driving adapter REST của inventory-service, giữ các đường dẫn cũ dưới
// /api/ecommerce/inventory và thêm /api/ecommerce/warehouses (gộp từ warehouse-service).
package http

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

type Handler struct {
	svc      *app.Service
	verifier *auth.Verifier
}

func New(svc *app.Service, v *auth.Verifier) *Handler { return &Handler{svc: svc, verifier: v} }

// H bọc handler: lỗi nghiệp vụ (domain.Error) được đổi sang HTTP status.
type H func(w http.ResponseWriter, r *http.Request) error

func (h H) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	httpx.Handler(func(w http.ResponseWriter, r *http.Request) error { return toHTTP(h(w, r)) }).ServeHTTP(w, r)
}

func (h *Handler) Mount(r chi.Router) {
	r.Route("/api/ecommerce", func(r chi.Router) {
		r.Use(auth.Middleware(h.verifier))

		r.Route("/inventory", func(r chi.Router) {
			// Đọc tồn kho: công khai (storefront cần hiển thị còn hàng).
			r.Method(http.MethodGet, "/{sku}", H(h.getBySKU))
			r.Method(http.MethodGet, "/{sku}/in-stock", H(h.inStock))
			r.Method(http.MethodPost, "/availability", H(h.availability))
			r.Method(http.MethodGet, "/levels", H(h.levels))

			r.Group(func(r chi.Router) {
				r.Use(auth.RequireAuth(h.verifier))
				r.Method(http.MethodPost, "/", H(h.upsert))
				r.Method(http.MethodPut, "/{sku}", H(h.updateQuantity))
				r.Method(http.MethodDelete, "/{sku}", H(h.deleteBySKU))
				r.Method(http.MethodPost, "/adjust", H(h.adjust))
				r.Method(http.MethodPost, "/reserve", H(h.reserve))
				r.Method(http.MethodPost, "/release", H(h.release))
				r.Method(http.MethodPost, "/commit", H(h.commit))
			})
		})

		r.Route("/flash-sales/{saleId}/products/{productId}", func(r chi.Router) {
			r.Use(auth.RequireAuth(h.verifier))
			r.With(h.requireAdmin).Method(http.MethodPost, "/start", H(h.startFlashSale))
			r.Method(http.MethodPost, "/queue", H(h.flashSaleQueue))
			r.Method(http.MethodGet, "/queue", H(h.flashSaleQueue))
			r.Method(http.MethodPost, "/claim", H(h.claimFlashSale))
			r.With(h.requireAdmin).Method(http.MethodPost, "/end", H(h.endFlashSale))
		})

		r.Route("/warehouses", func(r chi.Router) {
			r.Method(http.MethodGet, "/", H(h.listWarehouses))
			r.Method(http.MethodGet, "/{id}", H(h.getWarehouse))
			r.Group(func(r chi.Router) {
				r.Use(auth.RequireAuth(h.verifier))
				r.Method(http.MethodPost, "/", H(h.createWarehouse))
				r.Method(http.MethodPut, "/{id}", H(h.updateWarehouse))
				r.Method(http.MethodDelete, "/{id}", H(h.deleteWarehouse))
			})
		})
	})
}

func actor(r *http.Request) string {
	if p, ok := auth.FromContext(r.Context()); ok {
		return p.Subject
	}
	return ""
}

// SKUView là dạng trả về của GET /inventory/{sku}: tổng hợp mọi kho.
type SKUView struct {
	SKU       string         `json:"sku"`
	ProductID string         `json:"productId"`
	Quantity  int32          `json:"quantity"`
	Reserved  int32          `json:"reserved"`
	Available int32          `json:"available"`
	Stocks    []domain.Stock `json:"warehouses"`
}

func (h *Handler) getBySKU(w http.ResponseWriter, r *http.Request) error {
	sku := chi.URLParam(r, "sku")
	stocks, err := h.svc.StocksBySKU(r.Context(), sku)
	if err != nil {
		return err
	}
	v := SKUView{SKU: sku, ProductID: stocks[0].ProductID, Stocks: stocks}
	for _, st := range stocks {
		v.Quantity += st.Quantity
		v.Reserved += st.Reserved
		if !st.Discontinued {
			v.Available += st.Available()
		}
	}
	httpx.OK(w, http.StatusOK, "Get successfully", v)
	return nil
}

func (h *Handler) inStock(w http.ResponseWriter, r *http.Request) error {
	q, err := strconv.Atoi(r.URL.Query().Get("quantity"))
	if err != nil || q <= 0 {
		return httpx.BadRequest("quantity must be a positive integer")
	}
	stocks, err := h.svc.StocksBySKU(r.Context(), chi.URLParam(r, "sku"))
	if err != nil {
		if domain.KindOf(err) == domain.KindNotFound {
			httpx.OK(w, http.StatusOK, "Get successfully", false)
			return nil
		}
		return err
	}
	var avail int32
	for _, st := range stocks {
		if !st.Discontinued {
			avail += st.Available()
		}
	}
	httpx.OK(w, http.StatusOK, "Get successfully", avail >= int32(q))
	return nil
}

func (h *Handler) availability(w http.ResponseWriter, r *http.Request) error {
	var body struct {
		Items       []domain.Item `json:"items"`
		WarehouseID string        `json:"warehouseId"`
	}
	if err := httpx.Decode(r, &body); err != nil {
		return err
	}
	av, err := h.svc.CheckAvailability(r.Context(), body.Items, body.WarehouseID)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get successfully", av)
	return nil
}

func (h *Handler) levels(w http.ResponseWriter, r *http.Request) error {
	var ids []string
	for _, p := range strings.Split(r.URL.Query().Get("productIds"), ",") {
		if p = strings.TrimSpace(p); p != "" {
			ids = append(ids, p)
		}
	}
	lv, err := h.svc.Levels(r.Context(), ids, r.URL.Query().Get("warehouseId"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get successfully", lv)
	return nil
}

func (h *Handler) upsert(w http.ResponseWriter, r *http.Request) error {
	var in app.UpsertStockInput
	if err := httpx.Decode(r, &in); err != nil {
		return err
	}
	st, err := h.svc.UpsertStock(r.Context(), in, actor(r))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Created successfully", st)
	return nil
}

// updateQuantity: PUT /inventory/{sku}?quantity=&warehouseId= (giữ tham số như bản Java).
func (h *Handler) updateQuantity(w http.ResponseWriter, r *http.Request) error {
	q, err := strconv.Atoi(r.URL.Query().Get("quantity"))
	if err != nil || q < 0 {
		return httpx.BadRequest("quantity must be a non-negative integer")
	}
	sku := chi.URLParam(r, "sku")
	stocks, err := h.svc.StocksBySKU(r.Context(), sku)
	if err != nil {
		return err
	}
	wh := r.URL.Query().Get("warehouseId")
	if wh == "" {
		if len(stocks) > 1 {
			return httpx.BadRequest("SKU is stocked in several warehouses; warehouseId is required")
		}
		wh = stocks[0].WarehouseID
	}
	qty := int32(q)
	st, err := h.svc.UpsertStock(r.Context(), app.UpsertStockInput{
		ProductID: stocks[0].ProductID, SKU: sku, WarehouseID: wh, Quantity: &qty,
	}, actor(r))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Update successfully", st)
	return nil
}

func (h *Handler) deleteBySKU(w http.ResponseWriter, r *http.Request) error {
	if err := h.svc.DeleteBySKU(r.Context(), chi.URLParam(r, "sku")); err != nil {
		return err
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

func (h *Handler) adjust(w http.ResponseWriter, r *http.Request) error {
	var body struct {
		Adjustments []app.Adjustment `json:"adjustments"`
		Reason      string           `json:"reason"`
		ReferenceID string           `json:"referenceId"`
	}
	if err := httpx.Decode(r, &body); err != nil {
		return err
	}
	res, err := h.svc.Adjust(r.Context(), body.Adjustments, body.Reason, body.ReferenceID, actor(r))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, res.Message, res)
	return nil
}

func (h *Handler) reserve(w http.ResponseWriter, r *http.Request) error {
	var body struct {
		ReservationID string        `json:"reservationId"`
		CustomerID    string        `json:"customerId"`
		Type          string        `json:"reservationType"`
		Items         []domain.Item `json:"items"`
	}
	if err := httpx.Decode(r, &body); err != nil {
		return err
	}
	res, err := h.svc.Reserve(r.Context(), app.ReserveInput{
		ID: body.ReservationID, CustomerID: body.CustomerID, Type: domain.ParseReservationType(strings.ToUpper(body.Type)), Items: body.Items,
	})
	if err != nil {
		return err
	}
	code := http.StatusOK
	if !res.Success {
		code = http.StatusConflict
	}
	httpx.OK(w, code, res.Message, res)
	return nil
}

func (h *Handler) release(w http.ResponseWriter, r *http.Request) error {
	var body struct {
		ReservationID string   `json:"reservationId"`
		ProductIDs    []string `json:"productIds"`
		Reason        string   `json:"reason"`
	}
	if err := httpx.Decode(r, &body); err != nil {
		return err
	}
	res, err := h.svc.Release(r.Context(), body.ReservationID, body.ProductIDs, body.Reason, false)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, res.Message, res)
	return nil
}

func (h *Handler) commit(w http.ResponseWriter, r *http.Request) error {
	var body struct {
		ReservationID string        `json:"reservationId"`
		OrderID       string        `json:"orderId"`
		Items         []domain.Item `json:"items"`
	}
	if err := httpx.Decode(r, &body); err != nil {
		return err
	}
	res, err := h.svc.Commit(r.Context(), body.ReservationID, body.OrderID, body.Items)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, res.Message, res)
	return nil
}

func (h *Handler) listWarehouses(w http.ResponseWriter, r *http.Request) error {
	ws, err := h.svc.ListWarehouses(r.Context())
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get successfully", ws)
	return nil
}

func (h *Handler) getWarehouse(w http.ResponseWriter, r *http.Request) error {
	wh, err := h.svc.GetWarehouse(r.Context(), chi.URLParam(r, "id"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get successfully", wh)
	return nil
}

func (h *Handler) createWarehouse(w http.ResponseWriter, r *http.Request) error {
	var in app.WarehouseInput
	if err := httpx.Decode(r, &in); err != nil {
		return err
	}
	wh, err := h.svc.CreateWarehouse(r.Context(), in)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Created successfully", wh)
	return nil
}

func (h *Handler) updateWarehouse(w http.ResponseWriter, r *http.Request) error {
	var in app.WarehouseInput
	if err := httpx.Decode(r, &in); err != nil {
		return err
	}
	wh, err := h.svc.UpdateWarehouse(r.Context(), chi.URLParam(r, "id"), in)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Update successfully", wh)
	return nil
}

func (h *Handler) deleteWarehouse(w http.ResponseWriter, r *http.Request) error {
	if err := h.svc.DeleteWarehouse(r.Context(), chi.URLParam(r, "id")); err != nil {
		return err
	}
	httpx.OK[any](w, http.StatusOK, "Delete successfully", nil)
	return nil
}

// requireAdmin: mở/đóng flash sale là thao tác vận hành, khách không được gọi.
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
