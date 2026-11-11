// Package domain: quy tắc chọn giá thuần (không I/O). Giá tính bằng đơn vị nhỏ nhất của tiền tệ.
package domain

import (
	"regexp"
	"strings"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
)

const DefaultList = "default"

type SaleKind string

const (
	KindSale  SaleKind = "SALE"
	KindFlash SaleKind = "FLASH"
)

type ScheduleStatus string

const (
	Scheduled ScheduleStatus = "SCHEDULED"
	Active    ScheduleStatus = "ACTIVE"
	Ended     ScheduleStatus = "ENDED"
	Cancelled ScheduleStatus = "CANCELLED"
)

type PriceList struct {
	ID            int64      `json:"id"`
	Code          string     `json:"code"`
	Name          string     `json:"name"`
	Currency      string     `json:"currency"`
	CustomerGroup string     `json:"customerGroup"` // "" = công khai
	Priority      int        `json:"priority"`
	Active        bool       `json:"active"`
	StartsAt      *time.Time `json:"startsAt,omitempty"`
	EndsAt        *time.Time `json:"endsAt,omitempty"`
}

var codeRe = regexp.MustCompile(`^[a-z0-9][a-z0-9_-]{0,62}$`)

func (l *PriceList) Normalize() error {
	l.Code = strings.ToLower(strings.TrimSpace(l.Code))
	l.Currency = strings.ToUpper(strings.TrimSpace(l.Currency))
	l.CustomerGroup = strings.ToUpper(strings.TrimSpace(l.CustomerGroup))
	l.Name = strings.TrimSpace(l.Name)
	switch {
	case !codeRe.MatchString(l.Code):
		return apperr.Invalid("code must be lowercase letters, digits, '-' or '_'")
	case l.Name == "":
		return apperr.Invalid("name is required")
	case len(l.Currency) != 3:
		return apperr.Invalid("currency must be an ISO 4217 code")
	case l.StartsAt != nil && l.EndsAt != nil && !l.EndsAt.After(*l.StartsAt):
		return apperr.Invalid("endsAt must be after startsAt")
	case l.Code == DefaultList && l.CustomerGroup != "":
		return apperr.Invalid("the default price list cannot be restricted to a customer group")
	}
	return nil
}

// AppliesTo: bảng giá đang hiệu lực, đúng tiền tệ, công khai hoặc thuộc nhóm của khách.
func (l PriceList) AppliesTo(currency string, groups []string, at time.Time) bool {
	if !l.Active || !strings.EqualFold(l.Currency, currency) {
		return false
	}
	if (l.StartsAt != nil && at.Before(*l.StartsAt)) || (l.EndsAt != nil && !at.Before(*l.EndsAt)) {
		return false
	}
	if l.CustomerGroup == "" {
		return true
	}
	for _, g := range groups {
		if strings.EqualFold(g, l.CustomerGroup) {
			return true
		}
	}
	return false
}

type Price struct {
	ListID    int64  `json:"priceListId"`
	ProductID string `json:"productId"`
	Amount    int64  `json:"amount"`
	CompareAt *int64 `json:"compareAt,omitempty"`
}

func (p Price) Validate() error {
	switch {
	case strings.TrimSpace(p.ProductID) == "":
		return apperr.Invalid("productId is required")
	case p.Amount < 0:
		return apperr.Invalid("amount must not be negative")
	case p.CompareAt != nil && *p.CompareAt < p.Amount:
		return apperr.Invalid("compareAt must be greater than or equal to amount")
	}
	return nil
}

type Schedule struct {
	ID        string         `json:"id"`
	ListID    int64          `json:"priceListId"`
	ListCode  string         `json:"priceList"`
	ProductID string         `json:"productId"`
	Kind      SaleKind       `json:"kind"`
	Amount    int64          `json:"amount"`
	StartsAt  time.Time      `json:"startsAt"`
	EndsAt    *time.Time     `json:"endsAt,omitempty"`
	Status    ScheduleStatus `json:"status"`
	Reason    string         `json:"reason"`
	CreatedBy string         `json:"createdBy"`
}

// MaxFlashDuration: flash sale ngắn để không ai lỡ tay đặt giá rẻ kéo dài.
const MaxFlashDuration = 72 * time.Hour

