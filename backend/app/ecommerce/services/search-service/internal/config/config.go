package config

import (
	"strings"

	c "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/config"
)

type Config struct {
	HTTPAddr, GRPCAddr                       string
	ElasticsearchURL, ElasticsearchIndex     string
	ElasticsearchUser, ElasticsearchPassword string
	ElasticsearchAPIKey                      string // ưu tiên hơn user/password
	KafkaBrokers                             []string
	KafkaGroup                               string
	LogFormat, LogLevel                      string
	JWKSURL, JWTSecret, JWTIssuer            string
	Version                                  string
}

func Load() Config {
	return Config{
		HTTPAddr:              c.String("HTTP_ADDR", ":5013"),
		GRPCAddr:              c.String("GRPC_ADDR", ":9098"),
		ElasticsearchURL:      c.String("ELASTICSEARCH_URL", "http://localhost:9200"),
		ElasticsearchIndex:    c.String("ELASTICSEARCH_INDEX", "products"),
		ElasticsearchUser:     c.String("ELASTICSEARCH_USERNAME", ""),
		ElasticsearchPassword: c.String("ELASTICSEARCH_PASSWORD", ""),
		ElasticsearchAPIKey:   c.String("ELASTICSEARCH_API_KEY", ""),
		KafkaBrokers:          splitList(c.String("KAFKA_BROKERS", "")),
		KafkaGroup:            c.String("KAFKA_GROUP", "search-service"),
		LogFormat:             c.String("LOG_FORMAT", "json"),
		LogLevel:              c.String("LOG_LEVEL", "info"),
		JWKSURL:               c.String("JWT_JWKS_URL", ""),
		JWTSecret:             c.String("JWT_SECRET", ""),
		JWTIssuer:             c.String("JWT_ISSUER", ""),
		Version:               c.String("SERVICE_VERSION", "dev"),
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
