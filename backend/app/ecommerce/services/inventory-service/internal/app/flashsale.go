package app

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

// Flash sale:
//  1. Start: giữ chỗ số hàng của đợt sale trong Postgres (reservation FLASH_SALE) rồi nạp
//     bộ đếm vào Redis.
//  2. Queue (tuỳ chọn): phòng chờ ảo, khách xếp hàng và được cho vào dần theo AdmitPerSecond.
//  3. Claim: trừ kho nguyên tử bằng Lua trên Redis (không chạm Postgres) và tạo một claim có hạn,
//     đẩy FlashSaleClaimed lên Kafka. checkout-service nghe event này và mở sẵn phiên checkout có
//     id = order id = claim id.
//  4. Khách đặt đơn: saga gọi Reserve/Commit/Release với reservation id của đơn. Vì order id là
//     claim id nên các lệnh đó được chuyển thành xác nhận/chốt/trả claim trên Redis; không giữ thêm
//     hàng trong Postgres (số hàng của sale đã được giữ ở bước 1).
//  5. Claim không được đặt đơn trước hạn thì SweepFlashClaims trả suất lại cho người khác.
//  6. End: khi không còn claim dở dang, chốt đúng số đã bán vào Postgres, phần dư trả về kho.

// reservationPrefix khớp CheckoutContract.reservationId ở libs/java/workflow-contracts.
const reservationPrefix = "checkout-"

type FlashSaleInput struct {
	SaleID           string    `json:"saleId"`
	ProductID        string    `json:"productId"`
	Quantity         int32     `json:"quantity"`
	PerCustomerLimit int32     `json:"perCustomerLimit"`
	OpensAt          time.Time `json:"opensAt"` // để trống = mở ngay
	EndsAt           time.Time `json:"endsAt"`
	// Phòng chờ: số người được vào mỗi giây; 0 = không dùng phòng chờ.
	AdmitPerSecond int32 `json:"admitPerSecond"`
	AdmitBurst     int32 `json:"admitBurst"` // số người vào ngay lúc mở; mặc định = AdmitPerSecond
}

type Claim struct {
	ClaimID string `json:"claimId"`
	// Phiên checkout mở sẵn cho claim này (cùng id). Tạo bất đồng bộ nên có thể trễ vài trăm ms.
	CheckoutSessionID string    `json:"checkoutSessionId"`
	SaleID            string    `json:"saleId"`
	ProductID         string    `json:"productId"`
	Quantity          int32     `json:"quantity"`
	Remaining         int32     `json:"remaining"`
	ExpiresAt         time.Time `json:"expiresAt"`
}

// QueueStatus là thứ khách thấy trong phòng chờ.
type QueueStatus struct {
	WaitingRoom bool      `json:"waitingRoom"` // false = sale này không dùng phòng chờ
	Position    int64     `json:"position"`    // 1 = đầu hàng; 0 = chưa xếp hàng
	Ahead       int64     `json:"ahead"`       // số người còn phải chờ trước mình
	QueueLength int64     `json:"queueLength"`
	CanClaim    bool      `json:"canClaim"`
	Remaining   int32     `json:"remaining"`
	SoldOut     bool      `json:"soldOut"`
	OpensAt     time.Time `json:"opensAt"`
	// Gợi ý cho client: bao lâu nữa tới lượt và nên hỏi lại sau mấy giây.
	EstimatedWaitSeconds int64 `json:"estimatedWaitSeconds"`
	RetryAfterSeconds    int64 `json:"retryAfterSeconds"`
}

type FlashSaleSummary struct {
	SaleID    string `json:"saleId"`
	ProductID string `json:"productId"`
	Initial   int32  `json:"initial"`
	Sold      int32  `json:"sold"`
	Returned  int32  `json:"returned"`
}

