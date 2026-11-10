package http

import (
	"net/http"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/httpx"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
)

func toHTTP(err error) error {
	switch domain.KindOf(err) {
	case domain.KindInvalid:
		return httpx.BadRequest(err.Error())
	case domain.KindNotFound:
		return httpx.NotFound(err.Error())
	case domain.KindConflict:
		return httpx.Conflict(err.Error())
	case domain.KindUnavailable:
		return &httpx.Error{Code: http.StatusServiceUnavailable, Message: err.Error()}
	}
	return err
}
