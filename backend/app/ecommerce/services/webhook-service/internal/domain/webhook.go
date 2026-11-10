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

// Loại sự kiện công khai cho bên thứ ba (tách khỏi tên message Protobuf nội bộ).
const (
	EventOrderCreated          = "order.created"
	EventOrderConfirmed        = "order.confirmed"
	EventOrderCancelled        = "order.cancelled"
	EventProductUpdated        = "product.updated"
	EventProductDeleted        = "product.deleted"
	EventShipmentStatusChanged = "shipment.status_changed"
	EventPing                  = "ping"
)

var AllEvents = []string{EventOrderCreated, EventOrderConfirmed, EventOrderCancelled, EventProductUpdated, EventProductDeleted, EventShipmentStatusChanged}

type Webhook struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	URL       string    `json:"url"`
	Secret    string    `json:"secret,omitempty"`
	Events    []string  `json:"events"`
	Active    bool      `json:"active"`
	CreatedAt time.Time `json:"createdAt"`
}

// Validate: URL phải https (http chỉ khi allowHTTP, dùng cho dev), sự kiện phải thuộc danh sách.
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

// Backoff: 30s, 1m, 2m, 4m ... tối đa 6 giờ giữa hai lần thử (tổng ~1,5 ngày cho 10 lần).
func Backoff(attempts int) time.Duration {
	d := 30 * time.Second * time.Duration(math.Pow(2, float64(max(attempts-1, 0))))
	return min(d, 6*time.Hour)
}

// Sign: chữ ký kiểu Standard Webhooks, chống phát lại: HMAC-SHA256(secret, "<timestamp>.<body>").
// Bên nhận kiểm X-Webhook-Signature và từ chối timestamp lệch quá 5 phút.
func Sign(secret string, ts time.Time, body []byte) string {
	m := hmac.New(sha256.New, []byte(secret))
	m.Write([]byte(strconv.FormatInt(ts.Unix(), 10) + "."))
	m.Write(body)
	return "sha256=" + hex.EncodeToString(m.Sum(nil))
}

// SignLegacy: X-Hub-Signature-256 như bản Java (HMAC trên body), giữ cho tích hợp cũ.
func SignLegacy(secret string, body []byte) string {
	m := hmac.New(sha256.New, []byte(secret))
	m.Write(body)
	return "sha256=" + hex.EncodeToString(m.Sum(nil))
}
