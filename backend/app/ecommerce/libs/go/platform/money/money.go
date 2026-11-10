// Package money đổi giữa ecommerce.common.v1.Money (units + nanos) và số tiền đơn vị nhỏ nhất (int64),
// để tính toán không dùng float.
package money

import (
	"math"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
)

// MinorDigits: số chữ số thập phân của tiền tệ (VND, JPY, KRW = 0; còn lại 2).
func MinorDigits(currency string) int {
	switch currency {
	case "VND", "JPY", "KRW":
		return 0
	}
	return 2
}

// ToMinor: Money → đơn vị nhỏ nhất (VND: đồng, USD: cent), làm tròn half-up theo trị tuyệt đối.
func ToMinor(m *commonv1.Money) int64 {
	if m == nil {
		return 0
	}
	d := MinorDigits(m.GetCurrencyCode())
	scale := int64(math.Pow10(d))
	nanoPerMinor := int64(1_000_000_000) / scale
	n := int64(m.GetNanos())
	r := n / nanoPerMinor
	if rem := n % nanoPerMinor; rem*2 >= nanoPerMinor {
		r++
	} else if rem*2 <= -nanoPerMinor {
		r--
	}
	return m.GetUnits()*scale + r
}

// FromMinor: đơn vị nhỏ nhất → Money.
func FromMinor(minor int64, currency string) *commonv1.Money {
	scale := int64(math.Pow10(MinorDigits(currency)))
	nanoPerMinor := int64(1_000_000_000) / scale
	return &commonv1.Money{CurrencyCode: currency, Units: minor / scale, Nanos: int32((minor % scale) * nanoPerMinor)}
}

// PercentOf: phần trăm (ví dụ 10.0) của số tiền đơn vị nhỏ nhất, làm tròn half-up.
// Dùng phần nghìn của phần trăm (3 chữ số thập phân) để tránh sai số float.
func PercentOf(minor int64, percent float64) int64 {
	milli := int64(math.Round(percent * 1000)) // 10.5% → 10500
	num := minor * milli
	q, rem := num/100_000, num%100_000
	if rem*2 >= 100_000 {
		q++
	} else if rem*2 <= -100_000 {
		q--
	}
	return q
}
