// Package domain là lõi nghiệp vụ giỏ hàng: không phụ thuộc DB, HTTP, gRPC hay Kafka.
package domain

import (
	"errors"
	"fmt"
)

type Kind int

const (
	KindInvalid Kind = iota + 1
	KindNotFound
	KindConflict
	KindUnavailable // service phụ thuộc không phản hồi
)

// Error là lỗi nghiệp vụ; adapter đổi sang HTTP status / gRPC code tương ứng.
type Error struct {
	Kind Kind
	Msg  string
}

func (e *Error) Error() string { return e.Msg }

func Invalid(format string, a ...any) error  { return &Error{KindInvalid, fmt.Sprintf(format, a...)} }
func NotFound(format string, a ...any) error { return &Error{KindNotFound, fmt.Sprintf(format, a...)} }
func Conflict(format string, a ...any) error { return &Error{KindConflict, fmt.Sprintf(format, a...)} }
func Unavailable(format string, a ...any) error {
	return &Error{KindUnavailable, fmt.Sprintf(format, a...)}
}

// KindOf trả Kind của lỗi nghiệp vụ, 0 nếu là lỗi hạ tầng.
func KindOf(err error) Kind {
	var de *Error
	if errors.As(err, &de) {
		return de.Kind
	}
	return 0
}
