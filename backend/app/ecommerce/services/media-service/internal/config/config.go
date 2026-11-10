package config

import (
	"time"

	c "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/config"
)

type Config struct {
	HTTPAddr, DatabaseURL, DBSchema              string
	S3Bucket, S3Region, S3Endpoint, S3PresignURL string
	S3PathStyle                                  bool
	S3AccessKey, S3SecretKey, PublicURL          string
	UploadTTL, PendingTTL, CleanupInterval       time.Duration
	LogFormat, LogLevel                          string
	JWKSURL, JWTSecret, JWTIssuer                string
	Version                                      string
}

func Load() Config {
	return Config{
		HTTPAddr:        c.String("HTTP_ADDR", ":5007"),
		DatabaseURL:     c.String("DATABASE_URL", "postgres://postgres:123456@localhost:5432/CongThanhApp-Ecommerce?sslmode=disable"),
		DBSchema:        c.String("DB_SCHEMA", "media"),
		S3Bucket:        c.String("S3_BUCKET", "ecommerce-media"),
		S3Region:        c.String("S3_REGION", "ap-southeast-1"),
		S3Endpoint:      c.String("S3_ENDPOINT", ""),
		S3PresignURL:    c.String("S3_PRESIGN_ENDPOINT", ""),
		S3PathStyle:     c.Bool("S3_PATH_STYLE", false),
		S3AccessKey:     c.String("S3_ACCESS_KEY", ""),
		S3SecretKey:     c.String("S3_SECRET_KEY", ""),
		PublicURL:       c.String("MEDIA_PUBLIC_URL", "http://localhost:9000/ecommerce-media/"),
		UploadTTL:       c.Duration("MEDIA_UPLOAD_TTL", 15*time.Minute),
		PendingTTL:      c.Duration("MEDIA_PENDING_TTL", 24*time.Hour),
		CleanupInterval: c.Duration("MEDIA_CLEANUP_INTERVAL", time.Hour),
		LogFormat:       c.String("LOG_FORMAT", "json"),
		LogLevel:        c.String("LOG_LEVEL", "info"),
		JWKSURL:         c.String("JWT_JWKS_URL", ""),
		JWTSecret:       c.String("JWT_SECRET", ""),
		JWTIssuer:       c.String("JWT_ISSUER", ""),
		Version:         c.String("SERVICE_VERSION", "dev"),
	}
}
