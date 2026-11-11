package main

import (
	"context"
	"log/slog"
	"os"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/logging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/otel"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/server"
	httpadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/data/ingestion/clickstream-collector/internal/adapter/http"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/data/ingestion/clickstream-collector/internal/adapter/kafka"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/data/ingestion/clickstream-collector/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/data/ingestion/clickstream-collector/internal/config"
)

const serviceName = "clickstream-collector"

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

	if cfg.IPHashSalt == "" {
		slog.Warn("IP_HASH_SALT trống: hash IP dễ bị dò ngược, hãy đặt giá trị bí mật ngoài môi trường dev")
	}
	pub, err := kafka.NewPublisher(cfg.KafkaBrokers, cfg.Topic)
	if err != nil {
		return err
	}
	defer pub.Close()

	svc := app.NewService(pub, cfg.IPHashSalt)
	router := server.NewRouter(serviceName, svc.Ready)
	httpadapter.New(svc, cfg.AllowedOrigins).Mount(router)

	return server.Run(ctx, server.Options{HTTPAddr: cfg.HTTPAddr, HTTPHandler: router})
}
