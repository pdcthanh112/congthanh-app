package opensearch

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/domain"
)

func TestBuildSearch(t *testing.T) {
	b := BuildSearch(domain.Query{Text: "ao thun", CategoryID: "c1", MinPrice: 100, Sort: domain.SortPriceAsc, Page: 2, Size: 10})
	js, _ := json.Marshal(b)
	s := string(js)
	for _, want := range []string{`"from":20`, `"post_filter":{"term":{"categoryId":"c1"}}`, `"gte":100`, `"price":"asc"`, `"fuzziness":"AUTO"`} {
		if !strings.Contains(s, want) {
			t.Errorf("missing %s in %s", want, s)
		}
	}
	if strings.Contains(s, `"lte"`) {
		t.Error("lte must be omitted when maxPrice = 0")
	}
	if js, _ := json.Marshal(BuildSearch(domain.Query{Size: 5})); !strings.Contains(string(js), "match_all") {
		t.Error("empty text must match all")
	}
}

// Fake OpenSearch: kiểm tra version ngoài và giải mã kết quả.
func TestIndexAgainstFake(t *testing.T) {
	var mu sync.Mutex
	versions := map[string]string{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		defer mu.Unlock()
		switch {
		case r.Method == http.MethodHead:
			w.WriteHeader(http.StatusNotFound)
		case r.Method == http.MethodPut && r.URL.Path == "/products":
			body, _ := io.ReadAll(r.Body)
			if !strings.Contains(string(body), "asciifolding") {
				t.Error("mapping not sent")
			}
		case r.Method == http.MethodPut:
			v := r.URL.Query().Get("version")
			if old, ok := versions[r.URL.Path]; ok && old > v {
				w.WriteHeader(http.StatusConflict)
				return
			}
			versions[r.URL.Path] = v
			w.WriteHeader(http.StatusCreated)
		case r.Method == http.MethodDelete:
			w.WriteHeader(http.StatusNotFound)
		case strings.HasSuffix(r.URL.Path, "/_search"):
			_, _ = w.Write([]byte(`{"hits":{"total":{"value":1},"hits":[{"_score":1.5,"_source":{"id":"p1","name":"Áo thun","price":199000,"currency":"VND"}}]},
				"aggregations":{"categories":{"buckets":[{"key":"c1","doc_count":1}]}}}`))
		}
	}))
	defer srv.Close()
	x := New(Config{URL: srv.URL, Index: "products"})
	ctx := context.Background()
	if err := x.EnsureIndex(ctx); err != nil {
		t.Fatal(err)
	}
	if err := x.Upsert(ctx, domain.Product{ID: "p1"}, 200); err != nil {
		t.Fatal(err)
	}
	if err := x.Upsert(ctx, domain.Product{ID: "p1"}, 100); err != nil {
		t.Fatalf("stale event must be ignored, got %v", err)
	}
	if err := x.Delete(ctx, "missing", 1); err != nil {
		t.Fatalf("delete missing must be ok, got %v", err)
	}
	res, err := x.Search(ctx, domain.Query{Text: "ao", Size: 10})
	if err != nil || res.Total != 1 || res.Hits[0].Name != "Áo thun" || res.Hits[0].Score != 1.5 || res.Categories[0].Count != 1 {
		t.Fatalf("%+v %v", res, err)
	}
	sug, err := x.Suggest(ctx, "ao", 5)
	if err != nil || len(sug) != 1 {
		t.Fatalf("%v %v", sug, err)
	}
}
