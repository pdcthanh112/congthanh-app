package domain

import "testing"

func ptr(v int64) *int64 { return &v }

var rates = []Rate{
	{Carrier: "internal", ServiceCode: "standard", ServiceName: "Std", CountryCode: "VN", Currency: "VND", BaseFee: 30000, PerKgFee: 5000, FreeOver: ptr(500000), DaysMin: 3, DaysMax: 5, Active: true},
	{Carrier: "internal", ServiceCode: "standard", ServiceName: "Std", CountryCode: "VN", RegionCode: "HCM", Currency: "VND", BaseFee: 20000, PerKgFee: 4000, FreeOver: ptr(500000), DaysMin: 1, DaysMax: 2, Active: true},
	{Carrier: "internal", ServiceCode: "express", ServiceName: "Exp", CountryCode: "VN", Currency: "VND", BaseFee: 50000, PerKgFee: 8000, DaysMin: 1, DaysMax: 2, Active: true},
	{Carrier: "ghn", ServiceCode: "standard", ServiceName: "GHN", CountryCode: "VN", Currency: "VND", BaseFee: 1, Active: false},
}

func TestQuotes(t *testing.T) {
	q, err := Quotes(rates, Address{CountryCode: "vn", RegionCode: "hcm"}, Parcel{WeightGrams: 2500, DeclaredValue: 100000, Currency: "VND"})
	if err != nil || len(q) != 2 {
		t.Fatalf("%+v %v", q, err)
	}
	// 2.5kg → 3kg: HCM 20000 + 2×4000 = 28000; express 50000 + 2×8000 = 66000.
	if q[0].ServiceCode != "standard" || q[0].Fee != 28000 || q[0].DaysMax != 2 || q[1].Fee != 66000 {
		t.Fatalf("%+v", q)
	}
	free, _ := Quotes(rates, Address{CountryCode: "VN", RegionCode: "DN"}, Parcel{WeightGrams: 500, DeclaredValue: 600000, Currency: "VND"})
	if free[0].Fee != 0 || free[0].DaysMax != 5 {
		t.Fatalf("%+v", free)
	}
	if q, _ := Quotes(rates, Address{CountryCode: "US"}, Parcel{WeightGrams: 1, Currency: "VND"}); len(q) != 0 {
		t.Fatal("no rates for US")
	}
	if _, err := Quotes(rates, Address{CountryCode: "VN"}, Parcel{WeightGrams: 0, Currency: "VND"}); err == nil {
		t.Fatal("zero weight must fail")
	}
}

func TestTransitions(t *testing.T) {
	s := &Shipment{Status: StatusPending}
	if err := s.Transition(StatusLabelCreated); err == nil {
		t.Fatal("label needs address")
	}
	s.Address = &Address{CountryCode: "VN"}
	for _, to := range []Status{StatusLabelCreated, StatusPickedUp, StatusInTransit, StatusOutForDelivery, StatusFailed, StatusOutForDelivery, StatusDelivered} {
		if err := s.Transition(to); err != nil {
			t.Fatalf("-> %s: %v", to, err)
		}
	}
	if err := s.Transition(StatusCancelled); err == nil || !s.Status.Terminal() {
		t.Fatal("delivered is terminal")
	}
}
