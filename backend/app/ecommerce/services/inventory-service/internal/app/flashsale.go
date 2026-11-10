package app

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

// Flash sale:
//  1. Start: giữ chỗ số hàng của đợt sale trong Postgres (reservation FLASH_SALE) rồi nạp
//     bộ đếm vào Redis.
//  2. Claim: trừ kho nguyên tử bằng Lua trên Redis (không chạm Postgres), đẩy
//     FlashSaleClaimed lên Kafka để order-service tạo đơn bất đồng bộ.
//  3. End: lấy số đã bán từ Redis, chốt (commit) đúng số đó vào Postgres, phần dư trả về kho.
//
// Hàng đợi ảo (virtual waiting room) đặt ở edge/gateway, trước khi request tới đây.

type FlashSaleInput struct {
	SaleID           string    `json:"saleId"`
	ProductID        string    `json:"productId"`
	Quantity         int32     `json:"quantity"`
	PerCustomerLimit int32     `json:"perCustomerLimit"`
	EndsAt           time.Time `json:"endsAt"`
}

type Claim struct {
	ClaimID   string `json:"claimId"`
	SaleID    string `json:"saleId"`
	ProductID string `json:"productId"`
	Quantity  int32  `json:"quantity"`
	Remaining int32  `json:"remaining"`
}

type FlashSaleSummary struct {
	SaleID    string `json:"saleId"`
	ProductID string `json:"productId"`
	Initial   int32  `json:"initial"`
	Sold      int32  `json:"sold"`
	Returned  int32  `json:"returned"`
}

func flashReservationID(saleID, productID string) string {
	return fmt.Sprintf("flashsale:%s:%s", saleID, productID)
}

func (s *Service) flashEnabled() error {
	if s.flash == nil || s.claims == nil {
		return domain.Conflict("flash sale is disabled (Redis/Kafka not configured)")
	}
	return nil
}

func (s *Service) StartFlashSale(ctx context.Context, in FlashSaleInput) (domain.ReserveResult, error) {
	if err := s.flashEnabled(); err != nil {
		return domain.ReserveResult{}, err
	}
	if in.SaleID == "" || in.ProductID == "" || in.Quantity <= 0 {
		return domain.ReserveResult{}, domain.Invalid("saleId, productId and quantity > 0 are required")
	}
	if in.PerCustomerLimit <= 0 {
		in.PerCustomerLimit = 1
	}
	if !in.EndsAt.After(s.now()) {
		return domain.ReserveResult{}, domain.Invalid("endsAt must be in the future")
	}
	res, err := s.Reserve(ctx, ReserveInput{
		ID: flashReservationID(in.SaleID, in.ProductID), Type: "FLASH_SALE",
		ExpiresAt: in.EndsAt.Add(time.Hour), // còn thời gian chốt sau khi sale kết thúc
		Items:     []domain.Item{{ProductID: in.ProductID, Quantity: in.Quantity}},
	})
	if err != nil || !res.Success {
		return res, err
	}
	ttl := time.Until(in.EndsAt) + 2*time.Hour
	return res, s.flash.Load(ctx, in.SaleID, in.ProductID, in.Quantity, in.PerCustomerLimit, ttl)
}

func (s *Service) ClaimFlashSale(ctx context.Context, saleID, productID, customerID string, qty int32) (Claim, error) {
	if err := s.flashEnabled(); err != nil {
		return Claim{}, err
	}
	if customerID == "" || qty <= 0 {
		return Claim{}, domain.Invalid("customerId and quantity > 0 are required")
	}
	remaining, err := s.flash.Claim(ctx, saleID, productID, customerID, qty)
	if err != nil {
		return Claim{}, err
	}
	c := Claim{ClaimID: uuid.NewString(), SaleID: saleID, ProductID: productID, Quantity: qty, Remaining: remaining}
	if err := s.claims.PublishClaim(ctx, c.ClaimID, saleID, productID, customerID, qty); err != nil {
		// Không ghi được Kafka → trả lại lượt cho người khác.
		if uerr := s.flash.Unclaim(context.WithoutCancel(ctx), saleID, productID, customerID, qty); uerr != nil {
			return Claim{}, fmt.Errorf("publish claim: %w (unclaim also failed: %v)", err, uerr)
		}
		return Claim{}, fmt.Errorf("publish claim: %w", err)
	}
	return c, nil
}

// EndFlashSale đối soát: chốt số đã bán vào Postgres, trả phần còn lại về kho.
func (s *Service) EndFlashSale(ctx context.Context, saleID, productID string) (FlashSaleSummary, error) {
	if err := s.flashEnabled(); err != nil {
		return FlashSaleSummary{}, err
	}
	initial, remaining, err := s.flash.Drain(ctx, saleID, productID)
	if err != nil {
		return FlashSaleSummary{}, err
	}
	sold := initial - remaining
	id := flashReservationID(saleID, productID)
	if sold > 0 {
		if _, err := s.Commit(ctx, id, "flashsale:"+saleID, []domain.Item{{ProductID: productID, Quantity: sold}}); err != nil {
			return FlashSaleSummary{}, err
		}
	} else if _, err := s.Release(ctx, id, nil, "flash sale ended without sales", false); err != nil {
		return FlashSaleSummary{}, err
	}
	return FlashSaleSummary{SaleID: saleID, ProductID: productID, Initial: initial, Sold: sold, Returned: remaining}, nil
}
