package server

import (
	"context"
	"log/slog"
	"runtime/debug"
	"strings"
	"time"

	"go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/health"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/reflection"
	"google.golang.org/grpc/status"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
)

// NewGRPCServer tạo server có sẵn: log, recover panic, xác thực JWT (nếu bật),
// health check và reflection.
func NewGRPCServer(v *auth.Verifier) (*grpc.Server, *health.Server) {
	s := grpc.NewServer(
		grpc.StatsHandler(otelgrpc.NewServerHandler()),
		grpc.ChainUnaryInterceptor(recoverUnary, logUnary, authUnary(v)),
	)
	hs := health.NewServer()
	healthpb.RegisterHealthServer(s, hs)
	reflection.Register(s)
	return s, hs
}

func recoverUnary(ctx context.Context, req any, info *grpc.UnaryServerInfo, h grpc.UnaryHandler) (resp any, err error) {
	defer func() {
		if r := recover(); r != nil {
			slog.ErrorContext(ctx, "grpc panic", "method", info.FullMethod, "panic", r, "stack", string(debug.Stack()))
			err = status.Error(codes.Internal, "internal error")
		}
	}()
	return h(ctx, req)
}

func logUnary(ctx context.Context, req any, info *grpc.UnaryServerInfo, h grpc.UnaryHandler) (any, error) {
	start := time.Now()
	resp, err := h(ctx, req)
	slog.InfoContext(ctx, "grpc", "method", info.FullMethod, "code", status.Code(err).String(), "duration", time.Since(start))
	return resp, err
}

// authUnary: gRPC nội bộ giữa các service; nếu client gửi token thì kiểm tra,
// không gửi thì cho qua (mạng nội bộ). Health/reflection luôn được bỏ qua.
func authUnary(v *auth.Verifier) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, h grpc.UnaryHandler) (any, error) {
		if v == nil || strings.HasPrefix(info.FullMethod, "/grpc.") {
			return h(ctx, req)
		}
		md, _ := metadata.FromIncomingContext(ctx)
		if vals := md.Get("authorization"); len(vals) > 0 {
			raw, ok := auth.BearerToken(vals[0])
			if !ok {
				return nil, status.Error(codes.Unauthenticated, "invalid authorization header")
			}
			p, err := v.Verify(raw)
			if err != nil {
				return nil, status.Error(codes.Unauthenticated, "invalid token")
			}
			ctx = auth.WithPrincipal(ctx, p)
		}
		return h(ctx, req)
	}
}
