package domain

import (
	"strings"
	"testing"
	"time"
)

func ptr[T any](v T) *T { return &v }

func valid(now time.Time) Event {
	return Event{
		EventID:     "6f1c1f0e-8a77-4c55-9d1e-2b0f7a1c9e01",
		EventType:   ProductView,
		OccurredAt:  now.Add(-time.Second),
		SessionID:   "s-1",
		AnonymousID: "a-1",
		ProductID:   ptr("p-1"),
	}
}

func TestValidate(t *testing.T) {
	now := time.Date(2026, 11, 10, 12, 0, 0, 0, time.UTC)
	cases := []struct {
		name    string
		mutate  func(e *Event)
		wantErr string
	}{
		{"hợp lệ", func(*Event) {}, ""},
		{"event_id không phải UUID", func(e *Event) { e.EventID = "abc" }, "event_id"},
		{"event_type lạ", func(e *Event) { e.EventType = "scroll" }, "event_type"},
		{"thiếu session", func(e *Event) { e.SessionID = "" }, "session_id"},
		{"lệch giờ", func(e *Event) { e.OccurredAt = now.Add(48 * time.Hour) }, "lệch"},
		{"product_view thiếu product_id", func(e *Event) { e.ProductID = nil }, "product_id"},
		{"search thiếu query", func(e *Event) { e.EventType = Search; e.ProductID = nil }, "search_query"},
		{"purchase thiếu order", func(e *Event) { e.EventType = Purchase }, "order_id"},
		{"giá sai định dạng", func(e *Event) { e.Price = ptr("1,5") }, "price"},
		{"tiền tệ sai", func(e *Event) { e.Currency = ptr("usd") }, "currency"},
		{"quantity 0", func(e *Event) { e.Quantity = ptr(0) }, "quantity"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			e := valid(now)
			tc.mutate(&e)
			err := e.Validate(now)
			if tc.wantErr == "" {
				if err != nil {
					t.Fatalf("không mong lỗi, nhận %v", err)
				}
				return
			}
			if err == nil || !strings.Contains(err.Error(), tc.wantErr) {
				t.Fatalf("mong lỗi chứa %q, nhận %v", tc.wantErr, err)
			}
		})
	}
}
