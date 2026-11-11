// Package app: use case báo giá, tạo vận đơn từ đơn hàng, in nhãn qua hãng vận chuyển và cập nhật hành trình.
package app

import (
	"context"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/domain"
)

// StatusChange là sự kiện miền; adapter ghi vào outbox cùng transaction với thay đổi trạng thái.
type StatusChange struct {
	From, To domain.Status
}

type Repository interface {
	ActiveRates(ctx context.Context, country string) ([]domain.Rate, error)
	ListRates(ctx context.Context) ([]domain.Rate, error)
	CreateRate(ctx context.Context, r domain.Rate) (domain.Rate, error)
	UpdateRate(ctx context.Context, r domain.Rate) error
	DeleteRate(ctx context.Context, id int64) error

	// CreateShipment bỏ qua nếu đơn đã có vận đơn (event giao lại); trả false khi đã tồn tại.
	CreateShipment(ctx context.Context, s domain.Shipment) (bool, error)
	GetShipment(ctx context.Context, id string) (domain.Shipment, error)
	ShipmentByOrder(ctx context.Context, orderID string) (domain.Shipment, error)
	ShipmentByTracking(ctx context.Context, tracking string) (domain.Shipment, error)
	// SaveTransition lưu trạng thái, ghi lịch sử và outbox trong một transaction; khoá lạc quan theo trạng thái cũ.
	SaveTransition(ctx context.Context, s domain.Shipment, ev domain.Event, ch StatusChange) error
}

// Carrier là driven port tới hãng vận chuyển (USPS, UPS, FedEx qua EasyPost/Shippo, hoặc nhãn nội bộ).
type Carrier interface {
	Code() string
	CreateLabel(ctx context.Context, s domain.Shipment) (tracking string, err error)
}

type IDs interface{ NewID() string }

type Service struct {
	repo     Repository
	carriers map[string]Carrier
	ids      IDs
	now      func() time.Time
}

func NewService(r Repository, ids IDs, carriers ...Carrier) *Service {
	m := map[string]Carrier{}
	for _, c := range carriers {
		m[c.Code()] = c
	}
	return &Service{repo: r, carriers: m, ids: ids, now: time.Now}
}

func (s *Service) Quote(ctx context.Context, dest domain.Address, p domain.Parcel) ([]domain.Quote, error) {
	dest = dest.Normalize()
	if len(dest.CountryCode) != 2 {
		return nil, apperr.Invalid("destination countryCode is required")
	}
	rates, err := s.repo.ActiveRates(ctx, dest.CountryCode)
	if err != nil {
		return nil, err
	}
	return domain.Quotes(rates, dest, p)
}

func (s *Service) ListRates(ctx context.Context) ([]domain.Rate, error) { return s.repo.ListRates(ctx) }

func (s *Service) CreateRate(ctx context.Context, r domain.Rate) (domain.Rate, error) {
	if err := r.Validate(); err != nil {
		return r, err
	}
	return s.repo.CreateRate(ctx, r)
}

func (s *Service) UpdateRate(ctx context.Context, r domain.Rate) error {
	if err := r.Validate(); err != nil {
		return err
	}
	return s.repo.UpdateRate(ctx, r)
}

func (s *Service) DeleteRate(ctx context.Context, id int64) error { return s.repo.DeleteRate(ctx, id) }

// OnOrderPlaced tạo vận đơn PENDING (idempotent theo order_id).
func (s *Service) OnOrderPlaced(ctx context.Context, orderID, customerID string, addr *domain.Address) error {
	now := s.now().UTC()
	if addr != nil {
		a := addr.Normalize()
		addr = &a
	}
	_, err := s.repo.CreateShipment(ctx, domain.Shipment{
		ID: s.ids.NewID(), OrderID: orderID, CustomerID: customerID, Status: domain.StatusPending,
		Address: addr, CreatedAt: now, UpdatedAt: now,
	})
	return err
}

// OnOrderCancelled huỷ vận đơn nếu hàng chưa rời kho; đã lấy hàng thì giữ nguyên (xử lý hoàn hàng riêng).
func (s *Service) OnOrderCancelled(ctx context.Context, orderID string) error {
	sh, err := s.repo.ShipmentByOrder(ctx, orderID)
	if apperr.KindOf(err) == apperr.KindNotFound {
		return nil
	}
	if err != nil {
		return err
	}
	if sh.Status != domain.StatusPending && sh.Status != domain.StatusLabelCreated {
		return nil
	}
	_, err = s.UpdateStatus(ctx, sh.ID, domain.StatusCancelled, "Order cancelled", "")
	return err
}

// CreateLabel gọi hãng vận chuyển lấy mã vận đơn, chuyển LABEL_CREATED.
func (s *Service) CreateLabel(ctx context.Context, id, carrier, serviceCode string) (domain.Shipment, error) {
	c, ok := s.carriers[carrier]
	if !ok {
		return domain.Shipment{}, apperr.Invalid("unknown carrier %q", carrier)
	}
	sh, err := s.repo.GetShipment(ctx, id)
	if err != nil {
		return sh, err
	}
	from := sh.Status
	if err := sh.Transition(domain.StatusLabelCreated); err != nil {
		return sh, err
	}
	sh.Carrier, sh.ServiceCode = carrier, serviceCode
	tracking, err := c.CreateLabel(ctx, sh)
	if err != nil {
		return sh, apperr.Unavailable("carrier %s: %v", carrier, err)
	}
	sh.TrackingNumber = &tracking
	sh.UpdatedAt = s.now().UTC()
	ev := domain.Event{Status: sh.Status, Note: "Label created with " + carrier, OccurredAt: sh.UpdatedAt}
	return sh, s.repo.SaveTransition(ctx, sh, ev, StatusChange{From: from, To: sh.Status})
}

// UpdateStatus: hãng vận chuyển/quản trị cập nhật hành trình.
func (s *Service) UpdateStatus(ctx context.Context, id string, to domain.Status, note, location string) (domain.Shipment, error) {
	sh, err := s.repo.GetShipment(ctx, id)
	if err != nil {
		return sh, err
	}
	from := sh.Status
	if to == domain.StatusLabelCreated {
		return sh, apperr.Invalid("use the label endpoint to create a label")
	}
	if err := sh.Transition(to); err != nil {
		return sh, err
	}
	sh.UpdatedAt = s.now().UTC()
	ev := domain.Event{Status: to, Note: note, Location: location, OccurredAt: sh.UpdatedAt}
	return sh, s.repo.SaveTransition(ctx, sh, ev, StatusChange{From: from, To: to})
}

// ForOrder: khách chỉ xem vận đơn của mình; ADMIN xem tất cả.
func (s *Service) ForOrder(ctx context.Context, orderID, subject string, admin bool) (domain.Shipment, error) {
	sh, err := s.repo.ShipmentByOrder(ctx, orderID)
	if err != nil {
		return sh, err
	}
	if !admin && sh.CustomerID != subject {
		return domain.Shipment{}, apperr.NotFound("shipment for order %s not found", orderID)
	}
	return sh, nil
}

func (s *Service) Get(ctx context.Context, id string) (domain.Shipment, error) {
	return s.repo.GetShipment(ctx, id)
}

// Track: tra cứu công khai theo mã vận đơn, ẩn địa chỉ và thông tin khách.
func (s *Service) Track(ctx context.Context, tracking string) (domain.Shipment, error) {
	sh, err := s.repo.ShipmentByTracking(ctx, tracking)
	if err != nil {
		return sh, err
	}
	sh.Address, sh.CustomerID, sh.OrderID = nil, "", ""
	return sh, nil
}
