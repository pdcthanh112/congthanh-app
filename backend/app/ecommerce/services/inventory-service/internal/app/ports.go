// Package app chứa các use case của inventory-service. Nó chỉ phụ thuộc domain và các
// port (interface) dưới đây; adapter (Postgres, Redis, Kafka, gRPC, HTTP) hiện thực port.
package app

import (
	"context"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

// UnitOfWork chạy fn trong một transaction; mọi thay đổi và event (outbox) cùng commit.
type UnitOfWork interface {
	Do(ctx context.Context, fn func(ctx context.Context, tx Tx) error) error
}

// Tx là các thao tác ghi trong transaction. Các hàm Lock* khoá dòng (SELECT ... FOR UPDATE).
type Tx interface {
	LockReservation(ctx context.Context, id string) (domain.Reservation, error)
	CreateReservation(ctx context.Context, r domain.Reservation) error
	SaveReservation(ctx context.Context, r domain.Reservation) error
	AddReservationItems(ctx context.Context, reservationID string, items []domain.ReservationItem) error
	LockActiveReservationItems(ctx context.Context, reservationID string, productIDs []string) ([]domain.ReservationItem, error)
	SetReservationItemsStatus(ctx context.Context, reservationID string, stockIDs []int64, status string) error
	CountActiveReservationItems(ctx context.Context, reservationID string) (int, error)

	LockSellableStocks(ctx context.Context, productID string) ([]domain.Stock, error)
	LockStocks(ctx context.Context, ids []int64) (map[int64]domain.Stock, error)
	LockOrCreateStock(ctx context.Context, productID, sku, warehouseID string) (domain.Stock, error)
	LockStocksBySKU(ctx context.Context, sku string) ([]domain.Stock, error)
	SaveStock(ctx context.Context, s domain.Stock) (domain.Stock, error)
	DeleteUnreferencedStocks(ctx context.Context, sku string) (remaining int, err error)

	RecordMovement(ctx context.Context, m domain.Movement) error
	Publish(ctx context.Context, events ...domain.Event) error
}

// Reader là truy vấn chỉ đọc (không cần transaction).
type Reader interface {
	StocksByProduct(ctx context.Context, productIDs []string, warehouseID string) ([]domain.Stock, error)
	StocksBySKU(ctx context.Context, sku string) ([]domain.Stock, error)
	ExpiredReservations(ctx context.Context, limit int) ([]string, error)
}

type WarehouseRepository interface {
	List(ctx context.Context) ([]domain.Warehouse, error)
	Get(ctx context.Context, id string) (domain.Warehouse, error)
	Create(ctx context.Context, w domain.Warehouse) (domain.Warehouse, error)
	Update(ctx context.Context, w domain.Warehouse) (domain.Warehouse, error)
	Delete(ctx context.Context, id string) error
}

// FlashSaleConfig là cấu hình một đợt sale nạp vào Redis.
type FlashSaleConfig struct {
	Quantity         int32
	PerCustomerLimit int32
	OpensAt          time.Time
	AdmitPerSecond   int32 // 0 = không dùng phòng chờ
	AdmitBurst       int32 // số người được vào ngay lúc mở
	TTL              time.Duration
}

type ClaimRequest struct {
	SaleID, ProductID, CustomerID, ClaimID string
	Quantity                               int32
	Deadline, Now                          time.Time
}

// ClaimRelease là kết quả trả một claim.
type ClaimRelease int

const (
	ClaimGone      ClaimRelease = iota // không còn claim: đã trả, hết hạn hoặc sale đã đóng
	ClaimReleased                      // vừa trả suất về sale
	ClaimCommitted                     // đã chốt (đã bán), không trả được
)

// QueueState là vị trí của một khách trong phòng chờ. Rank tính từ 0, -1 = chưa xếp hàng.
type QueueState struct {
	Rank, Admitted, Length int64
	Remaining              int32
	OpensAt                time.Time
	AdmitPerSecond         int32
}

// FlashSaleStore giữ bộ đếm, claim và phòng chờ của flash sale trong Redis; mọi thay đổi là Lua nguyên tử.
type FlashSaleStore interface {
	Load(ctx context.Context, saleID, productID string, cfg FlashSaleConfig) error
	Claim(ctx context.Context, c ClaimRequest) (remaining int32, err error)
	FindClaim(ctx context.Context, claimID string) (saleID, productID string, found bool, err error)
	ConfirmClaim(ctx context.Context, saleID, productID, claimID, customerID string, qty int32) error
	CommitClaim(ctx context.Context, saleID, productID, claimID string) (found bool, err error)
	ReleaseClaim(ctx context.Context, saleID, productID, claimID string) (ClaimRelease, error)
	SweepExpired(ctx context.Context, now time.Time, limit int) (released, activeSales int, err error)
	Queue(ctx context.Context, saleID, productID, customerID string, join bool, now time.Time) (QueueState, error)
	Drain(ctx context.Context, saleID, productID string) (initial, remaining int32, err error)
}

// ClaimPublisher đẩy lượt giành hàng flash sale lên Kafka (luồng nóng, không qua outbox).
type ClaimPublisher interface {
	PublishClaim(ctx context.Context, c PublishedClaim) error
}

type PublishedClaim struct {
	ClaimID, SaleID, ProductID, CustomerID string
	Quantity                               int32
	ExpiresAt                              time.Time
}