type flashClaimRef struct{ saleID, productID, claimID string }

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
	now := s.now()
	if !in.EndsAt.After(now) {
		return domain.ReserveResult{}, domain.Invalid("endsAt must be in the future")
	}
	if in.OpensAt.IsZero() {
		in.OpensAt = now
	}
	if !in.OpensAt.Before(in.EndsAt) {
		return domain.ReserveResult{}, domain.Invalid("opensAt must be before endsAt")
	}
	if in.AdmitPerSecond < 0 || in.AdmitBurst < 0 {
		return domain.ReserveResult{}, domain.Invalid("admitPerSecond and admitBurst must not be negative")
	}
	if in.AdmitPerSecond > 0 && in.AdmitBurst == 0 {
		in.AdmitBurst = in.AdmitPerSecond
	}
	// Reservation phải sống lâu hơn claim cuối cùng (giành sát giờ đóng + thời gian chờ thanh toán).
	res, err := s.Reserve(ctx, ReserveInput{
		ID: flashReservationID(in.SaleID, in.ProductID), Type: "FLASH_SALE",
		ExpiresAt: in.EndsAt.Add(time.Hour + s.opt.FlashClaimTTL),
		Items:     []domain.Item{{ProductID: in.ProductID, Quantity: in.Quantity}},
	})
	if err != nil || !res.Success {
		return res, err
	}
	s.activeSales.Store(-1) // có sale mới: chưa quét lại thì coi như chưa biết
	return res, s.flash.Load(ctx, in.SaleID, in.ProductID, FlashSaleConfig{
		Quantity: in.Quantity, PerCustomerLimit: in.PerCustomerLimit, OpensAt: in.OpensAt,
		AdmitPerSecond: in.AdmitPerSecond, AdmitBurst: in.AdmitBurst,
		TTL: in.EndsAt.Sub(now) + 2*time.Hour + s.opt.FlashClaimTTL,
	})
}

// FlashSaleQueue trả vị trí của khách trong phòng chờ; join = true thì xếp hàng nếu chưa có.
func (s *Service) FlashSaleQueue(ctx context.Context, saleID, productID, customerID string, join bool) (QueueStatus, error) {
	if err := s.flashEnabled(); err != nil {
		return QueueStatus{}, err
	}
	if customerID == "" {
		return QueueStatus{}, domain.Invalid("customerId is required")
	}
	now := s.now()
	q, err := s.flash.Queue(ctx, saleID, productID, customerID, join, now)
	if err != nil {
		return QueueStatus{}, err
	}
	out := QueueStatus{
		WaitingRoom: q.AdmitPerSecond > 0, QueueLength: q.Length, Remaining: q.Remaining,
		SoldOut: q.Remaining <= 0, OpensAt: q.OpensAt,
	}
	untilOpen := max(int64(q.OpensAt.Sub(now)/time.Second), 0)
	open := !now.Before(q.OpensAt)
	if !out.WaitingRoom {
		out.CanClaim = open && !out.SoldOut
		out.EstimatedWaitSeconds = untilOpen
	} else if q.Rank >= 0 {
		out.Position = q.Rank + 1
		out.Ahead = max(q.Rank-q.Admitted+1, 0)
		out.CanClaim = open && q.Rank < q.Admitted && !out.SoldOut
		if out.Ahead > 0 {
			out.EstimatedWaitSeconds = untilOpen + (out.Ahead+int64(q.AdmitPerSecond)-1)/int64(q.AdmitPerSecond)
		}
	}
	if !out.CanClaim && !out.SoldOut {
		out.RetryAfterSeconds = min(max(out.EstimatedWaitSeconds/2, 2), 30)
	}
	return out, nil
}

func (s *Service) ClaimFlashSale(ctx context.Context, saleID, productID, customerID string, qty int32) (Claim, error) {
	if err := s.flashEnabled(); err != nil {
		return Claim{}, err
	}
	if customerID == "" || qty <= 0 {
		return Claim{}, domain.Invalid("customerId and quantity > 0 are required")
	}
	id, err := uuid.NewV7()
	if err != nil {
		return Claim{}, err
	}
	now := s.now()
	c := Claim{
		ClaimID: id.String(), CheckoutSessionID: id.String(), SaleID: saleID, ProductID: productID,
		Quantity: qty, ExpiresAt: now.Add(s.opt.FlashClaimTTL).Truncate(time.Second),
	}
	c.Remaining, err = s.flash.Claim(ctx, ClaimRequest{
		SaleID: saleID, ProductID: productID, CustomerID: customerID, ClaimID: c.ClaimID,
		Quantity: qty, Deadline: c.ExpiresAt, Now: now,
	})
	if err != nil {
		return Claim{}, err
	}
	err = s.claims.PublishClaim(ctx, PublishedClaim{
		ClaimID: c.ClaimID, SaleID: saleID, ProductID: productID, CustomerID: customerID, Quantity: qty, ExpiresAt: c.ExpiresAt,
	})
	if err != nil {
		// Không ghi được Kafka → không ai mở phiên checkout cho claim này: trả suất lại ngay.
		if _, uerr := s.flash.ReleaseClaim(context.WithoutCancel(ctx), saleID, productID, c.ClaimID); uerr != nil {
			return Claim{}, fmt.Errorf("publish claim: %w (release also failed: %v)", err, uerr)
		}
		return Claim{}, fmt.Errorf("publish claim: %w", err)
	}
	return c, nil
}

