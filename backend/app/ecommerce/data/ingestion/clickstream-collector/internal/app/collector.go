package app

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/data/ingestion/clickstream-collector/internal/domain"
)

type Publisher interface {
	Publish(ctx context.Context, events []domain.Event) error
	Ping(ctx context.Context) error
}

const MaxBatch = 100

type Rejected struct {
	Index int    `json:"index"`
	Error string `json:"error"`
}

type Result struct {
	Accepted int        `json:"accepted"`
	Rejected []Rejected `json:"rejected"`
}

type Client struct {
	UserAgent string
	IP        string
}

type Service struct {
	pub  Publisher
	salt string
	now  func() time.Time
}

func NewService(pub Publisher, ipHashSalt string) *Service {
	return &Service{pub: pub, salt: ipHashSalt, now: time.Now}
}

func (s *Service) Collect(ctx context.Context, events []domain.Event, c Client) (Result, error) {
	now := s.now().UTC()
	res := Result{Rejected: []Rejected{}}
	ok := make([]domain.Event, 0, len(events))
	ipHash := s.hashIP(c.IP)
	for i := range events {
		e := events[i]
		if err := e.Validate(now); err != nil {
			res.Rejected = append(res.Rejected, Rejected{Index: i, Error: err.Error()})
			continue
		}
		e.OccurredAt = e.OccurredAt.UTC()
		e.ReceivedAt = now
		if c.UserAgent != "" {
			ua := truncate(c.UserAgent, 512)
			e.UserAgent = &ua
		}
		e.IPHash = ipHash
		ok = append(ok, e)
	}
	if len(ok) > 0 {
		if err := s.pub.Publish(ctx, ok); err != nil {
			return Result{}, err
		}
	}
	res.Accepted = len(ok)
	return res, nil
}

func (s *Service) Ready(ctx context.Context) error { return s.pub.Ping(ctx) }

func (s *Service) hashIP(ip string) *string {
	if ip == "" {
		return nil
	}
	sum := sha256.Sum256([]byte(s.salt + ip))
	h := hex.EncodeToString(sum[:])
	return &h
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}
