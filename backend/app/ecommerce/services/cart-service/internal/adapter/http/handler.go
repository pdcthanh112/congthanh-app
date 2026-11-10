// Package http là driving adapter REST của cart-service, giữ nguyên đường dẫn và format
// phản hồi của bản Java (/api/ecommerce/carts/...).
package http

import (
	"context"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/grpcx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
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
	r.Route("/api/ecommerce/carts", func(r chi.Router) {
		r.Use(auth.Middleware(h.verifier), auth.RequireAuth(h.verifier), forwardToken)

		r.Method(http.MethodPost, "/", H(h.create))
		r.Method(http.MethodGet, "/customer/{customerId}", H(h.listForCustomer))
		r.Method(http.MethodGet, "/customer/{customerId}/default", H(h.defaultCart))
		r.Method(http.MethodGet, "/customer-backoffice/{customerId}", H(h.listForBackoffice))

		r.Route("/{cartId}", func(r chi.Router) {
			r.Method(http.MethodGet, "/", H(h.get))
			r.Method(http.MethodPut, "/", H(h.update))
			r.Method(http.MethodDelete, "/", H(h.delete))
			r.Method(http.MethodPost, "/validate", H(h.validate))
			r.Method(http.MethodGet, "/items", H(h.items))
			r.Method(http.MethodPost, "/items", H(h.addItem))
			r.Method(http.MethodDelete, "/items", H(h.clear))
			r.Method(http.MethodPut, "/items/{itemId}", H(h.updateItem))
			r.Method(http.MethodDelete, "/items/{itemId}", H(h.deleteItem))
			r.Method(http.MethodGet, "/items/{itemId}/detail", H(h.itemDetail))
		})
	})
}

func forwardToken(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		next.ServeHTTP(w, r.WithContext(grpcx.WithToken(r.Context(), r.Header.Get("Authorization"))))
	})
}

// authorize: khi bật xác thực, chỉ chủ giỏ (sub của token) hoặc ADMIN được truy cập.
func (h *Handler) authorize(ctx context.Context, customerID string) error {
	if h.verifier == nil {
		return nil
	}
	p, _ := auth.FromContext(ctx)
	if p.Subject == customerID || p.HasRole("ADMIN") {
		return nil
	}
	return httpx.Forbidden("you do not have access to this cart")
}

func (h *Handler) requireAdmin(ctx context.Context) error {
	if h.verifier == nil {
		return nil
	}
	if p, _ := auth.FromContext(ctx); p.HasRole("ADMIN") {
		return nil
	}
	return httpx.Forbidden("admin role required")
}

func idParam(r *http.Request, name string) (int64, error) {
	v, err := strconv.ParseInt(chi.URLParam(r, name), 10, 64)
	if err != nil {
		return 0, httpx.BadRequest(name + " must be a number")
	}
	return v, nil
}

// ownedCart đọc cartId từ URL, tải giỏ và kiểm tra quyền.
func (h *Handler) ownedCart(r *http.Request) (domain.Cart, error) {
	id, err := idParam(r, "cartId")
	if err != nil {
		return domain.Cart{}, err
	}
	c, err := h.svc.Get(r.Context(), id)
	if err != nil {
		return domain.Cart{}, err
	}
	return c, h.authorize(r.Context(), c.CustomerID)
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) error {
	c, err := h.ownedCart(r)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get xong", toDTO(c))
	return nil
}

func (h *Handler) listForCustomer(w http.ResponseWriter, r *http.Request) error {
	cid := chi.URLParam(r, "customerId")
	if err := h.authorize(r.Context(), cid); err != nil {
		return err
	}
	carts, err := h.svc.ListByCustomer(r.Context(), cid, false)
	if err != nil {
		return err
	}
	out := make([]CartVm, len(carts))
	for i, c := range carts {
		out[i] = toVm(c)
	}
	httpx.OK(w, http.StatusOK, "Get xong", out)
	return nil
}

func (h *Handler) defaultCart(w http.ResponseWriter, r *http.Request) error {
	cid := chi.URLParam(r, "customerId")
	if err := h.authorize(r.Context(), cid); err != nil {
		return err
	}
	c, err := h.svc.GetDefault(r.Context(), cid)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get xong", toVm(c))
	return nil
}

func (h *Handler) listForBackoffice(w http.ResponseWriter, r *http.Request) error {
	if err := h.requireAdmin(r.Context()); err != nil {
		return err
	}
	carts, err := h.svc.ListByCustomer(r.Context(), chi.URLParam(r, "customerId"), true)
	if err != nil {
		return err
	}
	out := make([]CartDTO, len(carts))
	for i, c := range carts {
		out[i] = toDTO(c)
	}
	httpx.OK(w, http.StatusOK, "Get xong", out)
	return nil
}

