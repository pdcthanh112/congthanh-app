// Package domain: đăng ký webhook, loại sự kiện, chữ ký HMAC và lịch gửi lại.
package domain

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"math"
	"net/url"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
)

const (
	EventOrderCreated          = "order.created"
	EventOrderConfirmed        = "order.confirmed"
	EventOrderCancelled        = "order.cancelled"
	EventProductUpdated        = "product.updated"
	EventProductDeleted        = "product.deleted"
	EventShipmentCreated       = "shipment.created"
	EventShipmentStatusChanged = "shipment.status_changed"
	EventPaymentAuthorized     = "payment.authorized"
	EventPaymentFailed         = "payment.failed"
	EventPaymentRefunded       = "payment.refunded"
	EventPriceChanged          = "price.changed"
	EventStockLow              = "inventory.low_stock"
	EventStockoutRisk = "inventory.stockout_risk"
	EventCheckoutReviewRequested = "checkout.review_requested"
	EventCheckoutReviewDecided   = "checkout.review_decided"
	EventPing                    = "ping"
)

var AllEvents = []string{
	EventOrderCreated, EventOrderConfirmed, EventOrderCancelled,
	EventProductUpdated, EventProductDeleted, EventPriceChanged, EventStockLow, EventStockoutRisk,
	EventShipmentCreated, EventShipmentStatusChanged,
	EventPaymentAuthorized, EventPaymentFailed, EventPaymentRefunded,
	EventCheckoutReviewRequested, EventCheckoutReviewDecided,
}

type Webhook struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	URL       string    `json:"url"`
	Secret    string    `json:"secret,omitempty"`
	Events    []string  `json:"events"`
	Active    bool      `json:"active"`
	CreatedAt time.Time `json:"createdAt"`
}

func (w Webhook) Validate(allowHTTP bool) error {
	if n := strings.TrimSpace(w.Name); n == "" || len(n) > 100 {
		return apperr.Invalid("name is required (max 100 characters)")
	}
	u, err := url.Parse(w.URL)
	if err != nil || u.Host == "" || len(w.URL) > 2048 {
		return apperr.Invalid("url is invalid")
	}
	if u.Scheme != "https" && !(allowHTTP && u.Scheme == "http") {
		return apperr.Invalid("url must use https")
	}
	if u.User != nil {
		return apperr.Invalid("url must not contain credentials")
	}
	if len(w.Events) == 0 {
		return apperr.Invalid("at least one event is required")
	}
	for _, e := range w.Events {
		if !slices.Contains(AllEvents, e) {
			return apperr.Invalid("unknown event %q", e)
		}
	}
	return nil
}

type DeliveryStatus string

const (
	DeliveryPending   DeliveryStatus = "PENDING"
	DeliverySucceeded DeliveryStatus = "SUCCEEDED"
	DeliveryDead      DeliveryStatus = "DEAD"
)

type Delivery struct {
	ID             string         `json:"id"`
	WebhookID      string         `json:"webhookId"`
	EventID        string         `json:"eventId"`
	EventType      string         `json:"eventType"`
	Payload        []byte         `json:"-"`
	Status         DeliveryStatus `json:"status"`
	Attempts       int            `json:"attempts"`
	NextAttemptAt  time.Time      `json:"nextAttemptAt"`
	LastStatusCode *int           `json:"lastStatusCode,omitempty"`
	LastError      *string        `json:"lastError,omitempty"`
	CreatedAt      time.Time      `json:"createdAt"`
	DeliveredAt    *time.Time     `json:"deliveredAt,omitempty"`
}

const MaxAttempts = 10

func Backoff(attempts int) time.Duration {
	d := 30 * time.Second * time.Duration(math.Pow(2, float64(max(attempts-1, 0))))
	return min(d, 6*time.Hour)
}

func Sign(secret string, ts time.Time, body []byte) string {
	m := hmac.New(sha256.New, []byte(secret))
	m.Write([]byte(strconv.FormatInt(ts.Unix(), 10) + "."))
	m.Write(body)
	return "sha256=" + hex.EncodeToString(m.Sum(nil))
}

func SignLegacy(secret string, body []byte) string {
	m := hmac.New(sha256.New, []byte(secret))
	m.Write(body)
	return "sha256=" + hex.EncodeToString(m.Sum(nil))
}
