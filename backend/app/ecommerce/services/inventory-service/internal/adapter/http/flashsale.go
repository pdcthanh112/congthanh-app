package http

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/featureflag"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
)

// startFlashSale: POST /flash-sales/{saleId}/products/{productId}/start
// body {quantity, perCustomerLimit, endsAt} — giữ hàng trong Postgres rồi nạp vào Redis.
func (h *Handler) startFlashSale(w http.ResponseWriter, r *http.Request) error {
	var body struct {
		Quantity         int32     `json:"quantity"`
		PerCustomerLimit int32     `json:"perCustomerLimit"`
		EndsAt           time.Time `json:"endsAt"`
	}
	if err := httpx.Decode(r, &body); err != nil {
		return err
	}
	res, err := h.svc.StartFlashSale(r.Context(), app.FlashSaleInput{
		SaleID: chi.URLParam(r, "saleId"), ProductID: chi.URLParam(r, "productId"),
		Quantity: body.Quantity, PerCustomerLimit: body.PerCustomerLimit, EndsAt: body.EndsAt,
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
