package domain

import "testing"

var rates = []TaxRate{
	{ID: 1, TaxClassID: 1, CountryCode: "VN", RatePercent: 10},
	{ID: 2, TaxClassID: 1, CountryCode: "VN", RegionCode: "HCM", RatePercent: 8},
	{ID: 3, TaxClassID: 1, CountryCode: "VN", RegionCode: "HCM", PostalCode: "700000", RatePercent: 7},
	{ID: 4, TaxClassID: 1, CountryCode: "VN", PostalCode: "100000", RatePercent: 6},
	{ID: 5, TaxClassID: 2, CountryCode: "VN", RatePercent: 5},
}

func TestBestRateMostSpecific(t *testing.T) {
	cases := []struct {
		a    Address
		want int64
		ok   bool
	}{
		{Address{"VN", "HN", ""}, 1, true},
		{Address{"VN", "HCM", "711111"}, 2, true},
		{Address{"VN", "HCM", "700000"}, 3, true},
		{Address{"VN", "HN", "100000"}, 4, true},
		{Address{"US", "CA", "94105"}, 0, false},
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
		{LineID: "a", TaxClassID: 1, TaxableMinor: 199000, Currency: "VND"},
		{LineID: "b", TaxClassID: 2, TaxableMinor: 50001, Currency: "VND"},
		{LineID: "c", TaxClassID: 99, TaxableMinor: 1000, Currency: "VND"},
	}
	out, total, err := Calculate(rates, Address{"VN", "HN", ""}, lines)
	if err != nil {
		t.Fatal(err)
	}
	if out[0].TaxMinor != 19900 || out[1].TaxMinor != 2500 || out[2].TaxMinor != 0 || total != 22400 {
		t.Fatalf("got %+v total=%d", out, total)
	}
	if _, _, err := Calculate(rates, Address{"VN", "", ""}, []Line{{Currency: "VND"}, {Currency: "USD"}}); err == nil {
		t.Fatal("mixed currency must fail")
	}
}

func TestNormalizeValidate(t *testing.T) {
	a := Address{" vn ", "hcm", "700 000"}.Normalize()
	if a != (Address{"VN", "HCM", "700000"}) {
		t.Fatalf("got %+v", a)
	}
	if (TaxRate{TaxClassID: 1, CountryCode: "VN", RatePercent: 101}).Validate() == nil {
		t.Fatal("rate > 100 must fail")
	}
}
