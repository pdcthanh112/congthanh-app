// Package app là use case của search-service; chỉ biết domain và port Index.
package app

import (
	"context"
	"strings"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/domain"
)

// Index là driven port tới công cụ tìm kiếm (OpenSearch).
type Index interface {
	// Upsert/Delete dùng version (thời điểm event, nano giây) để bỏ event cũ đến muộn.
	Upsert(ctx context.Context, p domain.Product, version int64) error
	Delete(ctx context.Context, id string, version int64) error
	Search(ctx context.Context, q domain.Query) (domain.Result, error)
	Suggest(ctx context.Context, prefix string, limit int) ([]string, error)
}

type Service struct{ idx Index }

func NewService(idx Index) *Service { return &Service{idx: idx} }

// Apply cập nhật chỉ mục theo bản chụp sản phẩm mới nhất.
func (s *Service) Apply(ctx context.Context, p domain.Product, at time.Time) error {
	if p.ID == "" {
		return nil
	}
	p.UpdatedAt = at
	if !p.Searchable() {
		return s.idx.Delete(ctx, p.ID, at.UnixNano())
	}
	return s.idx.Upsert(ctx, p, at.UnixNano())
}

func (s *Service) Remove(ctx context.Context, id string, at time.Time) error {
	if id == "" {
		return nil
	}
	return s.idx.Delete(ctx, id, at.UnixNano())
}

func (s *Service) Search(ctx context.Context, q domain.Query) (domain.Result, error) {
	q, err := q.Normalize()
	if err != nil {
		return domain.Result{}, err
	}
	return s.idx.Search(ctx, q)
}

func (s *Service) Suggest(ctx context.Context, prefix string, limit int) ([]string, error) {
	prefix = strings.TrimSpace(prefix)
	if prefix == "" {
		return []string{}, nil
	}
	if len([]rune(prefix)) > 100 {
		return nil, apperr.Invalid("prefix is too long")
	}
	if limit <= 0 || limit > 20 {
		limit = 10
	}
	return s.idx.Suggest(ctx, prefix, limit)
}
