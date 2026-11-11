package http

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/data/ingestion/clickstream-collector/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/data/ingestion/clickstream-collector/internal/domain"
)

type fakePub struct {
	got []domain.Event
	err error
}

func (f *fakePub) Publish(_ context.Context, ev []domain.Event) error {
	f.got = append(f.got, ev...)
	return f.err
}
func (f *fakePub) Ping(context.Context) error { return nil }

func newRouter(pub *fakePub) chi.Router {
	r := chi.NewRouter()
	New(app.NewService(pub, "salt"), []string{"http://localhost:3000"}).Mount(r)
	return r
}

func body(occurred time.Time) string {
	ts := occurred.UTC().Format(time.RFC3339)
	return `{"events":[
	  {"event_id":"6f1c1f0e-8a77-4c55-9d1e-2b0f7a1c9e01","event_type":"add_to_cart","occurred_at":"` + ts + `",
	   "session_id":"s-1","anonymous_id":"a-1","product_id":"p-1","quantity":2,"price":"19.99","currency":"USD"},
	  {"event_id":"bad","event_type":"page_view","occurred_at":"` + ts + `","session_id":"s-1","anonymous_id":"a-1"}
	]}`
}

func TestCollectAcceptsValidAndRejectsInvalid(t *testing.T) {
	pub := &fakePub{}
	req := httptest.NewRequest(http.MethodPost, "/v1/events", strings.NewReader(body(time.Now())))
	req.Header.Set("User-Agent", "test-agent")
	req.Header.Set("Origin", "http://localhost:3000")
	rec := httptest.NewRecorder()
	newRouter(pub).ServeHTTP(rec, req)

	if rec.Code != http.StatusAccepted {
		t.Fatalf("status = %d, body = %s", rec.Code, rec.Body)
	}
	var res app.Result
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatal(err)
	}
	if res.Accepted != 1 || len(res.Rejected) != 1 || res.Rejected[0].Index != 1 {
		t.Fatalf("kết quả sai: %+v", res)
	}
	if len(pub.got) != 1 || pub.got[0].UserAgent == nil || *pub.got[0].UserAgent != "test-agent" ||
		pub.got[0].IPHash == nil || pub.got[0].ReceivedAt.IsZero() {
		t.Fatalf("event chưa được gắn thông tin server: %+v", pub.got)
	}
	if rec.Header().Get("Access-Control-Allow-Origin") != "http://localhost:3000" {
		t.Fatal("thiếu header CORS cho origin hợp lệ")
	}
}

func TestCollectRejectsUnknownFieldsAndEmptyBatch(t *testing.T) {
	for _, b := range []string{`{"events":[]}`, `{"events":[{"foo":1}]}`, `not json`} {
		rec := httptest.NewRecorder()
		newRouter(&fakePub{}).ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/v1/events", strings.NewReader(b)))
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("%s: status = %d", b, rec.Code)
		}
	}
}

func TestCollectReturns503WhenKafkaFails(t *testing.T) {
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/v1/events", strings.NewReader(body(time.Now())))
	newRouter(&fakePub{err: errors.New("broker down")}).ServeHTTP(rec, req)
	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d", rec.Code)
	}
}
