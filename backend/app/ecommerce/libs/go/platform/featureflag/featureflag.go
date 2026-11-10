// Package featureflag: feature flag qua OpenFeature, provider Unleash.
// Không cấu hình UNLEASH_URL thì mọi flag trả về giá trị mặc định do code truyền vào.
package featureflag

import (
	"context"
	"net/http"

	"github.com/Unleash/unleash-client-go/v4"
	unleashprovider "github.com/open-feature/go-sdk-contrib/providers/unleash/pkg"
	"github.com/open-feature/go-sdk/openfeature"
)

type Config struct {
	URL     string // vd. http://unleash:4242/api
	Token   string
	AppName string
}

// Init đăng ký provider toàn cục. Gọi một lần trong main.
func Init(c Config) error {
	if c.URL == "" {
		return nil
	}
	p, err := unleashprovider.NewProvider(unleashprovider.ProviderConfig{
		Options: []unleash.ConfigOption{
			unleash.WithAppName(c.AppName),
			unleash.WithUrl(c.URL),
			unleash.WithCustomHeaders(http.Header{"Authorization": {c.Token}}),
		},
	})
	if err != nil {
		return err
	}
	return openfeature.SetProviderAndWait(p)
}

// Bool đọc flag boolean; lỗi hoặc chưa có provider → def.
func Bool(ctx context.Context, key string, def bool, userID string) bool {
	ec := openfeature.NewEvaluationContext(userID, nil)
	v, _ := openfeature.NewDefaultClient().BooleanValue(ctx, key, def, ec)
	return v
}