func (s Schedule) Validate(now time.Time) error {
	switch {
	case strings.TrimSpace(s.ProductID) == "":
		return apperr.Invalid("productId is required")
	case s.Kind != KindSale && s.Kind != KindFlash:
		return apperr.Invalid("kind must be SALE or FLASH")
	case s.Amount < 0:
		return apperr.Invalid("amount must not be negative")
	case s.EndsAt != nil && !s.EndsAt.After(s.StartsAt):
		return apperr.Invalid("endsAt must be after startsAt")
	case s.EndsAt != nil && !s.EndsAt.After(now):
		return apperr.Invalid("endsAt is in the past")
	case s.Kind == KindFlash && (s.EndsAt == nil || s.EndsAt.Sub(s.StartsAt) > MaxFlashDuration):
		return apperr.Invalid("a flash sale needs endsAt within %s of startsAt", MaxFlashDuration)
	}
	return nil
}

// ActiveAt: khung giờ chứa at và chưa bị huỷ (không phụ thuộc scheduler đã cập nhật status hay chưa).
func (s Schedule) ActiveAt(at time.Time) bool {
	return s.Status != Cancelled && !at.Before(s.StartsAt) && (s.EndsAt == nil || at.Before(*s.EndsAt))
}

// PercentOff: giá sau khi giảm p% (làm tròn xuống đơn vị nhỏ nhất, không âm).
func PercentOff(amount int64, percent float64) (int64, error) {
	if percent <= 0 || percent >= 100 {
		return 0, apperr.Invalid("percentOff must be between 0 and 100")
	}
	return amount - (amount*int64(percent*1000)+99_999)/100_000, nil
}

// Candidate: một bảng giá áp dụng được cho sản phẩm, kèm giá thường và các đợt sale của sản phẩm trong bảng đó.
type Candidate struct {
	List      PriceList
	Base      *Price
	Schedules []Schedule
}

type Resolved struct {
	ProductID       string     `json:"productId"`
	Currency        string     `json:"currency"`
	Amount          int64      `json:"amount"`
	CompareAt       int64      `json:"compareAt"`
	DiscountPercent int        `json:"discountPercent"`
	PriceList       string     `json:"priceList"`
	SaleKind        SaleKind   `json:"saleKind,omitempty"`
	SaleEndsAt      *time.Time `json:"saleEndsAt,omitempty"`
	Lowest30d       *int64     `json:"lowest30d,omitempty"`
}

// effective: giá của một bảng giá tại at. Có nhiều đợt sale chồng nhau thì lấy đợt rẻ nhất.
func (c Candidate) effective(at time.Time) (amount int64, sale *Schedule, ok bool) {
	for i := range c.Schedules {
		s := &c.Schedules[i]
		if s.ActiveAt(at) && (sale == nil || s.Amount < sale.Amount) {
			sale = s
		}
	}
	switch {
	case sale != nil:
		return sale.Amount, sale, true
	case c.Base != nil:
		return c.Base.Amount, nil, true
	}
	return 0, nil, false
}

// Resolve chọn giá cho khách: giá thấp nhất trong các bảng giá áp dụng được (hoà thì priority cao hơn).
// Giá gạch ngang là giá thường của bảng giá công khai "default" (hoặc compareAt khai báo), chỉ khi cao hơn giá bán.
func Resolve(productID, currency string, cands []Candidate, at time.Time) (Resolved, bool) {
	var best *Candidate
	var bestAmount int64
	var bestSale *Schedule
	var reference int64
	for i := range cands {
		c := &cands[i]
		if c.List.Code == DefaultList && c.Base != nil {
			reference = max(reference, c.Base.Amount)
		}
		if c.Base != nil && c.Base.CompareAt != nil {
			reference = max(reference, *c.Base.CompareAt)
		}
		amount, sale, ok := c.effective(at)
		if !ok {
			continue
		}
		if best == nil || amount < bestAmount || (amount == bestAmount && c.List.Priority > best.List.Priority) {
			best, bestAmount, bestSale = c, amount, sale
		}
	}
	if best == nil {
		return Resolved{}, false
	}
	r := Resolved{ProductID: productID, Currency: strings.ToUpper(currency), Amount: bestAmount, CompareAt: bestAmount, PriceList: best.List.Code}
	if bestSale != nil {
		r.SaleKind, r.SaleEndsAt = bestSale.Kind, bestSale.EndsAt
	}
	if reference > bestAmount {
		r.CompareAt = reference
		r.DiscountPercent = int((reference - bestAmount) * 100 / reference)
	}
	return r, true
}
