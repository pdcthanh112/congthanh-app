// Package carrier chứa adapter hãng vận chuyển. "internal" tự sinh mã vận đơn (dev, hoặc kho tự giao).
// Thêm USPS/UPS/FedEx (trực tiếp hoặc qua EasyPost/Shippo): viết adapter thoả app.Carrier và đăng ký ở main.
package carrier

import (
	"context"
	"crypto/rand"
	"fmt"
	"math/big"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/domain"
)

type Internal struct{}

func (Internal) Code() string { return "internal" }

// CreateLabel sinh mã dạng CT2611XXXXXXXX (tiền tố, năm-tháng, 8 ký tự ngẫu nhiên không nhầm lẫn).
func (Internal) CreateLabel(_ context.Context, _ domain.Shipment) (string, error) {
	const alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
	b := make([]byte, 8)
	for i := range b {
		n, err := rand.Int(rand.Reader, big.NewInt(int64(len(alphabet))))
		if err != nil {
			return "", err
		}
		b[i] = alphabet[n.Int64()]
	}
	return fmt.Sprintf("CT%s%s", time.Now().UTC().Format("0601"), b), nil
}
