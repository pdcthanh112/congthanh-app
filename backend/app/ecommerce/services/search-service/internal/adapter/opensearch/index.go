// Package opensearch là driven adapter Index qua REST API của OpenSearch (net/http, không cần SDK).
package opensearch

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/domain"
)

type Config struct {
	URL      string
	Index    string
	Username string
	Password string
}

type Index struct {
	cfg  Config
	http *http.Client
}

func New(cfg Config) *Index {
	return &Index{cfg: cfg, http: &http.Client{Timeout: 5 * time.Second, Transport: otelhttp.NewTransport(http.DefaultTransport)}}
}

// Mapping: tên sản phẩm phân tích có bỏ dấu (asciifolding) để "ao thun" khớp "Áo thun";
// name.ac dùng edge n-gram cho gợi ý khi đang gõ.
var mapping = map[string]any{
	"settings": map[string]any{
		"analysis": map[string]any{
			"filter": map[string]any{
				"edge_2_20": map[string]any{"type": "edge_ngram", "min_gram": 2, "max_gram": 20},
			},
			"analyzer": map[string]any{
				"folded":       map[string]any{"tokenizer": "standard", "filter": []string{"lowercase", "asciifolding"}},
				"autocomplete": map[string]any{"tokenizer": "standard", "filter": []string{"lowercase", "asciifolding", "edge_2_20"}},
			},
		},
	},
	"mappings": map[string]any{
		"dynamic": "strict",
		"properties": map[string]any{
			"id":   map[string]any{"type": "keyword"},
			"sku":  map[string]any{"type": "keyword"},
			"slug": map[string]any{"type": "keyword"},
			"name": map[string]any{
				"type": "text", "analyzer": "folded",
				"fields": map[string]any{
					"ac":  map[string]any{"type": "text", "analyzer": "autocomplete", "search_analyzer": "folded"},
					"raw": map[string]any{"type": "keyword", "ignore_above": 256},
				},
			},
			"categoryId": map[string]any{"type": "keyword"},
			"price":      map[string]any{"type": "scaled_float", "scaling_factor": 100},
			"currency":   map[string]any{"type": "keyword"},
			"status":     map[string]any{"type": "keyword"},
			"updatedAt":  map[string]any{"type": "date"},
		},
	},
}

// EnsureIndex tạo chỉ mục (kèm mapping) nếu chưa có.
func (x *Index) EnsureIndex(ctx context.Context) error {
	code, _, err := x.do(ctx, http.MethodHead, "/"+x.cfg.Index, nil)
	if err != nil || code == http.StatusOK {
		return err
	}
	code, body, err := x.do(ctx, http.MethodPut, "/"+x.cfg.Index, mapping)
	if err != nil {
		return err
	}
	if code >= 300 && !strings.Contains(string(body), "resource_already_exists_exception") {
		return fmt.Errorf("create index %s: %d %s", x.cfg.Index, code, body)
	}
	return nil
}

func (x *Index) Ping(ctx context.Context) error {
	code, _, err := x.do(ctx, http.MethodGet, "/_cluster/health?timeout=2s", nil)
	if err == nil && code >= 300 {
		err = fmt.Errorf("opensearch health: %d", code)
	}
	return err
}

func versionQS(v int64) string {
	return "?version=" + strconv.FormatInt(v, 10) + "&version_type=external_gte"
}

// Upsert ghi tài liệu với version ngoài: event cũ hơn bản đang có (409) bị bỏ qua.
func (x *Index) Upsert(ctx context.Context, p domain.Product, version int64) error {
	code, body, err := x.do(ctx, http.MethodPut, "/"+x.cfg.Index+"/_doc/"+url.PathEscape(p.ID)+versionQS(version), p)
	if err != nil {
		return err
	}
	if code >= 300 && code != http.StatusConflict {
		return fmt.Errorf("index product %s: %d %s", p.ID, code, body)
	}
	return nil
}

func (x *Index) Delete(ctx context.Context, id string, version int64) error {
	code, body, err := x.do(ctx, http.MethodDelete, "/"+x.cfg.Index+"/_doc/"+url.PathEscape(id)+versionQS(version), nil)
	if err != nil {
		return err
	}
	if code >= 300 && code != http.StatusNotFound && code != http.StatusConflict {
		return fmt.Errorf("delete product %s: %d %s", id, code, body)
	}
	return nil
}

