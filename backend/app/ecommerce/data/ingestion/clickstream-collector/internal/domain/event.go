package domain

import (
	"errors"
	"fmt"
	"regexp"
	"time"
)

type EventType string

const (
	PageView       EventType = "page_view"
	ProductView    EventType = "product_view"
	Search         EventType = "search"
	AddToCart      EventType = "add_to_cart"
	RemoveFromCart EventType = "remove_from_cart"
	BeginCheckout  EventType = "begin_checkout"
	Purchase       EventType = "purchase"
)

var validTypes = map[EventType]bool{
	PageView: true, ProductView: true, Search: true, AddToCart: true,
	RemoveFromCart: true, BeginCheckout: true, Purchase: true,
}

type Event struct {
	EventID     string    `json:"event_id"`
	EventType   EventType `json:"event_type"`
	OccurredAt  time.Time `json:"occurred_at"`
	SessionID   string    `json:"session_id"`
	AnonymousID string    `json:"anonymous_id"`
	CustomerID  *string   `json:"customer_id,omitempty"`
	PageURL     *string   `json:"page_url,omitempty"`
	Referrer    *string   `json:"referrer,omitempty"`
	ProductID   *string   `json:"product_id,omitempty"`
	Quantity    *int      `json:"quantity,omitempty"`
	Price       *string   `json:"price,omitempty"`
	Currency    *string   `json:"currency,omitempty"`
	SearchQuery *string   `json:"search_query,omitempty"`
	OrderID     *string   `json:"order_id,omitempty"`

	ReceivedAt time.Time `json:"received_at"`
	UserAgent  *string   `json:"user_agent,omitempty"`
	IPHash     *string   `json:"ip_hash,omitempty"`
}

var (
	uuidRe     = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)
	priceRe    = regexp.MustCompile(`^[0-9]+(\.[0-9]{1,4})?$`)
	currencyRe = regexp.MustCompile(`^[A-Z]{3}$`)
)

const MaxClockSkew = 24 * time.Hour

func (e *Event) Validate(now time.Time) error {
	var errs []error
	if !uuidRe.MatchString(e.EventID) {
		errs = append(errs, errors.New("event_id phải là UUID"))
	}
	if !validTypes[e.EventType] {
		errs = append(errs, fmt.Errorf("event_type %q không hỗ trợ", e.EventType))
	}
	if e.OccurredAt.IsZero() {
		errs = append(errs, errors.New("thiếu occurred_at"))
	} else if d := e.OccurredAt.Sub(now); d > MaxClockSkew || d < -MaxClockSkew {
		errs = append(errs, errors.New("occurred_at lệch quá 24h so với server"))
	}
	if e.SessionID == "" || len(e.SessionID) > 128 {
		errs = append(errs, errors.New("session_id bắt buộc, tối đa 128 ký tự"))
	}
	if e.AnonymousID == "" || len(e.AnonymousID) > 128 {
		errs = append(errs, errors.New("anonymous_id bắt buộc, tối đa 128 ký tự"))
	}
	if tooLong(e.CustomerID, 64) || tooLong(e.ProductID, 64) || tooLong(e.OrderID, 64) ||
		tooLong(e.PageURL, 2048) || tooLong(e.Referrer, 2048) || tooLong(e.SearchQuery, 256) {
		errs = append(errs, errors.New("trường chuỗi vượt độ dài cho phép"))
	}
	if e.Quantity != nil && *e.Quantity < 1 {
		errs = append(errs, errors.New("quantity phải >= 1"))
	}
	if e.Price != nil && !priceRe.MatchString(*e.Price) {
		errs = append(errs, errors.New("price phải là số thập phân dạng chuỗi"))
	}
	if e.Currency != nil && !currencyRe.MatchString(*e.Currency) {
		errs = append(errs, errors.New("currency phải là mã ISO 4217"))
	}
	switch e.EventType {
	case ProductView, AddToCart, RemoveFromCart:
		if e.ProductID == nil || *e.ProductID == "" {
			errs = append(errs, fmt.Errorf("%s cần product_id", e.EventType))
		}
	case Search:
		if e.SearchQuery == nil || *e.SearchQuery == "" {
			errs = append(errs, errors.New("search cần search_query"))
		}
	case Purchase:
		if e.OrderID == nil || *e.OrderID == "" {
			errs = append(errs, errors.New("purchase cần order_id"))
		}
	}
	return errors.Join(errs...)
}

func tooLong(s *string, n int) bool { return s != nil && len(*s) > n }
