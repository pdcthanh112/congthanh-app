package config

import c "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/config"

type Config struct {
	HTTPAddr            string
	KafkaBrokers        []string
	Topic               string
	AllowedOrigins      []string
	IPHashSalt          string
	LogFormat, LogLevel string
	Version             string
}

func Load() Config {
	return Config{
		HTTPAddr:       c.String("HTTP_ADDR", ":5029"),
		KafkaBrokers:   c.List("KAFKA_BROKERS", []string{"localhost:29092"}),
		Topic:          c.String("CLICKSTREAM_TOPIC", "clickstream.events"),
		AllowedOrigins: c.List("CORS_ALLOWED_ORIGINS", []string{"http://localhost:3000"}),
		IPHashSalt:     c.String("IP_HASH_SALT", ""),
		LogFormat:      c.String("LOG_FORMAT", "json"),
		LogLevel:       c.String("LOG_LEVEL", "info"),
		Version:        c.String("SERVICE_VERSION", "dev"),
	}
}
