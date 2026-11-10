// Package app là use case của tax-service; chỉ biết domain và port Repository.
package app

import (
	"context"
	"strings"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/domain"
)

// Repository là driven port lưu loại thuế và thuế suất.
type Repository interface {
	ListClasses(ctx context.Context) ([]domain.TaxClass, error)
	GetClass(ctx context.Context, id int64) (domain.TaxClass, error)
	CreateClass(ctx context.Context, name string) (domain.TaxClass, error)
	UpdateClass(ctx context.Context, c domain.TaxClass) error
	DeleteClass(ctx context.Context, id int64) error

	ListRates(ctx context.Context, page, size int) ([]domain.TaxRate, int, error)
	GetRate(ctx context.Context, id int64) (domain.TaxRate, error)
	CreateRate(ctx context.Context, r domain.TaxRate) (domain.TaxRate, error)
	UpdateRate(ctx context.Context, r domain.TaxRate) error
	DeleteRate(ctx context.Context, id int64) error
	// RatesFor trả mọi thuế suất của quốc gia cho các loại thuế (ứng viên để domain chọn).
	RatesFor(ctx context.Context, country string, classIDs []int64) ([]domain.TaxRate, error)
}

type Service struct{ repo Repository }

func NewService(r Repository) *Service { return &Service{repo: r} }

func (s *Service) ListClasses(ctx context.Context) ([]domain.TaxClass, error) {
	return s.repo.ListClasses(ctx)
}
func (s *Service) GetClass(ctx context.Context, id int64) (domain.TaxClass, error) {
	return s.repo.GetClass(ctx, id)
}

func (s *Service) CreateClass(ctx context.Context, name string) (domain.TaxClass, error) {
	if err := domain.ValidateClassName(name); err != nil {
		return domain.TaxClass{}, err
	}
	return s.repo.CreateClass(ctx, strings.TrimSpace(name))
}

func (s *Service) UpdateClass(ctx context.Context, c domain.TaxClass) error {
	if err := domain.ValidateClassName(c.Name); err != nil {
		return err
	}
	c.Name = strings.TrimSpace(c.Name)
	return s.repo.UpdateClass(ctx, c)
}

func (s *Service) DeleteClass(ctx context.Context, id int64) error {
	return s.repo.DeleteClass(ctx, id)
}

func (s *Service) ListRates(ctx context.Context, page, size int) ([]domain.TaxRate, int, error) {
	if page < 0 {
		page = 0
	}
	if size <= 0 || size > 100 {
		size = 20
	}
	return s.repo.ListRates(ctx, page, size)
}

func (s *Service) GetRate(ctx context.Context, id int64) (domain.TaxRate, error) {
	return s.repo.GetRate(ctx, id)
}

func (s *Service) CreateRate(ctx context.Context, r domain.TaxRate) (domain.TaxRate, error) {
	r = r.Normalize()
	if err := r.Validate(); err != nil {
		return domain.TaxRate{}, err
	}
	if _, err := s.repo.GetClass(ctx, r.TaxClassID); err != nil {
		return domain.TaxRate{}, err
	}
	return s.repo.CreateRate(ctx, r)
}

func (s *Service) UpdateRate(ctx context.Context, r domain.TaxRate) error {
	r = r.Normalize()
	if err := r.Validate(); err != nil {
		return err
	}
	if _, err := s.repo.GetClass(ctx, r.TaxClassID); err != nil {
		return err
	}
	return s.repo.UpdateRate(ctx, r)
}

func (s *Service) DeleteRate(ctx context.Context, id int64) error { return s.repo.DeleteRate(ctx, id) }

// ClassRate là thuế suất áp dụng cho một loại thuế tại một địa chỉ.
type ClassRate struct {
	TaxClassID  int64   `json:"taxClassId"`
	RatePercent float64 `json:"ratePercent"`
	Matched     bool    `json:"matched"`
}

// Rates trả thuế suất áp dụng cho từng loại thuế tại địa chỉ (không khớp = 0%, matched=false).
func (s *Service) Rates(ctx context.Context, classIDs []int64, a domain.Address) ([]ClassRate, error) {
	a = a.Normalize()
	if err := a.Validate(); err != nil {
		return nil, err
	}
	if len(classIDs) == 0 {
		return nil, apperr.Invalid("at least one taxClassId is required")
	}
	if len(classIDs) > 200 {
		return nil, apperr.Invalid("at most 200 tax classes per request")
	}
	cands, err := s.repo.RatesFor(ctx, a.CountryCode, classIDs)
	if err != nil {
		return nil, err
	}
	out := make([]ClassRate, 0, len(classIDs))
	for _, id := range classIDs {
		r, ok := domain.BestRate(cands, id, a)
		out = append(out, ClassRate{TaxClassID: id, RatePercent: r.RatePercent, Matched: ok})
	}
	return out, nil
}

// Calculate tính thuế cho các dòng của đơn hàng tại địa chỉ giao.
func (s *Service) Calculate(ctx context.Context, a domain.Address, lines []domain.Line) ([]domain.LineTax, int64, error) {
	a = a.Normalize()
	if err := a.Validate(); err != nil {
		return nil, 0, err
	}
	if len(lines) > 500 {
		return nil, 0, apperr.Invalid("at most 500 lines per request")
	}
	seen := map[int64]bool{}
	var ids []int64
	for _, l := range lines {
		if !seen[l.TaxClassID] {
			seen[l.TaxClassID] = true
			ids = append(ids, l.TaxClassID)
		}
	}
	var cands []domain.TaxRate
	if len(ids) > 0 {
		var err error
		if cands, err = s.repo.RatesFor(ctx, a.CountryCode, ids); err != nil {
			return nil, 0, err
		}
	}
	return domain.Calculate(cands, a, lines)
}
