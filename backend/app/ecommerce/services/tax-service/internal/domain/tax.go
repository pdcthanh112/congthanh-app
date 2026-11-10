// Package domain là lõi nghiệp vụ thuế: chọn thuế suất theo địa chỉ và tính thuế từng dòng.
// Không phụ thuộc DB, HTTP hay gRPC.
package domain

import (
	"strings"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/money"
)

type TaxClass struct {
	ID   int64  `json:"id"`
	Name string `json:"name"`
}

type TaxRate struct {
	ID          int64   `json:"id"`
	TaxClassID  int64   `json:"taxClassId"`
	CountryCode string  `json:"countryCode"`
	RegionCode  string  `json:"regionCode"`
	PostalCode  string  `json:"postalCode"`
	RatePercent float64 `json:"ratePercent"`
}

type Address struct {
	CountryCode string `json:"countryCode"`
	RegionCode  string `json:"regionCode"`
	PostalCode  string `json:"postalCode"`
}

// Normalize viết hoa mã quốc gia/tỉnh và bỏ khoảng trắng mã bưu chính.
func (a Address) Normalize() Address {
	return Address{
		CountryCode: strings.ToUpper(strings.TrimSpace(a.CountryCode)),
		RegionCode:  strings.ToUpper(strings.TrimSpace(a.RegionCode)),
		PostalCode:  strings.ReplaceAll(strings.TrimSpace(a.PostalCode), " ", ""),
	}
}

func (a Address) Validate() error {
	if len(a.CountryCode) != 2 {
		return apperr.Invalid("countryCode must be ISO 3166-1 alpha-2")
	}
	return nil
}

func (r TaxRate) Normalize() TaxRate {
	a := Address{r.CountryCode, r.RegionCode, r.PostalCode}.Normalize()
	r.CountryCode, r.RegionCode, r.PostalCode = a.CountryCode, a.RegionCode, a.PostalCode
	return r
}

func (r TaxRate) Validate() error {
	if r.TaxClassID <= 0 {
		return apperr.Invalid("taxClassId is required")
	}
	if len(r.CountryCode) != 2 {
		return apperr.Invalid("countryCode must be ISO 3166-1 alpha-2")
	}
	if r.RatePercent < 0 || r.RatePercent > 100 {
		return apperr.Invalid("ratePercent must be between 0 and 100")
	}
	return nil
}

func ValidateClassName(name string) error {
	if n := strings.TrimSpace(name); n == "" || len(n) > 100 {
		return apperr.Invalid("name is required (max 100 characters)")
	}
	return nil
}

// specificity: -1 = không khớp; số lớn hơn = cụ thể hơn.
func (r TaxRate) specificity(a Address) int {
	if r.CountryCode != a.CountryCode {
		return -1
	}
	score := 0
	if r.RegionCode != "" {
		if r.RegionCode != a.RegionCode {
			return -1
		}
		score++
	}
	if r.PostalCode != "" {
		if r.PostalCode != a.PostalCode {
			return -1
		}
		score += 2
	}
	return score
}

// BestRate chọn thuế suất cụ thể nhất cho một loại thuế tại địa chỉ: mã bưu chính > tỉnh > quốc gia.
func BestRate(rates []TaxRate, classID int64, a Address) (TaxRate, bool) {
	best, bestScore := TaxRate{}, -1
	for _, r := range rates {
		if r.TaxClassID != classID {
			continue
		}
		if s := r.specificity(a); s > bestScore {
			best, bestScore = r, s
		}
	}
	return best, bestScore >= 0
}

type Line struct {
	LineID       string
	TaxClassID   int64
	TaxableMinor int64
	Currency     string
}

type LineTax struct {
	LineID      string
	RatePercent float64
	TaxMinor    int64
	Currency    string
}

// Calculate tính thuế từng dòng (làm tròn từng dòng, half-up) và tổng. Các dòng phải cùng tiền tệ.
func Calculate(rates []TaxRate, a Address, lines []Line) ([]LineTax, int64, error) {
	if len(lines) == 0 {
		return nil, 0, nil
	}
	cur := lines[0].Currency
	out := make([]LineTax, 0, len(lines))
	var total int64
	for _, l := range lines {
		if l.Currency != cur {
			return nil, 0, apperr.Invalid("all lines must use the same currency")
		}
		if l.TaxableMinor < 0 {
			return nil, 0, apperr.Invalid("taxable amount of line %q is negative", l.LineID)
		}
		r, _ := BestRate(rates, l.TaxClassID, a)
		tax := money.PercentOf(l.TaxableMinor, r.RatePercent)
		total += tax
		out = append(out, LineTax{LineID: l.LineID, RatePercent: r.RatePercent, TaxMinor: tax, Currency: cur})
	}
	return out, total, nil
}
