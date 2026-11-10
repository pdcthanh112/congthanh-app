package messaging

import (
	"context"
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"google.golang.org/protobuf/types/known/anypb"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	orderv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/order/v1"
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
