// Package app: quản lý webhook, tạo lượt giao từ sự kiện nội bộ và chạy worker gửi lại.
package app

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/domain"
)

type Repository interface {
	Create(ctx context.Context, w domain.Webhook) error
	Get(ctx context.Context, id string) (domain.Webhook, error)
	List(ctx context.Context) ([]domain.Webhook, error)
	Update(ctx context.Context, w domain.Webhook) error
	Delete(ctx context.Context, id string) error
	// SubscribersTx trả webhook đang bật theo dõi loại sự kiện (trong transaction của consumer).
	SubscribersTx(ctx context.Context, tx pgx.Tx, event string) ([]domain.Webhook, error)
	EnqueueTx(ctx context.Context, tx pgx.Tx, d domain.Delivery) error
	Enqueue(ctx context.Context, d domain.Delivery) error
	// ClaimDue lấy các lượt giao đến hạn (FOR UPDATE SKIP LOCKED) và dời next_attempt_at thêm lease:
	// nhiều pod chạy song song không gửi trùng, pod chết giữa chừng thì hết lease lượt giao tự quay lại.
	ClaimDue(ctx context.Context, limit int, lease time.Duration) ([]domain.Delivery, error)
	SaveAttempt(ctx context.Context, d domain.Delivery) error
	Deliveries(ctx context.Context, webhookID string, limit int) ([]domain.Delivery, error)
	GetDelivery(ctx context.Context, id string) (domain.Delivery, error)
	Retry(ctx context.Context, id string) error
}

// Result của một lần gửi HTTP.
type Result struct {
	StatusCode int
	Err        error
}

type Sender interface {
	Send(ctx context.Context, w domain.Webhook, d domain.Delivery) Result
}

type IDs interface{ NewID() string }

type Service struct {
	repo      Repository
	sender    Sender
	ids       IDs
	allowHTTP bool
	now       func() time.Time
}

func NewService(r Repository, s Sender, ids IDs, allowHTTP bool) *Service {
	return &Service{repo: r, sender: s, ids: ids, allowHTTP: allowHTTP, now: time.Now}
}

func newSecret() string {
	b := make([]byte, 32)
	_, _ = rand.Read(b)
	return "whsec_" + base64.RawURLEncoding.EncodeToString(b)
}

func dedupe(events []string) []string {
	seen, out := map[string]bool{}, []string{}
	for _, e := range events {
		if e = strings.TrimSpace(e); e != "" && !seen[e] {
			seen[e] = true
			out = append(out, e)
		}
	}
	return out
}

// Create trả webhook kèm secret (lần duy nhất secret được trả về).
func (s *Service) Create(ctx context.Context, w domain.Webhook) (domain.Webhook, error) {
	w.Events = dedupe(w.Events)
	if err := w.Validate(s.allowHTTP); err != nil {
		return w, err
	}
	w.ID, w.CreatedAt = s.ids.NewID(), s.now().UTC()
	if w.Secret == "" {
		w.Secret = newSecret()
	} else if len(w.Secret) < 16 {
		return w, apperr.Invalid("secret must have at least 16 characters")
	}
	return w, s.repo.Create(ctx, w)
}

func (s *Service) Get(ctx context.Context, id string) (domain.Webhook, error) {
	w, err := s.repo.Get(ctx, id)
	w.Secret = ""
	return w, err
}

func (s *Service) List(ctx context.Context) ([]domain.Webhook, error) {
	list, err := s.repo.List(ctx)
	for i := range list {
		list[i].Secret = ""
	}
	return list, err
}

func (s *Service) Update(ctx context.Context, w domain.Webhook) (domain.Webhook, error) {
	cur, err := s.repo.Get(ctx, w.ID)
	if err != nil {
		return w, err
	}
	w.Events = dedupe(w.Events)
	if err := w.Validate(s.allowHTTP); err != nil {
		return w, err
	}
	if w.Secret == "" {
		w.Secret = cur.Secret
	}
	w.CreatedAt = cur.CreatedAt
	if err := s.repo.Update(ctx, w); err != nil {
		return w, err
	}
	w.Secret = ""
	return w, nil
}

func (s *Service) Delete(ctx context.Context, id string) error { return s.repo.Delete(ctx, id) }

// Envelope là body JSON gửi cho bên nhận.
type Envelope struct {
	ID        string          `json:"id"`
	Type      string          `json:"type"`
	CreatedAt time.Time       `json:"createdAt"`
	Data      json.RawMessage `json:"data"`
}

