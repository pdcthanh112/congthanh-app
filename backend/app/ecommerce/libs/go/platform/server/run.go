// Package server chạy HTTP và gRPC song song, tắt êm khi nhận SIGINT/SIGTERM.
package server

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"os/signal"
	"syscall"
	"time"

	"golang.org/x/sync/errgroup"
	"google.golang.org/grpc"
)

type Options struct {
	HTTPAddr        string
	HTTPHandler     http.Handler
	GRPCAddr        string
	GRPCServer      *grpc.Server // nil = không mở gRPC
	ShutdownTimeout time.Duration
	// Background chạy cùng vòng đời server (vd. job dọn reservation hết hạn).
	Background []func(ctx context.Context) error
}

func Run(ctx context.Context, o Options) error {
	ctx, stop := signal.NotifyContext(ctx, syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	if o.ShutdownTimeout == 0 {
		o.ShutdownTimeout = 20 * time.Second
	}
	g, gctx := errgroup.WithContext(ctx)

	srv := &http.Server{
		Addr:              o.HTTPAddr,
		Handler:           o.HTTPHandler,
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       120 * time.Second,
	}
	g.Go(func() error {
		slog.Info("HTTP listening", "addr", o.HTTPAddr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			return err
		}
		return nil
	})

	if o.GRPCServer != nil {
		lis, err := net.Listen("tcp", o.GRPCAddr)
		if err != nil {
			return err
		}
		g.Go(func() error {
			slog.Info("gRPC listening", "addr", o.GRPCAddr)
			return o.GRPCServer.Serve(lis)
		})
	}

	for _, bg := range o.Background {
		g.Go(func() error { return bg(gctx) })
	}

	g.Go(func() error {
		<-gctx.Done()
		slog.Info("shutting down")
		sctx, cancel := context.WithTimeout(context.Background(), o.ShutdownTimeout)
		defer cancel()
		if o.GRPCServer != nil {
			done := make(chan struct{})
			go func() { o.GRPCServer.GracefulStop(); close(done) }()
			select {
			case <-done:
			case <-sctx.Done():
				o.GRPCServer.Stop()
			}
		}
		return srv.Shutdown(sctx)
	})
	return g.Wait()
}
