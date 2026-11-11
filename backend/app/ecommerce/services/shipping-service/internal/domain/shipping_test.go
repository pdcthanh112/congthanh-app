package domain

import "testing"

func ptr(v int64) *int64 { return &v }

var rates = []Rate{
	{Carrier: "internal", ServiceCode: "standard", ServiceName: "Std", CountryCode: "US", Currency: "USD", BaseFee: 599, PerKgFee: 150, FreeOver: ptr(5000), DaysMin: 3, DaysMax: 5, Active: true},
	{Carrier: "internal", ServiceCode: "standard", ServiceName: "Std", CountryCode: "US", RegionCode: "NY", Currency: "USD", BaseFee: 499, PerKgFee: 100, FreeOver: ptr(5000), DaysMin: 1, DaysMax: 3, Active: true},
	{Carrier: "internal", ServiceCode: "express", ServiceName: "Exp", CountryCode: "US", Currency: "USD", BaseFee: 1499, PerKgFee: 250, DaysMin: 1, DaysMax: 2, Active: true},
	{Carrier: "ups", ServiceCode: "ground", ServiceName: "UPS Ground", CountryCode: "US", Currency: "USD", BaseFee: 1, Active: false},
}

func TestQuotes(t *testing.T) {
	q, err := Quotes(rates, Address{CountryCode: "us", RegionCode: "ny"}, Parcel{WeightGrams: 2500, DeclaredValue: 2500, Currency: "USD"})
	if err != nil || len(q) != 2 {
		t.Fatalf("%+v %v", q, err)
	}
	// 2.5kg → 3kg: NY 499 + 2×100 = 699; express 1499 + 2×250 = 1999 (cent).
	if q[0].ServiceCode != "standard" || q[0].Fee != 699 || q[0].DaysMax != 3 || q[1].Fee != 1999 {
		t.Fatalf("%+v", q)
	}
	free, _ := Quotes(rates, Address{CountryCode: "US", RegionCode: "TX"}, Parcel{WeightGrams: 500, DeclaredValue: 6000, Currency: "USD"})
	if free[0].Fee != 0 || free[0].DaysMax != 5 {
		t.Fatalf("%+v", free)
	}
	if q, _ := Quotes(rates, Address{CountryCode: "CA"}, Parcel{WeightGrams: 1, Currency: "USD"}); len(q) != 0 {
		t.Fatal("no rates for Canada")
	}
	if _, err := Quotes(rates, Address{CountryCode: "US"}, Parcel{WeightGrams: 0, Currency: "USD"}); err == nil {
		t.Fatal("zero weight must fail")
	}
}

func TestTransitions(t *testing.T) {
	s := &Shipment{Status: StatusPending}
	if err := s.Transition(StatusLabelCreated); err == nil {
		t.Fatal("label needs address")
	}
	s.Address = &Address{CountryCode: "US"}
	for _, to := range []Status{StatusLabelCreated, StatusPickedUp, StatusInTransit, StatusOutForDelivery, StatusFailed, StatusOutForDelivery, StatusDelivered} {
		if err := s.Transition(to); err != nil {
			t.Fatalf("-> %s: %v", to, err)
		}
	}
	if err := s.Transition(StatusCancelled); err == nil || !s.Status.Terminal() {
		t.Fatal("delivered is terminal")
	}
}
