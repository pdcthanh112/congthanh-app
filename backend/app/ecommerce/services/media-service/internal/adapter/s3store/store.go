// Package s3store là driven adapter Storage trên API S3 (AWS S3, Cloudflare R2, MinIO).
package s3store

import (
	"context"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	smithyhttp "github.com/aws/smithy-go/transport/http"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/app"
)

type Config struct {
	Bucket    string
	Region    string
	Endpoint  string // trống = AWS; MinIO/R2 đặt URL endpoint
	PathStyle bool   // MinIO cần path-style
	AccessKey string // trống = chuỗi credential mặc định của AWS (IRSA, env, profile)
	SecretKey string
	PublicURL string // tiền tố URL công khai (CDN), ví dụ https://cdn.example.com/
	// PresignEndpoint: endpoint trình duyệt truy cập được (compose: localhost:9000 thay vì minio:9000).
	PresignEndpoint string
}

type Store struct {
	cfg     Config
	client  *s3.Client
	presign *s3.PresignClient
}

func New(ctx context.Context, cfg Config) (*Store, error) {
	opts := []func(*awsconfig.LoadOptions) error{awsconfig.WithRegion(cfg.Region)}
	if cfg.AccessKey != "" {
		opts = append(opts, awsconfig.WithCredentialsProvider(credentials.NewStaticCredentialsProvider(cfg.AccessKey, cfg.SecretKey, "")))
	}
	awsCfg, err := awsconfig.LoadDefaultConfig(ctx, opts...)
	if err != nil {
		return nil, err
	}
	mk := func(endpoint string) *s3.Client {
		return s3.NewFromConfig(awsCfg, func(o *s3.Options) {
			if endpoint != "" {
				o.BaseEndpoint = aws.String(endpoint)
			}
			o.UsePathStyle = cfg.PathStyle
		})
	}
	client := mk(cfg.Endpoint)
	pe := cfg.PresignEndpoint
	if pe == "" {
		pe = cfg.Endpoint
	}
	return &Store{cfg: cfg, client: client, presign: s3.NewPresignClient(mk(pe))}, nil
}

// PresignPut ký URL PUT có ràng buộc Content-Type và Content-Length: storage từ chối file khác khai báo.
func (s *Store) PresignPut(ctx context.Context, key, contentType string, size int64, ttl time.Duration) (string, map[string]string, error) {
	req, err := s.presign.PresignPutObject(ctx, &s3.PutObjectInput{
		Bucket: aws.String(s.cfg.Bucket), Key: aws.String(key),
		ContentType: aws.String(contentType), ContentLength: aws.Int64(size),
		CacheControl: aws.String("public, max-age=31536000, immutable"),
	}, s3.WithPresignExpires(ttl))
	if err != nil {
		return "", nil, err
	}
	headers := map[string]string{}
	for k, v := range req.SignedHeader {
		if k == "Host" || k == "Content-Length" || len(v) == 0 {
			continue
		}
		headers[k] = v[0]
	}
	return req.URL, headers, nil
}

func (s *Store) Stat(ctx context.Context, key string) (app.ObjectInfo, bool, error) {
	out, err := s.client.HeadObject(ctx, &s3.HeadObjectInput{Bucket: aws.String(s.cfg.Bucket), Key: aws.String(key)})
	if err != nil {
		var re *smithyhttp.ResponseError
		if errors.As(err, &re) && re.HTTPStatusCode() == http.StatusNotFound {
			return app.ObjectInfo{}, false, nil
		}
		return app.ObjectInfo{}, false, err
	}
	return app.ObjectInfo{Size: aws.ToInt64(out.ContentLength), ContentType: aws.ToString(out.ContentType)}, true, nil
}

func (s *Store) Remove(ctx context.Context, key string) error {
	_, err := s.client.DeleteObject(ctx, &s3.DeleteObjectInput{Bucket: aws.String(s.cfg.Bucket), Key: aws.String(key)})
	return err
}

func (s *Store) PublicURL(key string) string {
	return strings.TrimRight(s.cfg.PublicURL, "/") + "/" + key
}

// Ping: bucket tồn tại và truy cập được.
func (s *Store) Ping(ctx context.Context) error {
	_, err := s.client.HeadBucket(ctx, &s3.HeadBucketInput{Bucket: aws.String(s.cfg.Bucket)})
	return err
}
