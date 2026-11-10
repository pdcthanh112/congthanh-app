// Package httpx chứa envelope phản hồi và lỗi HTTP dùng chung, giữ đúng format
// {message, data, status, errorCode} mà các service Java cũ trả về.
package httpx

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
)

const (
	StatusSuccess = "Success"
	StatusFailed  = "Failed"
)

type Response[T any] struct {
	Message   string `json:"message"`
	Data      T      `json:"data"`
	Status    string `json:"status"`
	ErrorCode int    `json:"errorCode"`
}

// Error là lỗi nghiệp vụ mang sẵn HTTP status.
type Error struct {
	Code    int
	Message string
}

func (e *Error) Error() string { return e.Message }

func NotFound(msg string) error   { return &Error{http.StatusNotFound, msg} }
func BadRequest(msg string) error { return &Error{http.StatusBadRequest, msg} }
func Conflict(msg string) error   { return &Error{http.StatusConflict, msg} }
func Forbidden(msg string) error  { return &Error{http.StatusForbidden, msg} }

func JSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func OK[T any](w http.ResponseWriter, code int, msg string, data T) {
	JSON(w, code, Response[T]{Message: msg, Data: data, Status: StatusSuccess})
}

// Fail ghi lỗi; lỗi không phải *Error được coi là 500 và không lộ chi tiết ra ngoài.
func Fail(w http.ResponseWriter, r *http.Request, err error) {
	var he *Error
	if !errors.As(err, &he) {
		slog.ErrorContext(r.Context(), "unhandled error", "path", r.URL.Path, "err", err)
		he = &Error{http.StatusInternalServerError, "Internal server error"}
	}
	JSON(w, he.Code, Response[any]{Message: he.Message, Status: StatusFailed, ErrorCode: he.Code})
}

// Decode đọc JSON body (tối đa 1MB), từ chối field lạ.
func Decode(r *http.Request, v any) error {
	dec := json.NewDecoder(http.MaxBytesReader(nil, r.Body, 1<<20))
	dec.DisallowUnknownFields()
	if err := dec.Decode(v); err != nil {
		return BadRequest("Invalid request body: " + err.Error())
	}
	return nil
}

// Handler cho phép handler trả error thay vì tự ghi lỗi.
type Handler func(w http.ResponseWriter, r *http.Request) error

func (h Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if err := h(w, r); err != nil {
		Fail(w, r, err)
	}
}
