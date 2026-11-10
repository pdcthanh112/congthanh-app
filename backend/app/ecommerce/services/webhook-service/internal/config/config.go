package config

import (
	"strings"
	"time"

	c "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/config"
)

type Config struct {
	HTTPAddr, DatabaseURL, DBSchema string
	KafkaBrokers                    []string
	KafkaGroup                      string
	// AllowInsecure: cho URL http:// và IP nội bộ (chỉ dùng khi dev/test).
	AllowInsecure                 bool
	SendTimeout, PollInterval     time.Duration
	BatchSize                     int
	LogFormat, LogLevel           string
	JWKSURL, JWTSecret, JWTIssuer string
	Version                       string
}

func Load() Config {
	return Config{
		HTTPAddr:      c.String("HTTP_ADDR", ":5023"),
		DatabaseURL:   c.String("DATABASE_URL", "postgres://postgres:123456@localhost:5432/CongThanhApp-Ecommerce?sslmode=disable"),
		DBSchema:      c.String("DB_SCHEMA", "webhook"),
		KafkaBrokers:  splitList(c.String("KAFKA_BROKERS", "")),
		KafkaGroup:    c.String("KAFKA_GROUP", "webhook-service"),
		AllowInsecure: c.Bool("WEBHOOK_ALLOW_INSECURE", false),
		SendTimeout:   c.Duration("WEBHOOK_SEND_TIMEOUT", 10*time.Second),
		PollInterval:  c.Duration("WEBHOOK_POLL_INTERVAL", 2*time.Second),
		BatchSize:     c.Int("WEBHOOK_BATCH_SIZE", 50),
		LogFormat:     c.String("LOG_FORMAT", "json"),
		LogLevel:      c.String("LOG_LEVEL", "info"),
		JWKSURL:       c.String("JWT_JWKS_URL", ""),
		JWTSecret:     c.String("JWT_SECRET", ""),
		JWTIssuer:     c.String("JWT_ISSUER", ""),
		Version:       c.String("SERVICE_VERSION", "dev"),
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
