package config

import c "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/config"

type Config struct {
	HTTPAddr, GRPCAddr, DatabaseURL, DBSchema string
	LogFormat, LogLevel                       string
	JWKSURL, JWTSecret, JWTIssuer             string
	Version                                   string
}

func Load() Config {
	return Config{
		HTTPAddr:    c.String("HTTP_ADDR", ":5022"),
		GRPCAddr:    c.String("GRPC_ADDR", ":9096"),
		DatabaseURL: c.String("DATABASE_URL", "postgres://postgres:123456@localhost:5432/CongThanhApp-Ecommerce?sslmode=disable"),
		DBSchema:    c.String("DB_SCHEMA", "tax"),
		LogFormat:   c.String("LOG_FORMAT", "json"),
		LogLevel:    c.String("LOG_LEVEL", "info"),
		JWKSURL:     c.String("JWT_JWKS_URL", ""),
		JWTSecret:   c.String("JWT_SECRET", ""),
		JWTIssuer:   c.String("JWT_ISSUER", ""),
		Version:     c.String("SERVICE_VERSION", "dev"),
	}
}
