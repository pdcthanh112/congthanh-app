// Package auth xác thực JWT cho HTTP và gRPC.
//
// Hỗ trợ hai chế độ (chọn theo cấu hình):
//   - JWKS (Keycloak / OIDC): kiểm tra chữ ký RS256/ES256 bằng khoá công khai lấy từ JWKS URL.
//   - HMAC: secret dùng chung (tương thích token do auth-service cũ phát hành).
//
// Thiếu cấu hình thì NewVerifier trả lỗi để service không khởi động (fail-closed): gõ sai một biến môi trường không
// được phép tắt toàn bộ xác thực. Chỉ khi đặt rõ Disabled (AUTH_DISABLED=true, dùng cho dev/test) thì Verifier là
// nil và mọi middleware cho qua.
package auth

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
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

func (p Principal) HasAnyRole(roles ...string) bool {
	return slices.ContainsFunc(roles, p.HasRole)
}

type Config struct {
	JWKSURL    string
	HMACSecret string
	Issuer     string
	Audience   string
	// Disabled tắt xác thực (AUTH_DISABLED=true). Chỉ dùng cho dev/test; production phải có JWKSURL hoặc HMACSecret.
	Disabled bool
}

// ErrNotConfigured: không có JWKS/HMAC mà cũng không bật Disabled.
var ErrNotConfigured = errors.New("auth is not configured: set JWT_JWKS_URL or JWT_SECRET (or AUTH_DISABLED=true for local dev only)")

type Verifier struct {
	keyfunc jwt.Keyfunc
	opts    []jwt.ParserOption
}

// NewVerifier trả về nil, nil chỉ khi c.Disabled; thiếu cấu hình mà không Disabled thì trả ErrNotConfigured.
func NewVerifier(ctx context.Context, c Config) (*Verifier, error) {
	v := &Verifier{}
	switch {
	case c.Disabled:
		slog.Warn("JWT verification is DISABLED (AUTH_DISABLED=true): every request is allowed; never use in production")
		return nil, nil
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
		return nil, ErrNotConfigured
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

// RequireAuth chặn request chưa đăng nhập. Khi auth bị tắt rõ ràng (Verifier nil) thì cho qua.
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

// RequireRole chặn request không có ít nhất một trong các role (không phân biệt hoa thường, bỏ tiền tố ROLE_).
// Chưa đăng nhập → 401, sai role → 403. Đặt sau Middleware; tự kiểm tra đăng nhập nên không cần RequireAuth.
func RequireRole(v *Verifier, roles ...string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if v != nil {
				p, ok := FromContext(r.Context())
				if !ok {
					unauthorized(w, "Authentication required")
					return
				}
				if !p.HasAnyRole(roles...) {
					forbidden(w, "requires one of roles: "+strings.Join(roles, ", "))
					return
				}
			}
			next.ServeHTTP(w, r)
		})
	}
}

// Authorize là bản kiểm tra role dùng trong handler (khi không gắn được middleware theo route).
func Authorize(ctx context.Context, v *Verifier, roles ...string) error {
	if v == nil {
		return nil
	}
	p, ok := FromContext(ctx)
	if !ok {
		return ErrNoToken
	}
	if !p.HasAnyRole(roles...) {
		return ErrForbidden
	}
	return nil
}

var ErrForbidden = errors.New("insufficient role")

func forbidden(w http.ResponseWriter, msg string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusForbidden)
	_, _ = fmt.Fprintf(w, `{"message":%q,"data":null,"status":"Failed","errorCode":403}`, msg)
}

func unauthorized(w http.ResponseWriter, msg string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusUnauthorized)
	_, _ = fmt.Fprintf(w, `{"message":%q,"data":null,"status":"Failed","errorCode":401}`, msg)
}
