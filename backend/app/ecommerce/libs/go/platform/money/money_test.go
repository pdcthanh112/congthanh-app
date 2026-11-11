package money

import "testing"

func TestRoundTrip(t *testing.T) {
	for _, c := range []struct {
		minor int64
		cur   string
	}{{1500, "JPY"}, {1999, "USD"}, {-250, "USD"}, {0, "EUR"}} {
		if got := ToMinor(FromMinor(c.minor, c.cur)); got != c.minor {
			t.Fatalf("%v: got %d", c, got)
		}
	}
	m := FromMinor(1999, "USD")
	if m.Units != 19 || m.Nanos != 990_000_000 {
		t.Fatalf("got %v", m)
	}
}

func TestPercentOf(t *testing.T) {
	cases := []struct {
		minor int64
		pct   float64
		want  int64
	}{{199000, 10, 19900}, {1999, 8.875, 177}, {15, 10, 2}, {14, 10, 1}, {100000, 0, 0}}
	for _, c := range cases {
		if got := PercentOf(c.minor, c.pct); got != c.want {
			t.Fatalf("PercentOf(%d, %v) = %d, want %d", c.minor, c.pct, got, c.want)
		}
	}
}
