// webhook-service: bên thứ ba (ERP, kế toán, vận chuyển) đăng ký URL nhận sự kiện đơn hàng/sản phẩm/
// vận chuyển; gửi có ký HMAC, thử lại theo backoff, chặn SSRF (thay service webhook Java).
package main

import (
	"context"
	"log/slog"
	"os"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/kafka"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/logging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/otel"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/server"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/adapter/delivery"
	httpadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/adapter/http"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/adapter/messaging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/config"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/migrations"
)

const serviceName = "webhook-service"

func main() {
	if err := run(); err != nil {
		slog.Error(serviceName+" stopped", "err", err)
		os.Exit(1)
	}
}

func run() error {
	cfg := config.Load()
	logging.New(serviceName, cfg.LogFormat, cfg.LogLevel)
	ctx := context.Background()

	shutdownOtel, err := otel.Setup(ctx, serviceName, cfg.Version)
	if err != nil {
		return err
	}
	defer func() { _ = shutdownOtel(context.Background()) }()

	pool, err := db.Connect(ctx, cfg.DatabaseURL, cfg.DBSchema)
	if err != nil {
		return err
	}
	defer pool.Close()
	if err := db.Migrate(ctx, pool, migrations.FS, "goose_db_version"); err != nil {
		return err
	}
	verifier, err := auth.NewVerifier(ctx, auth.Config{JWKSURL: cfg.JWKSURL, HMACSecret: cfg.JWTSecret, Issuer: cfg.JWTIssuer})
	if err != nil {
		return err
	}
	if verifier == nil {
		slog.Warn("JWT verification is disabled (set JWT_JWKS_URL or JWT_SECRET)")
	}
	if cfg.AllowInsecure {
		slog.Warn("WEBHOOK_ALLOW_INSECURE=true: http:// URLs and private addresses are allowed (dev only)")
	}

	repo := postgres.New(pool)
	svc := app.NewService(repo, delivery.New(cfg.SendTimeout, cfg.AllowInsecure), postgres.UUIDv7{}, cfg.AllowInsecure)

	worker := func(ctx context.Context) error {
		t := time.NewTicker(cfg.PollInterval)
		defer t.Stop()
		for {
			select {
			case <-ctx.Done():
				return nil
			case <-t.C:
				// Lô đầy thì chạy tiếp ngay, không chờ tick.
				for {
					n, err := svc.DeliverDue(ctx, cfg.BatchSize)
					if err != nil {
						slog.ErrorContext(ctx, "deliver webhooks", "err", err)
					}
					if n < cfg.BatchSize || ctx.Err() != nil {
						break
					}
				}
			}
		}
	}
	bg := []func(context.Context) error{worker}
	if len(cfg.KafkaBrokers) > 0 {
		cons, err := kafka.NewConsumer(kafka.ConsumerConfig{Brokers: cfg.KafkaBrokers, Group: cfg.KafkaGroup, Topics: messaging.Topics}, pool, messaging.Handler(svc))
		if err != nil {
			return err
		}
		bg = append(bg, cons.Run)
	} else {
		slog.Warn("KAFKA_BROKERS not set: only ping deliveries will be sent")
	}

	router := server.NewRouter(serviceName, repo.Ping)
	httpadapter.New(svc, verifier).Mount(router)
	return server.Run(ctx, server.Options{HTTPAddr: cfg.HTTPAddr, HTTPHandler: router, Background: bg})
}
