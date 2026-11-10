// Package domain: báo giá phí giao hàng và vòng đời vận đơn (máy trạng thái).
package domain

import (
	"slices"
	"strings"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
)

type Address struct {
	RecipientName string `json:"recipientName"`
	Phone         string `json:"phone"`
	Line1         string `json:"line1"`
	Line2         string `json:"line2,omitempty"`
	Ward          string `json:"ward,omitempty"`
	District      string `json:"district,omitempty"`
	RegionCode    string `json:"regionCode"`
	PostalCode    string `json:"postalCode,omitempty"`
	CountryCode   string `json:"countryCode"`
}

func (a Address) Normalize() Address {
	a.CountryCode = strings.ToUpper(strings.TrimSpace(a.CountryCode))
	a.RegionCode = strings.ToUpper(strings.TrimSpace(a.RegionCode))
	return a
}

type Rate struct {
	ID          int64  `json:"id"`
	Carrier     string `json:"carrier"`
	ServiceCode string `json:"serviceCode"`
	ServiceName string `json:"serviceName"`
	CountryCode string `json:"countryCode"`
	RegionCode  string `json:"regionCode"`
	Currency    string `json:"currency"`
	BaseFee     int64  `json:"baseFeeMinor"`
	PerKgFee    int64  `json:"perKgFeeMinor"`
	FreeOver    *int64 `json:"freeOverMinor,omitempty"`
	DaysMin     int    `json:"daysMin"`
	DaysMax     int    `json:"daysMax"`
	Active      bool   `json:"active"`
}

func (r Rate) Validate() error {
	switch {
	case r.Carrier == "" || r.ServiceCode == "" || r.ServiceName == "":
		return apperr.Invalid("carrier, serviceCode and serviceName are required")
	case len(r.CountryCode) != 2 || len(r.Currency) != 3:
		return apperr.Invalid("countryCode must be ISO 3166-1 alpha-2 and currency ISO 4217")
	case r.BaseFee < 0 || r.PerKgFee < 0 || (r.FreeOver != nil && *r.FreeOver < 0):
		return apperr.Invalid("fees must not be negative")
	case r.DaysMin < 0 || r.DaysMax < r.DaysMin:
		return apperr.Invalid("invalid delivery days")
	}
	return nil
}

type Parcel struct {
	WeightGrams   int
	DeclaredValue int64 // đơn vị nhỏ nhất
	Currency      string
}

type Quote struct {
	Carrier     string `json:"carrier"`
	ServiceCode string `json:"serviceCode"`
	ServiceName string `json:"serviceName"`
	Fee         int64  `json:"feeMinor"`
	Currency    string `json:"currency"`
	DaysMin     int    `json:"daysMin"`
	DaysMax     int    `json:"daysMax"`
}

// Quotes: mỗi (carrier, service) lấy dòng cụ thể nhất (có tỉnh > chỉ quốc gia), cùng tiền tệ với đơn.
// Phí = base + perKg × (số kg làm tròn lên − 1); miễn phí khi giá trị đơn đạt ngưỡng. Sắp xếp theo phí.
func Quotes(rates []Rate, dest Address, p Parcel) ([]Quote, error) {
	dest = dest.Normalize()
	if len(dest.CountryCode) != 2 {
		return nil, apperr.Invalid("destination countryCode is required")
	}
	if p.WeightGrams <= 0 || p.WeightGrams > 50_000 {
		return nil, apperr.Invalid("weight must be between 1 g and 50 kg")
	}
	best := map[string]Rate{}
	for _, r := range rates {
		if !r.Active || r.CountryCode != dest.CountryCode || r.Currency != p.Currency {
			continue
		}
		if r.RegionCode != "" && r.RegionCode != dest.RegionCode {
			continue
		}
		key := r.Carrier + "/" + r.ServiceCode
		if cur, ok := best[key]; !ok || (cur.RegionCode == "" && r.RegionCode != "") {
			best[key] = r
		}
	}
	kg := int64((p.WeightGrams + 999) / 1000)
	out := make([]Quote, 0, len(best))
	for _, r := range best {
		fee := r.BaseFee + r.PerKgFee*(kg-1)
		if r.FreeOver != nil && p.DeclaredValue >= *r.FreeOver {
			fee = 0
		}
		out = append(out, Quote{Carrier: r.Carrier, ServiceCode: r.ServiceCode, ServiceName: r.ServiceName, Fee: fee, Currency: r.Currency, DaysMin: r.DaysMin, DaysMax: r.DaysMax})
	}
	slices.SortFunc(out, func(a, b Quote) int {
		if a.Fee != b.Fee {
			return int(a.Fee - b.Fee)
		}
		return strings.Compare(a.ServiceCode, b.ServiceCode)
	})
	return out, nil
}

type Status string

const (
	StatusPending        Status = "PENDING"
	StatusLabelCreated   Status = "LABEL_CREATED"
	StatusPickedUp       Status = "PICKED_UP"
	StatusInTransit      Status = "IN_TRANSIT"
	StatusOutForDelivery Status = "OUT_FOR_DELIVERY"
	StatusDelivered      Status = "DELIVERED"
	StatusFailed         Status = "FAILED"
	StatusReturned       Status = "RETURNED"
	StatusCancelled      Status = "CANCELLED"
)

var transitions = map[Status][]Status{
	StatusPending:        {StatusLabelCreated, StatusCancelled},
	StatusLabelCreated:   {StatusPickedUp, StatusCancelled},
	StatusPickedUp:       {StatusInTransit},
	StatusInTransit:      {StatusOutForDelivery, StatusFailed},
	StatusOutForDelivery: {StatusDelivered, StatusFailed},
	StatusFailed:         {StatusOutForDelivery, StatusReturned},
}

func (s Status) Terminal() bool {
	return s == StatusDelivered || s == StatusReturned || s == StatusCancelled
}

type Shipment struct {
	ID             string    `json:"id"`
	OrderID        string    `json:"orderId"`
	CustomerID     string    `json:"customerId"`
	Carrier        string    `json:"carrier"`
	ServiceCode    string    `json:"serviceCode"`
	TrackingNumber *string   `json:"trackingNumber,omitempty"`
	Status         Status    `json:"status"`
	Address        *Address  `json:"address,omitempty"`
	CreatedAt      time.Time `json:"createdAt"`
	UpdatedAt      time.Time `json:"updatedAt"`
	Events         []Event   `json:"events,omitempty"`
}

type Event struct {
	Status     Status    `json:"status"`
	Note       string    `json:"note,omitempty"`
	Location   string    `json:"location,omitempty"`
	OccurredAt time.Time `json:"occurredAt"`
}

// Transition kiểm tra bước chuyển trạng thái hợp lệ.
func (s *Shipment) Transition(to Status) error {
	if s.Status == to {
		return apperr.Conflict("shipment is already %s", to)
	}
	if !slices.Contains(transitions[s.Status], to) {
		return apperr.Conflict("cannot change shipment from %s to %s", s.Status, to)
	}
	if to == StatusLabelCreated && s.Address == nil {
		return apperr.Conflict("shipment has no delivery address")
	}
	s.Status = to
	return nil
}
