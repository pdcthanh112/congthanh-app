package app

import (
	"context"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/domain"
)

// Reader đọc ngoài transaction.
type Reader interface {
	LatestConsents(ctx context.Context, subject string) (map[domain.Purpose]domain.Consent, error)
	GetRequest(ctx context.Context, id string) (*domain.Request, error)
	RequestsBySubject(ctx context.Context, subject string, limit int) ([]domain.Request, error)
	RequestsByStatus(ctx context.Context, status domain.RequestStatus, limit int) ([]domain.Request, error)
	// StaleOpenRequests: yêu cầu còn mở chưa nhắc processor từ trước mốc olderThan.
	StaleOpenRequests(ctx context.Context, olderThan time.Time, limit int) ([]domain.Request, error)
}

// Tx là các thao tác ghi trong một transaction (dữ liệu + outbox cùng commit).
type Tx interface {
	AppendConsent(ctx context.Context, c domain.Consent) error
	OpenRequestOfType(ctx context.Context, subject string, t domain.RequestType) (bool, error)
	InsertRequest(ctx context.Context, r *domain.Request) error
	LockRequest(ctx context.Context, id string) (*domain.Request, error)
	UpdateRequest(ctx context.Context, r *domain.Request) error
	MarkReminded(ctx context.Context, id string, at time.Time) error
	Emit(ctx context.Context, events ...domain.Event) error
}

type Store interface {
	Reader
	InTx(ctx context.Context, fn func(Tx) error) error
}

type IDs interface{ New() string }

// Identity xoá tài khoản đăng nhập (Keycloak). Là bước cuối của yêu cầu xoá: chỉ chạy khi mọi processor khác đã xong.
type Identity interface {
	// DeleteUser idempotent: tài khoản không còn thì coi như đã xoá.
	DeleteUser(ctx context.Context, subjectID string) error
}
