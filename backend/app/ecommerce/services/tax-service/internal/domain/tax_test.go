package domain

import "testing"

var rates = []TaxRate{
	{ID: 1, TaxClassID: 1, CountryCode: "US", RatePercent: 0},
	{ID: 2, TaxClassID: 1, CountryCode: "US", RegionCode: "CA", RatePercent: 7.25},
	{ID: 3, TaxClassID: 1, CountryCode: "US", RegionCode: "CA", PostalCode: "94105", RatePercent: 8.625},
	{ID: 4, TaxClassID: 1, CountryCode: "US", PostalCode: "10001", RatePercent: 8.875},
	{ID: 5, TaxClassID: 2, CountryCode: "US", RegionCode: "IL", RatePercent: 1},
	{ID: 6, TaxClassID: 1, CountryCode: "US", RegionCode: "IL", RatePercent: 6.25},
}

func TestBestRateMostSpecific(t *testing.T) {
	cases := []struct {
		a    Address
		want int64
		ok   bool
	}{
		{Address{"US", "TX", ""}, 1, true},
		{Address{"US", "CA", "94110"}, 2, true},
		{Address{"US", "CA", "94105"}, 3, true},
		{Address{"US", "NY", "10001"}, 4, true},
		{Address{"CA", "ON", "M5V2T6"}, 0, false},
	}
	for _, c := range cases {
		got, ok := BestRate(rates, 1, c.a)
		if ok != c.ok || got.ID != c.want {
			t.Errorf("%+v: got id=%d ok=%v, want id=%d ok=%v", c.a, got.ID, ok, c.want, c.ok)
		}
	}
}

func TestCalculate(t *testing.T) {
	lines := []Line{
		{LineID: "a", TaxClassID: 1, TaxableMinor: 19999, Currency: "USD"},
		{LineID: "b", TaxClassID: 2, TaxableMinor: 5001, Currency: "USD"},
		{LineID: "c", TaxClassID: 99, TaxableMinor: 1000, Currency: "USD"},
	}
	// Illinois: 6.25% hàng thường, 1% thực phẩm (lớp Reduced).
	out, total, err := Calculate(rates, Address{"US", "IL", ""}, lines)
	if err != nil {
		t.Fatal(err)
	}
	if out[0].TaxMinor != 1250 || out[1].TaxMinor != 50 || out[2].TaxMinor != 0 || total != 1300 {
		t.Fatalf("got %+v total=%d", out, total)
	}
	if _, _, err := Calculate(rates, Address{"US", "", ""}, []Line{{Currency: "USD"}, {Currency: "EUR"}}); err == nil {
		t.Fatal("mixed currency must fail")
	}
}

func TestNormalizeValidate(t *testing.T) {
	a := Address{" us ", "ca", " 94105-1234 "}.Normalize()
	if a != (Address{"US", "CA", "94105"}) {
		t.Fatalf("got %+v", a)
	}
	if b := (Address{"gb", "", "SW1A 1AA"}).Normalize(); b.PostalCode != "SW1A1AA" {
		t.Fatalf("got %+v", b)
	}
	if (TaxRate{TaxClassID: 1, CountryCode: "US", RatePercent: 101}).Validate() == nil {
		t.Fatal("rate > 100 must fail")
	}
}
