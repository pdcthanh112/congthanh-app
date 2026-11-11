package config

import (
	"strings"

	c "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/config"
)

type Config struct {
	HTTPAddr          string
	GRPCAddr          string
	DatabaseURL       string
	DBSchema          string
	NodeID            int64
	MaxItemQuantity   int32
	MaxItemsPerCart   int
	ProductGRPCAddr   string
	InventoryGRPCAddr string
	ValidateProducts  bool
	LogFormat         string
	LogLevel          string
	JWKSURL           string
	JWTSecret         string
	JWTIssuer         string
	KafkaBrokers      []string // trống = không tiêu thụ ecommerce.order.events
	KafkaGroup        string
	UnleashURL        string
	UnleashToken      string
	Version           string
}

func Load() Config {
	return Config{
		HTTPAddr:          c.String("HTTP_ADDR", ":5002"),
		GRPCAddr:          c.String("GRPC_ADDR", ":19091"),
		DatabaseURL:       c.String("DATABASE_URL", "postgres://postgres:123456@localhost:5432/CongThanhApp-Ecommerce?sslmode=disable"),
		DBSchema:          c.String("DB_SCHEMA", "cart"),
		NodeID:            int64(c.Int("NODE_ID", 2)),
		MaxItemQuantity:   int32(c.Int("CART_MAX_ITEM_QUANTITY", 99)),
		MaxItemsPerCart:   c.Int("CART_MAX_ITEMS", 100),
		ProductGRPCAddr:   c.String("PRODUCT_GRPC_ADDR", "localhost:29090"),
		InventoryGRPCAddr: c.String("INVENTORY_GRPC_ADDR", "localhost:9092"),
		ValidateProducts:  c.Bool("CART_VALIDATE_PRODUCTS", true),
		LogFormat:         c.String("LOG_FORMAT", "json"),
		LogLevel:          c.String("LOG_LEVEL", "info"),
		JWKSURL:           c.String("JWT_JWKS_URL", ""),
		JWTSecret:         c.String("JWT_SECRET", ""),
		JWTIssuer:         c.String("JWT_ISSUER", ""),
		KafkaBrokers:      splitList(c.String("KAFKA_BROKERS", "")),
		KafkaGroup:        c.String("KAFKA_GROUP", "cart-service"),
		UnleashURL:        c.String("UNLEASH_URL", ""),
		UnleashToken:      c.String("UNLEASH_TOKEN", ""),
		Version:           c.String("SERVICE_VERSION", "dev"),
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
