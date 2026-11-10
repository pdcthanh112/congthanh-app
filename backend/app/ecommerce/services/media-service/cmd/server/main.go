// media-service: cấp URL ký sẵn để tải file thẳng lên S3/R2/MinIO, xác minh và lưu metadata
// (thay file-service Java nhận file qua service).
package main

import (
	"context"
	"errors"
	"log/slog"
	"os"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/logging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/otel"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/server"
	httpadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/adapter/http"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/adapter/s3store"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/config"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/migrations"
)

const serviceName = "media-service"

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
	store, err := s3store.New(ctx, s3store.Config{
		Bucket: cfg.S3Bucket, Region: cfg.S3Region, Endpoint: cfg.S3Endpoint, PathStyle: cfg.S3PathStyle,
		AccessKey: cfg.S3AccessKey, SecretKey: cfg.S3SecretKey, PublicURL: cfg.PublicURL, PresignEndpoint: cfg.S3PresignURL,
	})
	if err != nil {
		return err
	}
	repo := postgres.New(pool)
	svc := app.NewService(repo, store, postgres.UUIDv7{}, app.Options{UploadTTL: cfg.UploadTTL, PendingTTL: cfg.PendingTTL})

	cleanup := func(ctx context.Context) error {
		t := time.NewTicker(cfg.CleanupInterval)
		defer t.Stop()
		for {
			select {
			case <-ctx.Done():
				return nil
			case <-t.C:
				if n, err := svc.CleanupPending(ctx); err != nil {
					slog.ErrorContext(ctx, "cleanup pending uploads", "err", err)
				} else if n > 0 {
					slog.InfoContext(ctx, "cleaned up pending uploads", "count", n)
				}
			}
		}
	}

	router := server.NewRouter(serviceName, func(ctx context.Context) error { return errors.Join(repo.Ping(ctx), store.Ping(ctx)) })
	httpadapter.New(svc, verifier).Mount(router)

	return server.Run(ctx, server.Options{HTTPAddr: cfg.HTTPAddr, HTTPHandler: router, Background: []func(context.Context) error{cleanup}})
}
