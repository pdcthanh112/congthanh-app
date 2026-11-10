package delivery

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"strconv"
	"testing"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/domain"
)

func TestPublic(t *testing.T) {
	for ip, want := range map[string]bool{
		"8.8.8.8": true, "2606:4700::1111": true,
		"127.0.0.1": false, "10.1.2.3": false, "192.168.1.1": false, "169.254.169.254": false,
		"100.64.0.1": false, "::1": false, "::ffff:10.0.0.1": false, "fd00::1": false, "0.0.0.0": false,
	} {
		if Public(netip.MustParseAddr(ip)) != want {
			t.Errorf("%s: want %v", ip, want)
		}
	}
}

func TestSendSignsAndBlocks(t *testing.T) {
	var got http.Header
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got = r.Header.Clone()
		w.WriteHeader(http.StatusAccepted)
	}))
	defer srv.Close()
	hook := domain.Webhook{URL: srv.URL, Secret: "s3cret"}
	d := domain.Delivery{ID: "d1", EventID: "e1", EventType: domain.EventOrderCreated, Payload: []byte(`{"id":"e1"}`)}

	res := New(5*time.Second, true).Send(context.Background(), hook, d)
	if res.Err != nil || res.StatusCode != http.StatusAccepted {
		t.Fatalf("%+v", res)
	}
	ts, _ := strconv.ParseInt(got.Get("X-Webhook-Timestamp"), 10, 64)
	if got.Get("X-Webhook-Signature") != domain.Sign("s3cret", time.Unix(ts, 0), d.Payload) ||
		got.Get("X-Hub-Signature-256") != domain.SignLegacy("s3cret", d.Payload) || got.Get("X-Webhook-Event") != "order.created" {
		t.Fatalf("headers: %v", got)
	}

	// httptest chạy trên 127.0.0.1: chế độ prod phải chặn.
	res = New(5*time.Second, false).Send(context.Background(), hook, d)
	if !errors.Is(res.Err, ErrBlockedAddress) {
		t.Fatalf("want blocked, got %+v", res)
	}
}
