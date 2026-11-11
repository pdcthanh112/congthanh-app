package domain

import (
	"testing"
	"time"
)

var now = time.Date(2026, 11, 10, 12, 0, 0, 0, time.UTC)

func ptr[T any](v T) *T { return &v }

func list(code, group string, prio int) PriceList {
	return PriceList{Code: code, Currency: "USD", CustomerGroup: group, Priority: prio, Active: true}
}

func TestResolvePicksLowestAndCompareAt(t *testing.T) {
	cands := []Candidate{
		{List: list("default", "", 0), Base: &Price{Amount: 200_000}},
		{List: list("vip", "VIP", 10), Base: &Price{Amount: 180_000}},
	}
	r, ok := Resolve("p1", "usd", cands, now)
	if !ok || r.Amount != 180_000 || r.CompareAt != 200_000 || r.DiscountPercent != 10 || r.PriceList != "vip" {
		t.Fatalf("%+v", r)
	}
}

func TestResolveSaleWindow(t *testing.T) {
	end := now.Add(time.Hour)
	c := []Candidate{{List: list("default", "", 0), Base: &Price{Amount: 100_000}, Schedules: []Schedule{
		{Kind: KindFlash, Amount: 49_000, StartsAt: now.Add(-time.Minute), EndsAt: &end, Status: Scheduled},
		{Kind: KindSale, Amount: 80_000, StartsAt: now.Add(-time.Hour), Status: Active},
		{Kind: KindSale, Amount: 1_000, StartsAt: now.Add(-time.Hour), Status: Cancelled},
		{Kind: KindSale, Amount: 2_000, StartsAt: now.Add(2 * time.Hour), Status: Scheduled},
	}}}
	r, _ := Resolve("p1", "USD", c, now)
	if r.Amount != 49_000 || r.SaleKind != KindFlash || r.SaleEndsAt == nil || r.DiscountPercent != 51 {
		t.Fatalf("%+v", r)
	}
	// Hết giờ flash sale: còn đợt SALE không thời hạn.
	r, _ = Resolve("p1", "USD", c, end)
	if r.Amount != 80_000 || r.SaleKind != KindSale {
		t.Fatalf("%+v", r)
	}
}

func TestResolveNoPriceAndExplicitCompareAt(t *testing.T) {
	if _, ok := Resolve("p1", "USD", []Candidate{{List: list("default", "", 0)}}, now); ok {
		t.Fatal("no price must not resolve")
	}
	r, _ := Resolve("p1", "USD", []Candidate{{List: list("default", "", 0), Base: &Price{Amount: 90_000, CompareAt: ptr[int64](120_000)}}}, now)
	if r.CompareAt != 120_000 || r.DiscountPercent != 25 {
		t.Fatalf("%+v", r)
	}
}

func TestAppliesTo(t *testing.T) {
	l := list("vip", "VIP", 0)
	if l.AppliesTo("USD", nil, now) || !l.AppliesTo("usd", []string{"vip"}, now) || l.AppliesTo("EUR", []string{"VIP"}, now) {
		t.Fatal("group/currency filter")
	}
	l.EndsAt = ptr(now)
	if l.AppliesTo("USD", []string{"VIP"}, now) {
		t.Fatal("expired list")
	}
}

func TestValidation(t *testing.T) {
	end := now.Add(100 * time.Hour)
	if (Schedule{ProductID: "p", Kind: KindFlash, Amount: 1, StartsAt: now, EndsAt: &end}).Validate(now) == nil {
		t.Fatal("flash longer than 72h must fail")
	}
	if (Schedule{ProductID: "p", Kind: KindSale, Amount: 1, StartsAt: now}).Validate(now) != nil {
		t.Fatal("open-ended sale is allowed")
	}
	if (Price{ProductID: "p", Amount: 10, CompareAt: ptr[int64](5)}).Validate() == nil {
		t.Fatal("compareAt below amount")
	}
	l := PriceList{Code: "Default", Name: "x", Currency: "usd", CustomerGroup: "vip"}
	if l.Normalize() == nil {
		t.Fatal("default list cannot have a group")
	}
	if v, _ := PercentOff(199_000, 15); v != 169_150 {
		t.Fatalf("percent off %d", v)
	}
}
