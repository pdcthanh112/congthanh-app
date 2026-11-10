// Package grpcx tạo kết nối gRPC client có sẵn OTel, timeout mặc định và chuyển tiếp token.
package grpcx

import (
	"context"
	"time"

	"go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
)

// Dial tạo kết nối lười. Trong cluster, mTLS do Istio Ambient lo nên client dùng plaintext.
func Dial(addr string) (*grpc.ClientConn, error) {
	return grpc.NewClient(addr,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithStatsHandler(otelgrpc.NewClientHandler()),
		grpc.WithUnaryInterceptor(unary),
	)
}

type tokenKey struct{}

// WithToken lưu Authorization header của request gốc để chuyển tiếp sang service khác.
func WithToken(ctx context.Context, header string) context.Context {
	if header == "" {
		return ctx
	}
	return context.WithValue(ctx, tokenKey{}, header)
}

func unary(ctx context.Context, method string, req, reply any, cc *grpc.ClientConn, invoker grpc.UnaryInvoker, opts ...grpc.CallOption) error {
	if h, ok := ctx.Value(tokenKey{}).(string); ok {
		ctx = metadata.AppendToOutgoingContext(ctx, "authorization", h)
	}
	if _, has := ctx.Deadline(); !has {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, 3*time.Second)
		defer cancel()
	}
	return invoker(ctx, method, req, reply, cc, opts...)
}
