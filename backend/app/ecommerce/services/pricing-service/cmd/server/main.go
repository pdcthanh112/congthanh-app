// pricing-service: bảng giá (công khai, theo nhóm khách), giá hẹn giờ / flash sale, lịch sử giá và
// giá thấp nhất 30 ngày; tra giá theo lô qua gRPC/REST, phát ecommerce.pricing.events qua outbox.
package main

import (
	"context"
	"log/slog"
	"os"

	"github.com/redis/go-redis/extra/redisotel/v9"
	goredis "github.com/redis/go-redis/v9"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/kafka"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/logging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/otel"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/server"
	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/pricing/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/adapter/cache"
	grpcadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/adapter/grpc"
	httpadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/adapter/http"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/adapter/messaging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/config"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/migrations"
)

const serviceName = "pricing-service"

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

	var priceCache app.Cache
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
		priceCache = cache.New(rdb)
	} else {
		slog.Warn("REDIS_URL not set: price lookups are not cached")
	}

	repo := postgres.New(pool)
	svc := app.NewService(repo, priceCache, postgres.UUIDv7{}, nil)

	bg := []func(context.Context) error{svc.RunScheduler(cfg.SchedulerInterval)}
	if len(cfg.KafkaBrokers) > 0 {
		cons, err := kafka.NewConsumer(kafka.ConsumerConfig{Brokers: cfg.KafkaBrokers, Group: cfg.KafkaGroup, Topics: []string{messaging.TopicProductEvents}},
			pool, messaging.Handler(repo, svc.Invalidate))
		if err != nil {
			return err
		}
		bg = append(bg, cons.Run)
	} else {
		slog.Warn("KAFKA_BROKERS not set: new products will not get a default price from the catalog")
	}

	grpcSrv, health := server.NewGRPCServer(verifier)
	pb.RegisterPricingServiceServer(grpcSrv, grpcadapter.New(svc))
	health.SetServingStatus("", healthpb.HealthCheckResponse_SERVING)

	router := server.NewRouter(serviceName, repo.Ping)
	httpadapter.New(svc, verifier).Mount(router)

	return server.Run(ctx, server.Options{HTTPAddr: cfg.HTTPAddr, HTTPHandler: router, GRPCAddr: cfg.GRPCAddr, GRPCServer: grpcSrv, Background: bg})
}
