package carrier

import (
	"context"
	"regexp"
	"testing"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/domain"
)

func TestTrackingFormat(t *testing.T) {
	seen := map[string]bool{}
	for range 200 {
		tr, err := Internal{}.CreateLabel(context.Background(), domain.Shipment{})
		if err != nil || !regexp.MustCompile(`^CT\d{4}[2-9A-HJ-NP-Z]{8}$`).MatchString(tr) || seen[tr] {
			t.Fatalf("%q %v", tr, err)
		}
		seen[tr] = true
	}
}
