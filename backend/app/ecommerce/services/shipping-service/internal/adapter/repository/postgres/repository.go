// Package postgres là driven adapter Repository của shipping-service; sự kiện ghi outbox cùng transaction.
package postgres

import (
	"context"
	"errors"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	shippingv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/shipping/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/domain"
)

const (
	Source        = "shipping-service"
	AggregateType = "shipping" // Debezium Outbox Router → topic ecom.shipping.events
)

type Repository struct{ pool *pgxpool.Pool }

func New(pool *pgxpool.Pool) *Repository { return &Repository{pool: pool} }

func (r *Repository) Ping(ctx context.Context) error { return r.pool.Ping(ctx) }

type UUIDv7 struct{}

func (UUIDv7) NewID() string { return uuid.Must(uuid.NewV7()).String() }

const rateCols = `id, carrier, service_code, service_name, country_code, region_code, currency, base_fee_minor, per_kg_fee_minor, free_over_minor, days_min, days_max, active`

func scanRate(row pgx.CollectableRow) (domain.Rate, error) {
	var x domain.Rate
	err := row.Scan(&x.ID, &x.Carrier, &x.ServiceCode, &x.ServiceName, &x.CountryCode, &x.RegionCode, &x.Currency, &x.BaseFee, &x.PerKgFee, &x.FreeOver, &x.DaysMin, &x.DaysMax, &x.Active)
	return x, err
}

func (r *Repository) ActiveRates(ctx context.Context, country string) ([]domain.Rate, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+rateCols+` FROM shipping_rates WHERE active AND country_code = $1`, country)
	return pgx.CollectRows(rows, scanRate)
}

func (r *Repository) ListRates(ctx context.Context) ([]domain.Rate, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+rateCols+` FROM shipping_rates ORDER BY country_code, carrier, service_code, region_code`)
	return pgx.CollectRows(rows, scanRate)
}

func conflict(err error) error {
	var pg *pgconn.PgError
	if errors.As(err, &pg) && pg.Code == "23505" {
		return apperr.Conflict("a rate for this carrier, service and location already exists")
	}
	return err
}

func (r *Repository) CreateRate(ctx context.Context, x domain.Rate) (domain.Rate, error) {
	err := r.pool.QueryRow(ctx, `INSERT INTO shipping_rates (carrier, service_code, service_name, country_code, region_code, currency,
		base_fee_minor, per_kg_fee_minor, free_over_minor, days_min, days_max, active) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12) RETURNING id`,
		x.Carrier, x.ServiceCode, x.ServiceName, x.CountryCode, x.RegionCode, x.Currency, x.BaseFee, x.PerKgFee, x.FreeOver, x.DaysMin, x.DaysMax, x.Active).Scan(&x.ID)
	return x, conflict(err)
}

func (r *Repository) UpdateRate(ctx context.Context, x domain.Rate) error {
	tag, err := r.pool.Exec(ctx, `UPDATE shipping_rates SET carrier=$2, service_code=$3, service_name=$4, country_code=$5, region_code=$6,
		currency=$7, base_fee_minor=$8, per_kg_fee_minor=$9, free_over_minor=$10, days_min=$11, days_max=$12, active=$13 WHERE id=$1`,
		x.ID, x.Carrier, x.ServiceCode, x.ServiceName, x.CountryCode, x.RegionCode, x.Currency, x.BaseFee, x.PerKgFee, x.FreeOver, x.DaysMin, x.DaysMax, x.Active)
	if err == nil && tag.RowsAffected() == 0 {
		return apperr.NotFound("rate %d not found", x.ID)
	}
	return conflict(err)
}

func (r *Repository) DeleteRate(ctx context.Context, id int64) error {
	tag, err := r.pool.Exec(ctx, `DELETE FROM shipping_rates WHERE id = $1`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return apperr.NotFound("rate %d not found", id)
	}
	return err
}

