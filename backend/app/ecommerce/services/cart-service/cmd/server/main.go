// cart-service: giỏ hàng của khách. Composition root của kiến trúc hexagonal.
package main

import (
	"context"
	"github.com/jackc/pgx/v5"
	"log/slog"
	"os"

	healthpb "google.golang.org/grpc/health/grpc_health_v1"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/featureflag"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/grpcx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/idgen"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/kafka"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/logging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/otel"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/privacy"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/server"
	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/cart/v1"
	grpcadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/adapter/grpc"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/adapter/grpcclient"
	httpadapter "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/adapter/http"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/adapter/messaging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/config"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/migrations"
)

const serviceName = "cart-service"

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
	verifier, err := auth.NewVerifier(ctx, auth.Config{JWKSURL: cfg.JWKSURL, HMACSecret: cfg.JWTSecret, Issuer: cfg.JWTIssuer, Disabled: cfg.AuthDisabled})
	if err != nil {
		return err
	}

	// --- driven adapters ---
	productConn, err := grpcx.Dial(cfg.ProductGRPCAddr)
	if err != nil {
		return err
	}
	defer productConn.Close()
	inventoryConn, err := grpcx.Dial(cfg.InventoryGRPCAddr)
	if err != nil {
		return err
	}
	defer inventoryConn.Close()
	repo := postgres.New(pool, ids)

	svc := app.NewService(repo, repo, repo, grpcclient.NewProducts(productConn), grpcclient.NewInventory(inventoryConn), app.Options{
		MaxItemQuantity: cfg.MaxItemQuantity, MaxItemsPerCart: cfg.MaxItemsPerCart, ValidateProducts: cfg.ValidateProducts,
	})

	var bg []func(context.Context) error
	if len(cfg.KafkaBrokers) > 0 {
		cons, err := kafka.NewConsumer(kafka.ConsumerConfig{
			Brokers: cfg.KafkaBrokers, Group: cfg.KafkaGroup, Topics: []string{messaging.TopicOrderEvents},
		}, pool, messaging.OrderEventsHandler(svc))
		if err != nil {
			return err
		}
		bg = append(bg, cons.Run)
		// Yêu cầu xoá dữ liệu từ privacy-service: xoá giỏ và hàng chờ dọn giỏ của khách (ADR 0027).
		privacyCons, err := kafka.NewConsumer(kafka.ConsumerConfig{
			Brokers: cfg.KafkaBrokers, Group: cfg.KafkaGroup + "-privacy", Topics: []string{privacy.Topic},
		}, pool, privacy.Handler("cart-service", "cart-service", eraseCustomer))
		if err != nil {
			return err
		}
		bg = append(bg, privacyCons.Run)
	} else {
		slog.Warn("KAFKA_BROKERS not set: ordered items will not be removed from carts automatically")
	}

	// --- driving adapters ---
	grpcSrv, health := server.NewGRPCServer(verifier)
	pb.RegisterCartServiceServer(grpcSrv, grpcadapter.New(svc))
	health.SetServingStatus("", healthpb.HealthCheckResponse_SERVING)

	router := server.NewRouter(serviceName, repo.Ping)
	httpadapter.New(svc, verifier).Mount(router)

	// Dọn outbox (Debezium đã đọc qua WAL) và processed_events quá hạn.
	bg = append(bg, outbox.NewJanitor(pool, outbox.JanitorConfig{}).Run)

	return server.Run(ctx, server.Options{
		HTTPAddr: cfg.HTTPAddr, HTTPHandler: router,
		GRPCAddr: cfg.GRPCAddr, GRPCServer: grpcSrv,
		Background: bg,
	})
}

// eraseCustomer xoá giỏ hàng (cart_items xoá theo ON DELETE CASCADE) và hàng chờ dọn giỏ của khách.
func eraseCustomer(ctx context.Context, tx pgx.Tx, subjectID, _ string) (string, error) {
	if _, err := tx.Exec(ctx, `DELETE FROM carts WHERE customer_id = $1`, subjectID); err != nil {
		return "", err
	}
	if _, err := tx.Exec(ctx, `DELETE FROM pending_order_clears WHERE customer_id = $1`, subjectID); err != nil {
		return "", err
	}
	return "", nil
}
