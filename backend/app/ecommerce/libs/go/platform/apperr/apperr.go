// Package apperr là lỗi nghiệp vụ dùng chung cho service Go mới (tax, search, webhook, media, shipping):
// domain trả lỗi có Kind, adapter đổi sang HTTP status hoặc gRPC code bằng ToHTTP/ToGRPC.
// cart-service và inventory-service giữ bản riêng trong domain của chúng.
package apperr

import (
	"context"
	"errors"
	"fmt"
	"net/http"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
)

type Kind int

const (
	KindInvalid Kind = iota + 1
	KindNotFound
	KindConflict
	KindForbidden
	KindUnavailable
)

type Error struct {
	Kind Kind
	Msg  string
}

func (e *Error) Error() string { return e.Msg }

func Invalid(format string, a ...any) error  { return &Error{KindInvalid, fmt.Sprintf(format, a...)} }
func NotFound(format string, a ...any) error { return &Error{KindNotFound, fmt.Sprintf(format, a...)} }
func Conflict(format string, a ...any) error { return &Error{KindConflict, fmt.Sprintf(format, a...)} }
func Forbidden(format string, a ...any) error {
	return &Error{KindForbidden, fmt.Sprintf(format, a...)}
}
func Unavailable(format string, a ...any) error {
	return &Error{KindUnavailable, fmt.Sprintf(format, a...)}
}

// KindOf trả Kind của lỗi nghiệp vụ, 0 nếu là lỗi hạ tầng.
func KindOf(err error) Kind {
	var e *Error
	if errors.As(err, &e) {
		return e.Kind
	}
	return 0
}

// ToHTTP đổi lỗi nghiệp vụ sang *httpx.Error; lỗi khác giữ nguyên (httpx.Fail trả 500, không lộ chi tiết).
func ToHTTP(err error) error {
	if err == nil {
		return nil
	}
	code := map[Kind]int{
		KindInvalid: http.StatusBadRequest, KindNotFound: http.StatusNotFound, KindConflict: http.StatusConflict,
		KindForbidden: http.StatusForbidden, KindUnavailable: http.StatusServiceUnavailable,
	}[KindOf(err)]
	if code == 0 {
		return err
	}
	return &httpx.Error{Code: code, Message: err.Error()}
}

// ToGRPC đổi lỗi sang status gRPC.
func ToGRPC(err error) error {
	if err == nil {
		return nil
	}
	switch KindOf(err) {
	case KindInvalid:
		return status.Error(codes.InvalidArgument, err.Error())
	case KindNotFound:
		return status.Error(codes.NotFound, err.Error())
	case KindConflict:
		return status.Error(codes.FailedPrecondition, err.Error())
	case KindForbidden:
		return status.Error(codes.PermissionDenied, err.Error())
	case KindUnavailable:
		return status.Error(codes.Unavailable, err.Error())
	}
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return status.FromContextError(err).Err()
	}
	return status.Error(codes.Internal, "internal error")
}

// Handler là httpx.Handler có đổi lỗi nghiệp vụ sang HTTP status.
type Handler func(w http.ResponseWriter, r *http.Request) error

func (h Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	httpx.Handler(func(w http.ResponseWriter, r *http.Request) error { return ToHTTP(h(w, r)) }).ServeHTTP(w, r)
}
