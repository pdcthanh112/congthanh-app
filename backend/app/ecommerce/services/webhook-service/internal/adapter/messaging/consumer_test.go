package messaging

import (
	"context"
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/anypb"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	fraudv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/fraud/v1"
	orderv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/order/v1"
	paymentv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/payment/v1"
	streamv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/stream/v1"
)

type rec struct{ event, data string }

func (r *rec) FanOutTx(_ context.Context, _ pgx.Tx, _, event string, _ time.Time, data json.RawMessage) error {
	r.event, r.data = event, string(data)
	return nil
}

func TestHandlerMapsAndScrubs(t *testing.T) {
	r := &rec{}
	p, _ := anypb.New(&orderv1.OrderPlaced{OrderId: "o1", CustomerId: "c1", CheckoutWorkflowId: "wf-secret"})
	if err := Handler(r)(context.Background(), nil, &commonv1.EventEnvelope{Id: "e1", Type: name(&orderv1.OrderPlaced{}), Payload: p}); err != nil {
		t.Fatal(err)
	}
	if r.event != "order.created" || !strings.Contains(r.data, `"orderId":"o1"`) || strings.Contains(r.data, "wf-secret") {
		t.Fatalf("%+v", r)
	}
	r2 := &rec{}
	_ = Handler(r2)(context.Background(), nil, &commonv1.EventEnvelope{Type: "ecommerce.cart.v1.Something"})
	if r2.event != "" {
		t.Fatal("unknown events must be ignored")
	}
}

func TestHandlerNewEvents(t *testing.T) {
	cases := []struct {
		msg   proto.Message
		event string
		has   string
		not   string
	}{
		{&paymentv1.PaymentRefunded{PaymentId: "p1", OrderId: "o1"}, "payment.refunded", `"paymentId":"p1"`, ""},
		{&fraudv1.FraudReviewRequested{CheckoutId: "ck1", Score: 60, Reasons: []string{"VELOCITY_CARD"}}, "checkout.review_requested", `"checkoutId":"ck1"`, "VELOCITY"},
		{&fraudv1.FraudReviewDecided{CheckoutId: "ck1", Decision: "APPROVE"}, "checkout.review_decided", `"decision":"APPROVE"`, ""},
		{&streamv1.StockCover{ProductId: "p1", Available: 3, AtRisk: true, RiskChanged: true}, "inventory.stockout_risk", `"productId":"p1"`, "riskChanged"},
	}
	for _, c := range cases {
		r := &rec{}
		p, _ := anypb.New(c.msg)
		if err := Handler(r)(context.Background(), nil, &commonv1.EventEnvelope{Id: "e", Type: name(c.msg), Payload: p}); err != nil {
			t.Fatal(err)
		}
		if r.event != c.event || !strings.Contains(r.data, c.has) || (c.not != "" && strings.Contains(r.data, c.not)) {
			t.Fatalf("%s: %+v", c.event, r)
		}
	}
}

func TestStockCoverOnlyPublishedWhenTurningRisky(t *testing.T) {
	for _, m := range []*streamv1.StockCover{
		{ProductId: "p1", AtRisk: true, RiskChanged: false}, // vẫn rủi ro: đã báo trước đó
		{ProductId: "p1", AtRisk: false, RiskChanged: true}, // hết rủi ro
		{ProductId: "p1"},
	} {
		r := &rec{}
		p, _ := anypb.New(m)
		if err := Handler(r)(context.Background(), nil, &commonv1.EventEnvelope{Id: "e", Type: name(m), Payload: p}); err != nil {
			t.Fatal(err)
		}
		if r.event != "" {
			t.Fatalf("unexpected publish for %+v: %+v", m, r)
		}
	}
}
