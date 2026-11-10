package config

import (
	"strings"

	c "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/config"
)

type Config struct {
	HTTPAddr, GRPCAddr, DatabaseURL, DBSchema string
	KafkaBrokers                              []string
	KafkaGroup                                string
	LogFormat, LogLevel                       string
	JWKSURL, JWTSecret, JWTIssuer             string
	Version                                   string
}

func Load() Config {
	return Config{
		HTTPAddr:     c.String("HTTP_ADDR", ":5016"),
		GRPCAddr:     c.String("GRPC_ADDR", ":9097"),
		DatabaseURL:  c.String("DATABASE_URL", "postgres://postgres:123456@localhost:5432/CongThanhApp-Ecommerce?sslmode=disable"),
		DBSchema:     c.String("DB_SCHEMA", "shipping"),
		KafkaBrokers: splitList(c.String("KAFKA_BROKERS", "")),
		KafkaGroup:   c.String("KAFKA_GROUP", "shipping-service"),
		LogFormat:    c.String("LOG_FORMAT", "json"),
		LogLevel:     c.String("LOG_LEVEL", "info"),
		JWKSURL:      c.String("JWT_JWKS_URL", ""),
		JWTSecret:    c.String("JWT_SECRET", ""),
		JWTIssuer:    c.String("JWT_ISSUER", ""),
		Version:      c.String("SERVICE_VERSION", "dev"),
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
