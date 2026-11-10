// tax-service: loại thuế, thuế suất theo vùng và tính thuế cho đơn hàng (thay bản Java).
package main

import (
	"context"
	"log/slog"
	"os"

	healthpb "google.golang.org/grpc/health/grpc_health_v1"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/logging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/otel"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/server"
	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/tax/v1"
	grpcadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/adapter/grpc"
	httpadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/adapter/http"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/config"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/migrations"
)

const serviceName = "tax-service"

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

	repo := postgres.New(pool)
	svc := app.NewService(repo)

	grpcSrv, health := server.NewGRPCServer(verifier)
	pb.RegisterTaxServiceServer(grpcSrv, grpcadapter.New(svc))
	health.SetServingStatus("", healthpb.HealthCheckResponse_SERVING)

	router := server.NewRouter(serviceName, repo.Ping)
	httpadapter.New(svc, verifier).Mount(router)

	return server.Run(ctx, server.Options{HTTPAddr: cfg.HTTPAddr, HTTPHandler: router, GRPCAddr: cfg.GRPCAddr, GRPCServer: grpcSrv})
}
