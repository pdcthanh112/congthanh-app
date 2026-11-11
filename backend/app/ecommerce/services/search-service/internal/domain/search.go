// Package domain là mô hình tìm kiếm sản phẩm: tài liệu chỉ mục, truy vấn và kết quả.
package domain

import (
	"strings"
	"time"
	"unicode/utf8"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
)

// Product là tài liệu trong chỉ mục tìm kiếm (bản chụp từ event của product-service).
type Product struct {
	ID         string    `json:"id"`
	SKU        string    `json:"sku"`
	Name       string    `json:"name"`
	Slug       string    `json:"slug"`
	CategoryID string    `json:"categoryId"`
	Price      float64   `json:"price"`
	Currency   string    `json:"currency"`
	Status     string    `json:"status"`
	UpdatedAt  time.Time `json:"updatedAt"`
}

// Searchable: chỉ sản phẩm đang bán mới có trong chỉ mục; trạng thái khác thì xoá khỏi chỉ mục.
func (p Product) Searchable() bool {
	switch strings.ToUpper(p.Status) {
	case "", "ACTIVE", "PUBLISHED":
		return true
	}
	return false
}

type Sort string

const (
	SortRelevance Sort = "relevance"
	SortPriceAsc  Sort = "price_asc"
	SortPriceDesc Sort = "price_desc"
	SortNewest    Sort = "newest"
)

type Query struct {
	Text       string
	CategoryID string
	MinPrice   float64
	MaxPrice   float64
	Sort       Sort
	Page       int // từ 0
	Size       int
}

const MaxSize = 100

// Normalize đặt mặc định và kiểm tra giới hạn (trang sâu quá 10.000 kết quả bị chặn như Elasticsearch).
func (q Query) Normalize() (Query, error) {
	q.Text = strings.TrimSpace(q.Text)
	if utf8.RuneCountInString(q.Text) > 200 {
		return q, apperr.Invalid("query is too long (max 200 characters)")
	}
	if q.Size <= 0 {
		q.Size = 20
	}
	if q.Size > MaxSize {
		q.Size = MaxSize
	}
	if q.Page < 0 {
		q.Page = 0
	}
	if (q.Page+1)*q.Size > 10_000 {
		return q, apperr.Invalid("page is too deep; refine the query")
	}
	if q.MinPrice < 0 || q.MaxPrice < 0 || (q.MaxPrice > 0 && q.MinPrice > q.MaxPrice) {
		return q, apperr.Invalid("invalid price range")
	}
	switch q.Sort {
	case "":
		q.Sort = SortRelevance
	case SortRelevance, SortPriceAsc, SortPriceDesc, SortNewest:
	default:
		return q, apperr.Invalid("sort must be one of relevance, price_asc, price_desc, newest")
	}
	return q, nil
}

type Hit struct {
	Product
	Score float64 `json:"score"`
}

type Facet struct {
	CategoryID string `json:"categoryId"`
	Count      int64  `json:"count"`
}

type Result struct {
	Hits       []Hit   `json:"hits"`
	Total      int64   `json:"total"`
	Categories []Facet `json:"categories"`
}