func (h *Handler) create(w http.ResponseWriter, r *http.Request) error {
	var in app.CreateInput
	if err := httpx.Decode(r, &in); err != nil {
		return err
	}
	if p, ok := auth.FromContext(r.Context()); ok && in.CustomerID == "" {
		in.CustomerID = p.Subject
	}
	if err := h.authorize(r.Context(), in.CustomerID); err != nil {
		return err
	}
	c, err := h.svc.Create(r.Context(), in)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusCreated, "Created successfully", toDTO(c))
	return nil
}

func (h *Handler) update(w http.ResponseWriter, r *http.Request) error {
	c, err := h.ownedCart(r)
	if err != nil {
		return err
	}
	var in app.UpdateInput
	if err := httpx.Decode(r, &in); err != nil {
		return err
	}
	c, err = h.svc.Update(r.Context(), c.ID, in)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Update successfully", toDTO(c))
	return nil
}

func (h *Handler) delete(w http.ResponseWriter, r *http.Request) error {
	c, err := h.ownedCart(r)
	if err != nil {
		return err
	}
	if err := h.svc.Delete(r.Context(), c.ID); err != nil {
		return err
	}
	httpx.OK[any](w, http.StatusOK, "Delete successfully", nil)
	return nil
}

func (h *Handler) items(w http.ResponseWriter, r *http.Request) error {
	c, err := h.ownedCart(r)
	if err != nil {
		return err
	}
	out := make([]CartItemDTO, len(c.Items))
	for i, it := range c.Items {
		out[i] = toItemDTO(it)
	}
	httpx.OK(w, http.StatusOK, "Get xong", out)
	return nil
}

func (h *Handler) addItem(w http.ResponseWriter, r *http.Request) error {
	c, err := h.ownedCart(r)
	if err != nil {
		return err
	}
	var body struct {
		ProductID string `json:"productId"`
		Quantity  int32  `json:"quantity"`
	}
	if err := httpx.Decode(r, &body); err != nil {
		return err
	}
	it, err := h.svc.AddItem(r.Context(), c.ID, body.ProductID, body.Quantity)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Add to cart successfully", toItemDTO(it))
	return nil
}

// updateItem: PUT /{cartId}/items/{itemId}?quantity=N (giống bản Java).
func (h *Handler) updateItem(w http.ResponseWriter, r *http.Request) error {
	c, err := h.ownedCart(r)
	if err != nil {
		return err
	}
	itemID, err := idParam(r, "itemId")
	if err != nil {
		return err
	}
	q, err := strconv.Atoi(r.URL.Query().Get("quantity"))
	if err != nil {
		return httpx.BadRequest("quantity must be a number")
	}
	it, err := h.svc.UpdateItem(r.Context(), c.ID, itemID, int32(q))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Update successfully", toItemDTO(it))
	return nil
}

func (h *Handler) deleteItem(w http.ResponseWriter, r *http.Request) error {
	c, err := h.ownedCart(r)
	if err != nil {
		return err
	}
	itemID, err := idParam(r, "itemId")
	if err != nil {
		return err
	}
	if err := h.svc.DeleteItem(r.Context(), c.ID, itemID); err != nil {
		return err
	}
	httpx.OK[any](w, http.StatusOK, "Delete successfully", nil)
	return nil
}

func (h *Handler) clear(w http.ResponseWriter, r *http.Request) error {
	c, err := h.ownedCart(r)
	if err != nil {
		return err
	}
	n, err := h.svc.Clear(r.Context(), c.ID, r.URL.Query()["productId"])
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Delete successfully", map[string]int{"removedItems": n})
	return nil
}

func (h *Handler) itemDetail(w http.ResponseWriter, r *http.Request) error {
	c, err := h.ownedCart(r)
	if err != nil {
		return err
	}
	itemID, err := idParam(r, "itemId")
	if err != nil {
		return err
	}
	d, err := h.svc.ItemDetail(r.Context(), c.ID, itemID)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Get successfully", d)
	return nil
}

func (h *Handler) validate(w http.ResponseWriter, r *http.Request) error {
	c, err := h.ownedCart(r)
	if err != nil {
		return err
	}
	v, err := h.svc.Validate(r.Context(), c.ID)
	if err != nil {
		return err
	}
	msg := "Cart is valid"
	if !v.Valid {
		msg = "Cart has invalid items"
	}
	httpx.OK(w, http.StatusOK, msg, v)
	return nil
}