func (r *Repository) CreateShipment(ctx context.Context, s domain.Shipment) (bool, error) {
	created := false
	err := db.InTx(ctx, r.pool, func(tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `INSERT INTO shipments (id, order_id, customer_id, status, address, created_at, updated_at)
			VALUES ($1,$2,$3,$4,$5,$6,$7) ON CONFLICT (order_id) DO NOTHING`, s.ID, s.OrderID, s.CustomerID, s.Status, s.Address, s.CreatedAt, s.UpdatedAt)
		if err != nil || tag.RowsAffected() == 0 {
			return err
		}
		created = true
		if _, err := tx.Exec(ctx, `INSERT INTO shipment_events (shipment_id, status, note, occurred_at) VALUES ($1,$2,'Shipment created',$3)`,
			s.ID, s.Status, s.CreatedAt); err != nil {
			return err
		}
		return outbox.Write(ctx, tx, Source, outbox.Message{AggregateType: AggregateType, AggregateID: s.ID,
			Payload: &shippingv1.ShipmentCreated{ShipmentId: s.ID, OrderId: s.OrderID, CustomerId: s.CustomerID}})
	})
	return created, err
}

const shipCols = `id, order_id, customer_id, carrier, service_code, tracking_number, status, address, created_at, updated_at`

func (r *Repository) one(ctx context.Context, where string, arg any, what string) (domain.Shipment, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+shipCols+` FROM shipments WHERE `+where+` = $1`, arg)
	s, err := pgx.CollectExactlyOneRow(rows, func(row pgx.CollectableRow) (domain.Shipment, error) {
		var s domain.Shipment
		err := row.Scan(&s.ID, &s.OrderID, &s.CustomerID, &s.Carrier, &s.ServiceCode, &s.TrackingNumber, &s.Status, &s.Address, &s.CreatedAt, &s.UpdatedAt)
		return s, err
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return s, apperr.NotFound("shipment %s not found", what)
	}
	if err != nil {
		return s, err
	}
	evRows, _ := r.pool.Query(ctx, `SELECT status, note, location, occurred_at FROM shipment_events WHERE shipment_id = $1 ORDER BY occurred_at, id`, s.ID)
	s.Events, err = pgx.CollectRows(evRows, pgx.RowToStructByPos[domain.Event])
	return s, err
}

func (r *Repository) GetShipment(ctx context.Context, id string) (domain.Shipment, error) {
	return r.one(ctx, "id", id, id)
}
func (r *Repository) ShipmentByOrder(ctx context.Context, orderID string) (domain.Shipment, error) {
	return r.one(ctx, "order_id", orderID, "for order "+orderID)
}
func (r *Repository) ShipmentByTracking(ctx context.Context, t string) (domain.Shipment, error) {
	return r.one(ctx, "tracking_number", t, t)
}

func (r *Repository) SaveTransition(ctx context.Context, s domain.Shipment, ev domain.Event, ch app.StatusChange) error {
	return db.InTx(ctx, r.pool, func(tx pgx.Tx) error {
		// Khoá lạc quan: chỉ cập nhật khi trạng thái chưa bị request khác đổi.
		tag, err := tx.Exec(ctx, `UPDATE shipments SET status=$3, carrier=$4, service_code=$5, tracking_number=$6, updated_at=$7
			WHERE id=$1 AND status=$2`, s.ID, ch.From, s.Status, s.Carrier, s.ServiceCode, s.TrackingNumber, s.UpdatedAt)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return apperr.Conflict("shipment was modified concurrently, reload and retry")
		}
		if _, err := tx.Exec(ctx, `INSERT INTO shipment_events (shipment_id, status, note, location, occurred_at) VALUES ($1,$2,$3,$4,$5)`,
			s.ID, ev.Status, ev.Note, ev.Location, ev.OccurredAt); err != nil {
			return err
		}
		tracking := ""
		if s.TrackingNumber != nil {
			tracking = *s.TrackingNumber
		}
		return outbox.Write(ctx, tx, Source, outbox.Message{AggregateType: AggregateType, AggregateID: s.ID,
			Payload: &shippingv1.ShipmentStatusChanged{ShipmentId: s.ID, OrderId: s.OrderID, CustomerId: s.CustomerID,
				FromStatus: string(ch.From), ToStatus: string(ch.To), TrackingNumber: tracking, Carrier: s.Carrier}})
	})
}
