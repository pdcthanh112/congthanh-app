// search-service: tìm kiếm và gợi ý sản phẩm trên OpenSearch; chỉ mục cập nhật từ ecom.product.events
// (thay bản Java dùng Elasticsearch + Logstash/CDC).
package main

import (
	"context"
	"log/slog"
	"os"
	"time"

	healthpb "google.golang.org/grpc/health/grpc_health_v1"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/kafka"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/logging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/otel"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/server"
	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/search/v1"
	grpcadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/adapter/grpc"
	httpadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/adapter/http"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/adapter/messaging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/adapter/opensearch"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/config"
)

const serviceName = "search-service"

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

	idx := opensearch.New(opensearch.Config{URL: cfg.OpenSearchURL, Index: cfg.OpenSearchIndex, Username: cfg.OpenSearchUser, Password: cfg.OpenSearchPassword})
	// OpenSearch có thể lên chậm hơn service (compose, cluster mới): thử tạo chỉ mục trong 2 phút.
	for i := 0; ; i++ {
		if err = idx.EnsureIndex(ctx); err == nil {
			break
		}
		if i >= 24 {
			return err
		}
		slog.Warn("opensearch not ready, retrying", "err", err)
		time.Sleep(5 * time.Second)
	}
	verifier, err := auth.NewVerifier(ctx, auth.Config{JWKSURL: cfg.JWKSURL, HMACSecret: cfg.JWTSecret, Issuer: cfg.JWTIssuer})
	if err != nil {
		return err
	}
	svc := app.NewService(idx)

	var bg []func(context.Context) error
	if len(cfg.KafkaBrokers) > 0 {
		cons, err := kafka.NewConsumer(kafka.ConsumerConfig{Brokers: cfg.KafkaBrokers, Group: cfg.KafkaGroup, Topics: []string{messaging.TopicProductEvents}}, nil, messaging.Handler(svc))
		if err != nil {
			return err
		}
		bg = append(bg, cons.Run)
	} else {
		slog.Warn("KAFKA_BROKERS not set: search index will not be updated")
	}

	grpcSrv, health := server.NewGRPCServer(verifier)
	pb.RegisterSearchServiceServer(grpcSrv, grpcadapter.New(svc))
	health.SetServingStatus("", healthpb.HealthCheckResponse_SERVING)

	router := server.NewRouter(serviceName, idx.Ping)
	httpadapter.New(svc).Mount(router)

	return server.Run(ctx, server.Options{HTTPAddr: cfg.HTTPAddr, HTTPHandler: router, GRPCAddr: cfg.GRPCAddr, GRPCServer: grpcSrv, Background: bg})
}
