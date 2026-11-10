package apperr

import (
	"errors"
	"fmt"
	"net/http"
	"testing"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
)

func TestMapping(t *testing.T) {
	wrapped := fmt.Errorf("ctx: %w", NotFound("tax class %d", 7))
	var he *httpx.Error
	if !errors.As(ToHTTP(wrapped), &he) || he.Code != http.StatusNotFound {
		t.Fatalf("want 404, got %v", ToHTTP(wrapped))
	}
	if status.Code(ToGRPC(Invalid("x"))) != codes.InvalidArgument {
		t.Fatal("want InvalidArgument")
	}
	if status.Code(ToGRPC(errors.New("db down"))) != codes.Internal {
		t.Fatal("want Internal")
	}
	if ToHTTP(nil) != nil || ToGRPC(nil) != nil {
		t.Fatal("nil must stay nil")
	}
}
