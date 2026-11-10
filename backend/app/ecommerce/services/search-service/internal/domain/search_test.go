package domain

import "testing"

func TestQueryNormalize(t *testing.T) {
	q, err := Query{Text: "  áo  "}.Normalize()
	if err != nil || q.Text != "áo" || q.Size != 20 || q.Sort != SortRelevance {
		t.Fatalf("%+v %v", q, err)
	}
	if q, _ := (Query{Size: 1000}).Normalize(); q.Size != MaxSize {
		t.Fatalf("size cap: %d", q.Size)
	}
	for _, bad := range []Query{{Sort: "foo"}, {MinPrice: 10, MaxPrice: 5}, {Page: 500, Size: 100}} {
		if _, err := bad.Normalize(); err == nil {
			t.Fatalf("%+v must fail", bad)
		}
	}
}

func TestSearchable(t *testing.T) {
	if !(Product{Status: "active"}).Searchable() || (Product{Status: "INACTIVE"}).Searchable() {
		t.Fatal("status mapping")
	}
}