// flashClaimOf cho biết reservation của saga có phải là một claim flash sale không.
//
// Redis lỗi: nếu lần quét gần nhất thấy không có sale nào đang chạy thì chắc chắn không phải claim,
// đơn thường đi tiếp bình thường (Redis chết không kéo theo luồng đặt hàng). Còn sale đang chạy thì
// trả lỗi để saga thử lại, không đoán: đoán sai sẽ giữ hàng hai lần hoặc bán lố.
func (s *Service) flashClaimOf(ctx context.Context, reservationID string) (flashClaimRef, bool, error) {
	if s.flash == nil {
		return flashClaimRef{}, false, nil
	}
	claimID, ok := strings.CutPrefix(reservationID, reservationPrefix)
	if !ok || uuid.Validate(claimID) != nil {
		return flashClaimRef{}, false, nil
	}
	sale, product, found, err := s.flash.FindClaim(ctx, claimID)
	if err != nil {
		if s.activeSales.Load() == 0 {
			return flashClaimRef{}, false, nil
		}
		return flashClaimRef{}, false, fmt.Errorf("look up flash sale claim: %w", err)
	}
	return flashClaimRef{saleID: sale, productID: product, claimID: claimID}, found, nil
}

func (s *Service) confirmFlashClaim(ctx context.Context, fc flashClaimRef, in ReserveInput, items []domain.Item) (domain.ReserveResult, error) {
	now := s.now()
	res := domain.ReserveResult{ID: in.ID, ReservedAt: now, ExpiresAt: now.Add(s.opt.ReservationTTL)}
	if len(items) != 1 || items[0].ProductID != fc.productID {
		res.Message = "a flash sale order must contain only the claimed product"
		return res, nil
	}
	err := s.flash.ConfirmClaim(ctx, fc.saleID, fc.productID, fc.claimID, in.CustomerID, items[0].Quantity)
	line := domain.ReserveLine{ProductID: fc.productID, Requested: items[0].Quantity}
	if domain.KindOf(err) == domain.KindConflict {
		line.Error = err.Error()
		res.Message, res.Lines = err.Error(), []domain.ReserveLine{line}
		return res, nil
	}
	if err != nil {
		return res, err
	}
	line.Reserved = items[0].Quantity
	res.Success, res.Lines = true, []domain.ReserveLine{line}
	return res, nil
}

func (s *Service) releaseFlashClaim(ctx context.Context, fc flashClaimRef, res domain.ReleaseResult) (domain.ReleaseResult, error) {
	r, err := s.flash.ReleaseClaim(ctx, fc.saleID, fc.productID, fc.claimID)
	if err != nil {
		return res, err
	}
	res.Success = r != ClaimCommitted
	switch r {
	case ClaimReleased:
		res.Lines = []domain.ReleaseLine{{ProductID: fc.productID}}
	case ClaimCommitted:
		res.Message = "flash sale claim already committed"
	default:
		res.Message = "flash sale claim already released"
	}
	return res, nil
}

func (s *Service) commitFlashClaim(ctx context.Context, fc flashClaimRef, res domain.CommitResult) (domain.CommitResult, error) {
	found, err := s.flash.CommitClaim(ctx, fc.saleID, fc.productID, fc.claimID)
	if err != nil {
		return res, err
	}
	// Commit chỉ chạy sau khi đã xác nhận claim, mà claim đã xác nhận thì không bị sweep. Không còn
	// claim nghĩa là đã chốt từ lần gọi trước và sale đã đóng: activity gọi lại, coi là thành công.
	res.Success = true
	if !found {
		res.Message = "flash sale already ended"
	}
	return res, nil
}

// SweepFlashClaims trả suất của các claim quá hạn chưa đặt đơn. Chạy định kỳ ở mọi pod (Lua nguyên tử).
func (s *Service) SweepFlashClaims(ctx context.Context, limit int) (int, error) {
	if s.flash == nil {
		return 0, nil
	}
	n, active, err := s.flash.SweepExpired(ctx, s.now(), limit)
	if err != nil {
		s.activeSales.Store(-1)
		return n, err
	}
	s.activeSales.Store(int64(active))
	return n, nil
}

// EndFlashSale đối soát: chốt số đã bán vào Postgres, trả phần còn lại về kho. Từ chối (Conflict)
// khi còn claim đang checkout hoặc chờ thanh toán; gọi lại sau khi chúng xong hoặc hết hạn.
func (s *Service) EndFlashSale(ctx context.Context, saleID, productID string) (FlashSaleSummary, error) {
	if err := s.flashEnabled(); err != nil {
		return FlashSaleSummary{}, err
	}
	if _, err := s.SweepFlashClaims(ctx, 1000); err != nil {
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