// BuildSearch dựng truy vấn OpenSearch. Lọc danh mục nằm ở post_filter để facet danh mục vẫn đếm
// trên toàn bộ kết quả của từ khoá (người dùng thấy các danh mục khác còn bao nhiêu sản phẩm).
func BuildSearch(q domain.Query) map[string]any {
	var must any = map[string]any{"match_all": map[string]any{}}
	if q.Text != "" {
		must = map[string]any{"multi_match": map[string]any{
			"query": q.Text, "fields": []string{"name^3", "name.ac", "sku^5"},
			"type": "best_fields", "operator": "and", "fuzziness": "AUTO",
		}}
	}
	filters := []any{}
	if q.MinPrice > 0 || q.MaxPrice > 0 {
		r := map[string]any{}
		if q.MinPrice > 0 {
			r["gte"] = q.MinPrice
		}
		if q.MaxPrice > 0 {
			r["lte"] = q.MaxPrice
		}
		filters = append(filters, map[string]any{"range": map[string]any{"price": r}})
	}
	body := map[string]any{
		"query":            map[string]any{"bool": map[string]any{"must": must, "filter": filters}},
		"from":             q.Page * q.Size,
		"size":             q.Size,
		"track_total_hits": true,
		"aggs":             map[string]any{"categories": map[string]any{"terms": map[string]any{"field": "categoryId", "size": 30}}},
	}
	if q.CategoryID != "" {
		body["post_filter"] = map[string]any{"term": map[string]any{"categoryId": q.CategoryID}}
	}
	switch q.Sort {
	case domain.SortPriceAsc:
		body["sort"] = []any{map[string]any{"price": "asc"}, "_score"}
	case domain.SortPriceDesc:
		body["sort"] = []any{map[string]any{"price": "desc"}, "_score"}
	case domain.SortNewest:
		body["sort"] = []any{map[string]any{"updatedAt": "desc"}, "_score"}
	}
	return body
}

type searchResp struct {
	Hits struct {
		Total struct {
			Value int64 `json:"value"`
		} `json:"total"`
		Hits []struct {
			Score  *float64       `json:"_score"`
			Source domain.Product `json:"_source"`
		} `json:"hits"`
	} `json:"hits"`
	Aggregations struct {
		Categories struct {
			Buckets []struct {
				Key      string `json:"key"`
				DocCount int64  `json:"doc_count"`
			} `json:"buckets"`
		} `json:"categories"`
	} `json:"aggregations"`
}

func (x *Index) Search(ctx context.Context, q domain.Query) (domain.Result, error) {
	code, body, err := x.do(ctx, http.MethodPost, "/"+x.cfg.Index+"/_search", BuildSearch(q))
	if err != nil {
		return domain.Result{}, apperr.Unavailable("search engine unavailable")
	}
	if code >= 300 {
		return domain.Result{}, fmt.Errorf("search: %d %s", code, body)
	}
	var r searchResp
	if err := json.Unmarshal(body, &r); err != nil {
		return domain.Result{}, err
	}
	out := domain.Result{Total: r.Hits.Total.Value, Hits: make([]domain.Hit, 0, len(r.Hits.Hits)), Categories: []domain.Facet{}}
	for _, h := range r.Hits.Hits {
		hit := domain.Hit{Product: h.Source}
		if h.Score != nil {
			hit.Score = *h.Score
		}
		out.Hits = append(out.Hits, hit)
	}
	for _, b := range r.Aggregations.Categories.Buckets {
		out.Categories = append(out.Categories, domain.Facet{CategoryID: b.Key, Count: b.DocCount})
	}
	return out, nil
}

// Suggest: tên sản phẩm khớp tiền tố (bỏ dấu), gộp tên trùng.
func (x *Index) Suggest(ctx context.Context, prefix string, limit int) ([]string, error) {
	req := map[string]any{
		"size":     limit,
		"_source":  []string{"name"},
		"query":    map[string]any{"match": map[string]any{"name.ac": map[string]any{"query": prefix, "operator": "and"}}},
		"collapse": map[string]any{"field": "name.raw"},
	}
	code, body, err := x.do(ctx, http.MethodPost, "/"+x.cfg.Index+"/_search", req)
	if err != nil {
		return nil, apperr.Unavailable("search engine unavailable")
	}
	if code >= 300 {
		return nil, fmt.Errorf("suggest: %d %s", code, body)
	}
	var r searchResp
	if err := json.Unmarshal(body, &r); err != nil {
		return nil, err
	}
	out := make([]string, 0, len(r.Hits.Hits))
	for _, h := range r.Hits.Hits {
		out = append(out, h.Source.Name)
	}
	return out, nil
}

func (x *Index) do(ctx context.Context, method, path string, body any) (int, []byte, error) {
	var rd io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return 0, nil, err
		}
		rd = bytes.NewReader(b)
	}
	req, err := http.NewRequestWithContext(ctx, method, strings.TrimRight(x.cfg.URL, "/")+path, rd)
	if err != nil {
		return 0, nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	if x.cfg.Username != "" {
		req.SetBasicAuth(x.cfg.Username, x.cfg.Password)
	}
	res, err := x.http.Do(req)
	if err != nil {
		return 0, nil, err
	}
	defer res.Body.Close()
	b, err := io.ReadAll(io.LimitReader(res.Body, 8<<20))
	return res.StatusCode, b, err
}
