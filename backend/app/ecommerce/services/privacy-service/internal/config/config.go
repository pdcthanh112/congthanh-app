package config

import (
	"strings"

	c "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/config"
)

// DefaultProcessors: service/hệ thống đang giữ dữ liệu cá nhân và phải xử lý yêu cầu xoá/xem.
// Thêm service mới có PII thì phải thêm vào đây (và cho nó nghe ErasureRequested/AccessExportRequested).
const DefaultProcessors = "customer-service,order-service,cart-service,review-service,message-service," +
	"notification-service,fraud-service,recommendation-service,returns-service,moderation-service," +
	"lakehouse,keycloak"

type Config struct {
	AuthDisabled                    bool // AUTH_DISABLED=true: tắt xác thực, chỉ cho dev/test
	HTTPAddr, DatabaseURL, DBSchema string
	KafkaBrokers                    []string
	KafkaGroup                      string
	LogFormat, LogLevel             string
	JWKSURL, JWTSecret, JWTIssuer   string
	Version                         string
	Processors                      []string
	PolicyVersion                   string
	// Keycloak admin (xoá tài khoản tự động); trống = task "keycloak" do nhân viên xác nhận tay.
	KeycloakURL, KeycloakRealm, KeycloakClientID, KeycloakClientSecret string
}

func Load() Config {
	return Config{
		HTTPAddr:             c.String("HTTP_ADDR", ":5033"),
		DatabaseURL:          c.String("DATABASE_URL", "postgres://postgres:postgres@localhost:5432/ecommerce?sslmode=disable"),
		DBSchema:             c.String("DB_SCHEMA", "privacy"),
		KafkaBrokers:         splitList(c.String("KAFKA_BROKERS", "")),
		KafkaGroup:           c.String("KAFKA_GROUP", "privacy-service"),
		LogFormat:            c.String("LOG_FORMAT", "json"),
		LogLevel:             c.String("LOG_LEVEL", "info"),
		JWKSURL:              c.String("JWT_JWKS_URL", ""),
		JWTSecret:            c.String("JWT_SECRET", ""),
		AuthDisabled:         c.Bool("AUTH_DISABLED", false),
		JWTIssuer:            c.String("JWT_ISSUER", ""),
		Version:              c.String("SERVICE_VERSION", "dev"),
		Processors:           splitList(c.String("PRIVACY_PROCESSORS", DefaultProcessors)),
		PolicyVersion:        c.String("PRIVACY_POLICY_VERSION", "2026-11"),
		KeycloakURL:          c.String("KEYCLOAK_URL", ""),
		KeycloakRealm:        c.String("KEYCLOAK_REALM", "ecommerce"),
		KeycloakClientID:     c.String("KEYCLOAK_CLIENT_ID", "privacy-service"),
		KeycloakClientSecret: c.String("KEYCLOAK_CLIENT_SECRET", ""),
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