// FanOutTx tạo lượt giao cho mọi webhook theo dõi sự kiện, trong transaction của consumer
// (cùng transaction với processed_events nên không tạo trùng khi Kafka giao lại).
func (s *Service) FanOutTx(ctx context.Context, tx pgx.Tx, eventID, event string, at time.Time, data json.RawMessage) error {
	subs, err := s.repo.SubscribersTx(ctx, tx, event)
	if err != nil || len(subs) == 0 {
		return err
	}
	body, err := json.Marshal(Envelope{ID: eventID, Type: event, CreatedAt: at.UTC(), Data: data})
	if err != nil {
		return err
	}
	now := s.now().UTC()
	for _, w := range subs {
		d := domain.Delivery{ID: s.ids.NewID(), WebhookID: w.ID, EventID: eventID, EventType: event, Payload: body,
			Status: domain.DeliveryPending, NextAttemptAt: now, CreatedAt: now}
		if err := s.repo.EnqueueTx(ctx, tx, d); err != nil {
			return err
		}
	}
	return nil
}

// Ping gửi sự kiện thử ngay (qua hàng đợi, worker gửi trong vài giây).
func (s *Service) Ping(ctx context.Context, webhookID string) (domain.Delivery, error) {
	if _, err := s.repo.Get(ctx, webhookID); err != nil {
		return domain.Delivery{}, err
	}
	now := s.now().UTC()
	id := s.ids.NewID()
	body, _ := json.Marshal(Envelope{ID: id, Type: domain.EventPing, CreatedAt: now, Data: json.RawMessage(`{"message":"pong"}`)})
	d := domain.Delivery{ID: id, WebhookID: webhookID, EventID: id, EventType: domain.EventPing, Payload: body,
		Status: domain.DeliveryPending, NextAttemptAt: now, CreatedAt: now}
	return d, s.repo.Enqueue(ctx, d)
}

func (s *Service) Deliveries(ctx context.Context, webhookID string, limit int) ([]domain.Delivery, error) {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	return s.repo.Deliveries(ctx, webhookID, limit)
}

func (s *Service) Retry(ctx context.Context, deliveryID string) error {
	d, err := s.repo.GetDelivery(ctx, deliveryID)
	if err != nil {
		return err
	}
	if d.Status == domain.DeliverySucceeded {
		return apperr.Conflict("delivery already succeeded")
	}
	return s.repo.Retry(ctx, deliveryID)
}

// DeliverDue gửi một lô lượt giao đến hạn (song song tối đa 8), trả số lượt đã xử lý.
// 2xx = thành công; còn lại thử lại theo backoff, quá MaxAttempts thì DEAD.
// Webhook bị xoá hoặc tắt: lượt giao chuyển DEAD. HTTP gửi ngoài transaction (nhờ lease).
func (s *Service) DeliverDue(ctx context.Context, batch int) (int, error) {
	ds, err := s.repo.ClaimDue(ctx, batch, 2*time.Minute)
	if err != nil || len(ds) == 0 {
		return 0, err
	}
	hooks := map[string]*domain.Webhook{}
	for _, d := range ds {
		if _, ok := hooks[d.WebhookID]; !ok {
			var w *domain.Webhook
			if got, err := s.repo.Get(ctx, d.WebhookID); err == nil {
				w = &got
			}
			hooks[d.WebhookID] = w
		}
	}
	var wg sync.WaitGroup
	sem := make(chan struct{}, 8)
	errs := make([]error, len(ds))
	for i, d := range ds {
		wg.Add(1)
		sem <- struct{}{}
		go func() {
			defer func() { <-sem; wg.Done() }()
			errs[i] = s.repo.SaveAttempt(ctx, s.attempt(ctx, hooks[d.WebhookID], d))
		}()
	}
	wg.Wait()
	return len(ds), errors.Join(errs...)
}

func (s *Service) attempt(ctx context.Context, w *domain.Webhook, d domain.Delivery) domain.Delivery {
	d.Attempts++
	if w == nil || !w.Active {
		msg := "webhook disabled or deleted"
		d.Status, d.LastError = domain.DeliveryDead, &msg
		return d
	}
	res := s.sender.Send(ctx, *w, d)
	now := s.now().UTC()
	code := res.StatusCode
	d.LastStatusCode = &code
	if res.Err == nil && code >= 200 && code < 300 {
		d.Status, d.DeliveredAt, d.LastError = domain.DeliverySucceeded, &now, nil
		return d
	}
	msg := fmt.Sprintf("unexpected status %d", code)
	if res.Err != nil {
		msg = res.Err.Error()
	}
	if len(msg) > 500 {
		msg = msg[:500]
	}
	d.LastError = &msg
	if d.Attempts >= domain.MaxAttempts {
		d.Status = domain.DeliveryDead
	} else {
		d.NextAttemptAt = now.Add(domain.Backoff(d.Attempts))
	}
	return d
}
