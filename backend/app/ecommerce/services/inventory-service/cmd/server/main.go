// inventory-service: tồn kho, kho hàng, giữ chỗ cho saga đặt hàng và flash sale.
// Composition root của kiến trúc hexagonal: chỉ nơi này biết đến mọi adapter.
package main

import (
	"context"
	"log/slog"
	"os"
	"time"

	"github.com/redis/go-redis/extra/redisotel/v9"
	goredis "github.com/redis/go-redis/v9"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/featureflag"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/idgen"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/kafka"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/logging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/otel"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/server"
	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/inventory/v1"
	grpcadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/adapter/grpc"
	httpadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/adapter/http"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/adapter/messaging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/adapter/repository/postgres"
	redisrepo "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/adapter/repository/redis"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/config"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/migrations"
)

const serviceName = "inventory-service"

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

	if err := featureflag.Init(featureflag.Config{URL: cfg.UnleashURL, Token: cfg.UnleashToken, AppName: serviceName}); err != nil {
		slog.Warn("feature flags unavailable, using defaults", "err", err)
	}

	pool, err := db.Connect(ctx, cfg.DatabaseURL, cfg.DBSchema)
	if err != nil {
		return err
	}
	defer pool.Close()
	if err := db.Migrate(ctx, pool, migrations.FS, "goose_db_version"); err != nil {
		return err
	}

	ids, err := idgen.New(cfg.NodeID)
	if err != nil {
		return err
	}
	verifier, err := auth.NewVerifier(ctx, auth.Config{JWKSURL: cfg.JWKSURL, HMACSecret: cfg.JWTSecret, Issuer: cfg.JWTIssuer})
	if err != nil {
		return err
	}
	if verifier == nil {
		slog.Warn("JWT verification is disabled (set JWT_JWKS_URL or JWT_SECRET)")
	}

	// --- driven adapters ---
	repo := postgres.New(pool, ids)
	var (
		flash  app.FlashSaleStore
		claims app.ClaimPublisher
		bg     []func(context.Context) error
	)
	if cfg.RedisURL != "" {
		opt, err := goredis.ParseURL(cfg.RedisURL)
		if err != nil {
			return err
		}
		rdb := goredis.NewClient(opt)
		defer rdb.Close()
		if err := redisotel.InstrumentTracing(rdb); err != nil {
			return err
		}
		flash = redisrepo.NewFlashSaleStore(rdb)
	}
	if len(cfg.KafkaBrokers) > 0 {
		prod, err := kafka.NewProducer(cfg.KafkaBrokers, messaging.Source)
		if err != nil {
			return err
		}
		defer prod.Close()
		claims = messaging.ClaimPublisher{P: prod}
	} else {
		slog.Warn("KAFKA_BROKERS not set: order events are not consumed and flash sale is disabled")
	}

	svc := app.NewService(repo, repo, repo.Warehouses(), flash, claims, app.Options{
		ReservationTTL: cfg.ReservationTTL, DefaultWarehouse: cfg.DefaultWarehouse,
	})

	if len(cfg.KafkaBrokers) > 0 {
		cons, err := kafka.NewConsumer(kafka.ConsumerConfig{
			Brokers: cfg.KafkaBrokers, Group: cfg.KafkaGroup, Topics: []string{messaging.TopicOrderEvents},
		}, pool, messaging.OrderEventsHandler(svc))
		if err != nil {
			return err
		}
		bg = append(bg, cons.Run)
	}
	bg = append(bg, sweeper(svc, cfg.SweepInterval))

	// --- driving adapters ---
	grpcSrv, health := server.NewGRPCServer(verifier)
	pb.RegisterInventoryServiceServer(grpcSrv, grpcadapter.New(svc))
	health.SetServingStatus("", healthpb.HealthCheckResponse_SERVING)

	router := server.NewRouter(serviceName, repo.Ping)
	httpadapter.New(svc, verifier).Mount(router)

	return server.Run(ctx, server.Options{
		HTTPAddr: cfg.HTTPAddr, HTTPHandler: router,
		GRPCAddr: cfg.GRPCAddr, GRPCServer: grpcSrv,
		Background: bg,
	})
}

// sweeper trả hàng cho các reservation hết hạn mà saga không release.
func sweeper(svc *app.Service, every time.Duration) func(context.Context) error {
	return func(ctx context.Context) error {
		t := time.NewTicker(every)
		defer t.Stop()
		for {
			select {
			case <-ctx.Done():
				return nil
			case <-t.C:
				n, err := svc.ReleaseExpired(ctx, 200)
				if err != nil {
					slog.ErrorContext(ctx, "release expired reservations", "err", err)
				} else if n > 0 {
					slog.InfoContext(ctx, "released expired reservations", "count", n)
				}
			}
		}
	}
}
