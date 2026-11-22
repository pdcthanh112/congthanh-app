package elasticsearch

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
	b := BuildSearch(domain.Query{Text: "ao thun", CategoryID: "c1", MinPrice: 100, Currency: "USD", Sort: domain.SortPriceAsc, Page: 2, Size: 10,
		BrandIDs: []string{"7"}, Attributes: map[string][]string{"ram": {"8 GB", "16 GB"}}, Options: map[string][]string{"Color": {"Black"}}})
	js, _ := json.Marshal(b)
	s := string(js)
	for _, want := range []string{`"from":20`, `"post_filter":{"term":{"categoryIds":"c1"}}`, `"priceMax":{"gte":100}`, `"price":"asc"`,
		`"fuzziness":"AUTO"`, `{"term":{"currency":"USD"}}`, `{"terms":{"brandId":["7"]}}`,
		`{"nested":{"path":"attributes","query":{"bool":{"filter":[{"term":{"attributes.code":"ram"}},{"terms":{"attributes.value":["8 GB","16 GB"]}}]}}}}`,
		`{"nested":{"path":"options","query":{"bool":{"filter":[{"term":{"options.name":"Color"}},{"terms":{"options.value":["Black"]}}]}}}}`} {
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

// Fake Elasticsearch: kiểm tra version ngoài và giải mã kết quả.
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
			_, _ = w.Write([]byte(`{"hits":{"total":{"value":1},"hits":[{"_score":1.5,"_source":{"id":"p1","name":"Cotton T-Shirt","price":19.99,"currency":"USD"}}]},
				"aggregations":{"categories":{"buckets":[{"key":"c1","doc_count":1}]},"brands":{"buckets":[{"key":"7","doc_count":1}]},
				"attributes":{"doc_count":2,"keys":{"buckets":[{"key":"ram","doc_count":2,"values":{"buckets":[{"key":"8 GB","doc_count":1}]}}]}},
				"options":{"doc_count":1,"keys":{"buckets":[{"key":"Color","doc_count":1,"values":{"buckets":[{"key":"Black","doc_count":1}]}}]}}}}`))
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
	if err != nil || res.Total != 1 || res.Hits[0].Name != "Cotton T-Shirt" || res.Hits[0].Score != 1.5 || res.Categories[0].Count != 1 ||
		res.Brands[0].Value != "7" || res.Attributes[0].Key != "ram" || res.Attributes[0].Values[0].Value != "8 GB" ||
		res.Options[0].Values[0].Count != 1 {
		t.Fatalf("%+v %v", res, err)
	}
	sug, err := x.Suggest(ctx, "ao", 5)
	if err != nil || len(sug) != 1 {
		t.Fatalf("%v %v", sug, err)
	}
}

// Elastic Cloud: API key được gửi dạng "Authorization: ApiKey ..." và ưu tiên hơn user/password.
func TestAPIKeyAuth(t *testing.T) {
	var got string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got = r.Header.Get("Authorization")
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()
	x := New(Config{URL: srv.URL, Index: "products", Username: "u", Password: "p", APIKey: "aWQ6a2V5"})
	if err := x.EnsureIndex(context.Background()); err != nil {
		t.Fatal(err)
	}
	if got != "ApiKey aWQ6a2V5" {
		t.Fatalf("Authorization = %q", got)
	}
}
