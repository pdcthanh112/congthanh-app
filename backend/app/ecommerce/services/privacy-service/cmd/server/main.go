package main

import (
	"context"
	"log/slog"
	"os"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/kafka"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/logging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/otel"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/server"
	httpadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/adapter/http"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/adapter/keycloak"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/adapter/messaging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/config"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/migrations"
)

const serviceName = "privacy-service"

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
	verifier, err := auth.NewVerifier(ctx, auth.Config{JWKSURL: cfg.JWKSURL, HMACSecret: cfg.JWTSecret, Issuer: cfg.JWTIssuer, Disabled: cfg.AuthDisabled})
	if err != nil {
		return err
	}

	repo := postgres.New(pool)
	svc := app.NewService(repo, postgres.UUIDv7{}, cfg.Processors, cfg.PolicyVersion)
	if cfg.KeycloakURL != "" && cfg.KeycloakClientSecret != "" {
		svc.WithIdentity(keycloak.New(cfg.KeycloakURL, cfg.KeycloakRealm, cfg.KeycloakClientID, cfg.KeycloakClientSecret))
	} else {
		slog.Warn("KEYCLOAK_URL/KEYCLOAK_CLIENT_SECRET not set: keycloak erasure tasks must be confirmed by staff")
	}

	bg := []func(context.Context) error{svc.RunSweeper, outbox.NewJanitor(pool, outbox.JanitorConfig{}).Run}
	if len(cfg.KafkaBrokers) > 0 {
		cons, err := kafka.NewConsumer(kafka.ConsumerConfig{Brokers: cfg.KafkaBrokers, Group: cfg.KafkaGroup,
			Topics: []string{messaging.TopicPrivacyEvents}}, pool, messaging.Handler(svc))
		if err != nil {
			return err
		}
		bg = append(bg, cons.Run)
	} else {
		slog.Warn("KAFKA_BROKERS not set: processor replies will not be received")
	}

	router := server.NewRouter(serviceName, repo.Ping)
	httpadapter.New(svc, verifier).Mount(router)

	return server.Run(ctx, server.Options{HTTPAddr: cfg.HTTPAddr, HTTPHandler: router, Background: bg})
}
