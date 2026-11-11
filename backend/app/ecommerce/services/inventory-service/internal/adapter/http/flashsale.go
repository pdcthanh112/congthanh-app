package http

import (
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/featureflag"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
)

// startFlashSale: POST /flash-sales/{saleId}/products/{productId}/start
// body {quantity, perCustomerLimit, opensAt, endsAt, admitPerSecond, admitBurst} — giữ hàng trong Postgres
// rồi nạp vào Redis. admitPerSecond > 0 bật phòng chờ.
func (h *Handler) startFlashSale(w http.ResponseWriter, r *http.Request) error {
	var body struct {
		Quantity         int32     `json:"quantity"`
		PerCustomerLimit int32     `json:"perCustomerLimit"`
		OpensAt          time.Time `json:"opensAt"`
		EndsAt           time.Time `json:"endsAt"`
		AdmitPerSecond   int32     `json:"admitPerSecond"`
		AdmitBurst       int32     `json:"admitBurst"`
	}
	if err := httpx.Decode(r, &body); err != nil {
		return err
	}
	res, err := h.svc.StartFlashSale(r.Context(), app.FlashSaleInput{
		SaleID: chi.URLParam(r, "saleId"), ProductID: chi.URLParam(r, "productId"),
		Quantity: body.Quantity, PerCustomerLimit: body.PerCustomerLimit, OpensAt: body.OpensAt, EndsAt: body.EndsAt,
		AdmitPerSecond: body.AdmitPerSecond, AdmitBurst: body.AdmitBurst,
	})
	if err != nil {
		return err
	}
	code := http.StatusCreated
	if !res.Success {
		code = http.StatusConflict
	}
	httpx.OK(w, code, res.Message, res)
	return nil
}

// flashSaleQueue: POST .../queue xếp hàng vào phòng chờ, GET .../queue xem vị trí. Client hỏi lại sau
// retryAfterSeconds (cũng trả ở header Retry-After) cho tới khi canClaim = true rồi gọi .../claim.
func (h *Handler) flashSaleQueue(w http.ResponseWriter, r *http.Request) error {
	q, err := h.svc.FlashSaleQueue(r.Context(), chi.URLParam(r, "saleId"), chi.URLParam(r, "productId"), actor(r), r.Method == http.MethodPost)
	if err != nil {
		return err
	}
	if q.RetryAfterSeconds > 0 {
		w.Header().Set("Retry-After", strconv.FormatInt(q.RetryAfterSeconds, 10))
	}
	httpx.OK(w, http.StatusOK, "OK", q)
	return nil
}

// claimFlashSale: POST .../claim body {quantity}; khách hàng lấy từ JWT.
func (h *Handler) claimFlashSale(w http.ResponseWriter, r *http.Request) error {
	if !featureflag.Bool(r.Context(), "inventory.flash-sale.claim", true, actor(r)) {
		return httpx.Conflict("flash sale is temporarily disabled")
	}
	var body struct {
		Quantity int32 `json:"quantity"`
	}
	if err := httpx.Decode(r, &body); err != nil {
		return err
	}
	c, err := h.svc.ClaimFlashSale(r.Context(), chi.URLParam(r, "saleId"), chi.URLParam(r, "productId"), actor(r), body.Quantity)
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusAccepted, "Claimed", c)
	return nil
}

func (h *Handler) endFlashSale(w http.ResponseWriter, r *http.Request) error {
	sum, err := h.svc.EndFlashSale(r.Context(), chi.URLParam(r, "saleId"), chi.URLParam(r, "productId"))
	if err != nil {
		return err
	}
	httpx.OK(w, http.StatusOK, "Flash sale ended", sum)
	return nil
}
