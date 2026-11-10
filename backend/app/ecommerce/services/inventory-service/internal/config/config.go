package config

import (
	"strings"
	"time"

	c "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/config"
)

type Config struct {
	HTTPAddr         string
	GRPCAddr         string
	DatabaseURL      string
	DBSchema         string
	NodeID           int64
	ReservationTTL   time.Duration
	SweepInterval    time.Duration
	LogFormat        string
	LogLevel         string
	JWKSURL          string
	JWTSecret        string
	JWTIssuer        string
	DefaultWarehouse string
	// Hạ tầng tuỳ chọn: để trống thì tính năng tương ứng tắt.
	RedisURL     string   // flash sale (Redis/Valkey)
	KafkaBrokers []string // consumer ecom.order.events + publish FlashSaleClaimed
	KafkaGroup   string
	UnleashURL   string
	UnleashToken string
	Version      string
}

func Load() Config {
	return Config{
		HTTPAddr:         c.String("HTTP_ADDR", ":5014"),
		GRPCAddr:         c.String("GRPC_ADDR", ":9092"),
		DatabaseURL:      c.String("DATABASE_URL", "postgres://postgres:123456@localhost:5432/CongThanhApp-Ecommerce?sslmode=disable"),
		DBSchema:         c.String("DB_SCHEMA", "inventory"),
		NodeID:           int64(c.Int("NODE_ID", 4)),
		ReservationTTL:   c.Duration("RESERVATION_TTL", 15*time.Minute),
		SweepInterval:    c.Duration("RESERVATION_SWEEP_INTERVAL", time.Minute),
		LogFormat:        c.String("LOG_FORMAT", "json"),
		LogLevel:         c.String("LOG_LEVEL", "info"),
		JWKSURL:          c.String("JWT_JWKS_URL", ""),
		JWTSecret:        c.String("JWT_SECRET", ""),
		JWTIssuer:        c.String("JWT_ISSUER", ""),
		DefaultWarehouse: c.String("DEFAULT_WAREHOUSE_ID", "WH-DEFAULT"),
		RedisURL:         c.String("REDIS_URL", ""),
		KafkaBrokers:     splitList(c.String("KAFKA_BROKERS", "")),
		KafkaGroup:       c.String("KAFKA_GROUP", "inventory-service"),
		UnleashURL:       c.String("UNLEASH_URL", ""),
		UnleashToken:     c.String("UNLEASH_TOKEN", ""),
		Version:          c.String("SERVICE_VERSION", "dev"),
	}
}

func splitList(v string) []string {
	var out []string
	for _, p := range strings.Split(v, ",") {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
