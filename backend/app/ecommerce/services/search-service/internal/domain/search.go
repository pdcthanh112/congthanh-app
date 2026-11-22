// Package domain là mô hình tìm kiếm sản phẩm: tài liệu chỉ mục, truy vấn và kết quả.
package domain

import (
	"strings"
	"time"
	"unicode/utf8"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
)

// Product là tài liệu trong chỉ mục tìm kiếm (bản chụp từ event của product-service). Chỉ sản phẩm gốc được lập
// chỉ mục; variant gộp vào sản phẩm gốc: SKU, khoảng giá và giá trị option của các variant đang bán (ADR 0030).
type Product struct {
	ID          string      `json:"id"`
	SKU         string      `json:"sku"`
	SKUs        []string    `json:"skus,omitempty"`
	Name        string      `json:"name"`
	Slug        string      `json:"slug"`
	CategoryID  string      `json:"categoryId"`
	CategoryIDs []string    `json:"categoryIds,omitempty"`
	BrandID     string      `json:"brandId,omitempty"`
	Price       float64     `json:"price"`
	PriceMax    float64     `json:"priceMax"`
	Currency    string      `json:"currency"`
	Status      string      `json:"status"`
	Featured    bool        `json:"featured"`
	Attributes  []Attribute `json:"attributes,omitempty"`
	Options     []Option    `json:"options,omitempty"`
	// SearchText: giá trị các thuộc tính đánh dấu searchable (tác giả, ISBN...), để tìm theo từ khoá.
	SearchText string    `json:"searchText,omitempty"`
	UpdatedAt  time.Time `json:"updatedAt"`
}

// Attribute là thuộc tính lọc được (facet); Number có khi thuộc tính kiểu số.
type Attribute struct {
	Code   string   `json:"code"`
	Name   string   `json:"name"`
	Value  string   `json:"value"`
	Number *float64 `json:"number,omitempty"`
}

// Option là một giá trị trục variant có ít nhất một variant đang bán (Color=Black).
type Option struct {
	Name  string `json:"name"`
	Value string `json:"value"`
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
	BrandIDs   []string
	// Attributes: code thuộc tính -> các giá trị chấp nhận (OR trong một thuộc tính, AND giữa các thuộc tính).
	Attributes map[string][]string
	// Options: tên trục -> các giá trị chấp nhận (cùng quy tắc với Attributes).
	Options  map[string][]string
	Currency string
	MinPrice float64
	MaxPrice float64
	Sort     Sort
	Page     int // từ 0
	Size     int
}

// Giới hạn bộ lọc để một URL không dựng ra truy vấn khổng lồ.
const (
	maxFilterKeys   = 20
	maxFilterValues = 50
)

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
	// Giá của các sản phẩm khác currency không so được với nhau: lọc theo giá phải nói rõ currency.
	q.Currency = strings.ToUpper(strings.TrimSpace(q.Currency))
	if (q.MinPrice > 0 || q.MaxPrice > 0) && q.Currency == "" {
		return q, apperr.Invalid("currency is required when filtering by price")
	}
	if len(q.BrandIDs) > maxFilterValues || len(q.Attributes)+len(q.Options) > maxFilterKeys {
		return q, apperr.Invalid("too many filters")
	}
	for _, m := range []map[string][]string{q.Attributes, q.Options} {
		for k, vs := range m {
			if k == "" || len(vs) == 0 || len(vs) > maxFilterValues {
				return q, apperr.Invalid("invalid filter %q", k)
			}
		}
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

// Bucket là một giá trị trong facet kèm số sản phẩm.
type Bucket struct {
	Value string `json:"value"`
	Count int64  `json:"count"`
}

// FacetGroup là facet của một thuộc tính (Key = code) hoặc một trục variant (Key = tên trục).
type FacetGroup struct {
	Key    string   `json:"key"`
	Values []Bucket `json:"values"`
}

type Result struct {
	Hits       []Hit        `json:"hits"`
	Total      int64        `json:"total"`
	Categories []Facet      `json:"categories"`
	Brands     []Bucket     `json:"brands"`
	Attributes []FacetGroup `json:"attributes"`
	Options    []FacetGroup `json:"options"`
}
