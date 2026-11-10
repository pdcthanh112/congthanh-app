// Package auth xác thực JWT cho HTTP và gRPC.
//
// Hỗ trợ hai chế độ (chọn theo cấu hình):
//   - JWKS (Keycloak / OIDC): kiểm tra chữ ký RS256/ES256 bằng khoá công khai lấy từ JWKS URL.
//   - HMAC: secret dùng chung (tương thích token do auth-service cũ phát hành).
//
// Không cấu hình gì thì Verifier là nil và middleware cho qua (chỉ dùng khi dev).
package auth

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"slices"
	"strings"

	"github.com/MicahParks/keyfunc/v3"
	"github.com/golang-jwt/jwt/v5"
)

type Principal struct {
	Subject string
	Roles   []string
}

func (p Principal) HasRole(role string) bool {
	return slices.ContainsFunc(p.Roles, func(r string) bool {
		return strings.EqualFold(strings.TrimPrefix(r, "ROLE_"), strings.TrimPrefix(role, "ROLE_"))
	})
}

type Config struct {
	JWKSURL    string
	HMACSecret string
	Issuer     string
	Audience   string
}

type Verifier struct {
	keyfunc jwt.Keyfunc
	opts    []jwt.ParserOption
}

// NewVerifier trả về nil, nil khi chưa cấu hình JWKS hay HMAC.
func NewVerifier(ctx context.Context, c Config) (*Verifier, error) {
	v := &Verifier{}
	switch {
	case c.JWKSURL != "":
		k, err := keyfunc.NewDefaultCtx(ctx, []string{c.JWKSURL})
		if err != nil {
			return nil, fmt.Errorf("load JWKS: %w", err)
		}
		v.keyfunc = k.Keyfunc
		v.opts = append(v.opts, jwt.WithValidMethods([]string{"RS256", "RS384", "RS512", "ES256", "ES384", "PS256"}))
	case c.HMACSecret != "":
		secret := []byte(c.HMACSecret)
		v.keyfunc = func(*jwt.Token) (any, error) { return secret, nil }
		v.opts = append(v.opts, jwt.WithValidMethods([]string{"HS256", "HS384", "HS512"}))
	default:
		return nil, nil
	}
	if c.Issuer != "" {
		v.opts = append(v.opts, jwt.WithIssuer(c.Issuer))
	}
	if c.Audience != "" {
		v.opts = append(v.opts, jwt.WithAudience(c.Audience))
	}
	v.opts = append(v.opts, jwt.WithExpirationRequired())
	return v, nil
}

var ErrNoToken = errors.New("missing bearer token")

func (v *Verifier) Verify(raw string) (Principal, error) {
	claims := jwt.MapClaims{}
	if _, err := jwt.ParseWithClaims(raw, claims, v.keyfunc, v.opts...); err != nil {
		return Principal{}, err
	}
	sub, _ := claims.GetSubject()
	return Principal{Subject: sub, Roles: extractRoles(claims)}, nil
}

// extractRoles gom role từ claim "role"/"roles" (auth-service cũ) và
// realm_access.roles / resource_access.*.roles (Keycloak).
func extractRoles(c jwt.MapClaims) []string {
	var out []string
	add := func(v any) {
		switch t := v.(type) {
		case string:
			out = append(out, t)
		case []any:
			for _, x := range t {
				if s, ok := x.(string); ok {
					out = append(out, s)
				}
			}
		}
	}
	add(c["role"])
	add(c["roles"])
	if ra, ok := c["realm_access"].(map[string]any); ok {
		add(ra["roles"])
	}
	if res, ok := c["resource_access"].(map[string]any); ok {
		for _, v := range res {
			if m, ok := v.(map[string]any); ok {
				add(m["roles"])
			}
		}
	}
	return out
}

func BearerToken(header string) (string, bool) {
	const p = "Bearer "
	if len(header) > len(p) && strings.EqualFold(header[:len(p)], p) {
		return strings.TrimSpace(header[len(p):]), true
	}
	return "", false
}

type ctxKey struct{}

func WithPrincipal(ctx context.Context, p Principal) context.Context {
	return context.WithValue(ctx, ctxKey{}, p)
}

func FromContext(ctx context.Context) (Principal, bool) {
	p, ok := ctx.Value(ctxKey{}).(Principal)
	return p, ok
}

// Middleware gắn Principal vào context nếu token hợp lệ; token sai → 401.
// Không có token thì cho qua; dùng RequireAuth cho route bắt buộc đăng nhập.
func Middleware(v *Verifier) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if v == nil {
				next.ServeHTTP(w, r)
				return
			}
			raw, ok := BearerToken(r.Header.Get("Authorization"))
			if !ok {
				next.ServeHTTP(w, r)
				return
			}
			p, err := v.Verify(raw)
			if err != nil {
				unauthorized(w, "Invalid or expired token")
				return
			}
			next.ServeHTTP(w, r.WithContext(WithPrincipal(r.Context(), p)))
		})
	}
}

// RequireAuth chặn request chưa đăng nhập. Khi auth bị tắt (Verifier nil) thì cho qua.
func RequireAuth(v *Verifier) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if v != nil {
				if _, ok := FromContext(r.Context()); !ok {
					unauthorized(w, "Authentication required")
					return
				}
			}
			next.ServeHTTP(w, r)
		})
	}
}

func unauthorized(w http.ResponseWriter, msg string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusUnauthorized)
	_, _ = fmt.Fprintf(w, `{"message":%q,"data":null,"status":"Failed","errorCode":401}`, msg)
}
