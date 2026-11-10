package domain

import (
	"testing"
	"time"
)

func TestValidate(t *testing.T) {
	ok := Webhook{Name: "ERP", URL: "https://erp.example.com/hook", Events: []string{EventOrderCreated}}
	if err := ok.Validate(false); err != nil {
		t.Fatal(err)
	}
	for _, w := range []Webhook{
		{Name: "x", URL: "http://erp.example.com", Events: []string{EventOrderCreated}},
		{Name: "x", URL: "https://u:p@erp.example.com", Events: []string{EventOrderCreated}},
		{Name: "x", URL: "https://erp.example.com", Events: []string{"order.exploded"}},
		{Name: "", URL: "https://erp.example.com", Events: []string{EventOrderCreated}},
	} {
		if w.Validate(false) == nil {
			t.Errorf("%+v must fail", w)
		}
	}
	if (Webhook{Name: "dev", URL: "http://localhost:8080", Events: AllEvents}).Validate(true) != nil {
		t.Fatal("http allowed in dev")
	}
}

func TestBackoff(t *testing.T) {
	if Backoff(1) != 30*time.Second || Backoff(2) != time.Minute || Backoff(20) != 6*time.Hour {
		t.Fatal(Backoff(1), Backoff(2), Backoff(20))
	}
}

func TestSign(t *testing.T) {
	ts := time.Unix(1700000000, 0)
	a, b := Sign("s", ts, []byte(`{}`)), Sign("s", ts.Add(time.Second), []byte(`{}`))
	if a == b || len(a) != len("sha256=")+64 {
		t.Fatal("timestamp must be part of the signature")
	}
	// Vector tham chiếu: echo -n '{"a":1}' | openssl dgst -sha256 -hmac secret
	if got := SignLegacy("secret", []byte(`{"a":1}`)); got != "sha256=aa9e2e3575f5d7098b6caccd790888c36d5fdb63342a73bada2d6a51747a8494" {
		t.Fatal(got)
	}
}
