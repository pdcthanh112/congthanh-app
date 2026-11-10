// Package delivery gửi HTTP POST tới URL của bên nhận, có ký HMAC và chặn SSRF.
package delivery

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"syscall"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/domain"
)

var ErrBlockedAddress = errors.New("destination address is not allowed")

type Sender struct {
	client *http.Client
	now    func() time.Time
}

// New: allowPrivate=false chặn kết nối tới IP nội bộ (loopback, private, link-local, metadata cloud
// 169.254.169.254...) kiểm ở lúc dial, sau khi DNS đã phân giải, nên đổi DNS (rebinding) cũng không lọt.
func New(timeout time.Duration, allowPrivate bool) *Sender {
	dialer := &net.Dialer{Timeout: 5 * time.Second}
	if !allowPrivate {
		dialer.Control = func(_, address string, _ syscall.RawConn) error {
			host, _, err := net.SplitHostPort(address)
			if err != nil {
				return err
			}
			ip, err := netip.ParseAddr(host)
			if err != nil || !Public(ip) {
				return ErrBlockedAddress
			}
			return nil
		}
	}
	tr := &http.Transport{
		DialContext: dialer.DialContext, ForceAttemptHTTP2: true, MaxIdleConnsPerHost: 4,
		TLSHandshakeTimeout: 5 * time.Second, ResponseHeaderTimeout: timeout, Proxy: nil,
	}
	return &Sender{
		client: &http.Client{Timeout: timeout, Transport: tr,
			// Không theo redirect: bên nhận phải trả 2xx tại đúng URL đã đăng ký.
			CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }},
		now: time.Now,
	}
}

// Public: địa chỉ định tuyến được trên Internet công cộng.
func Public(ip netip.Addr) bool {
	ip = ip.Unmap()
	if ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() ||
		ip.IsMulticast() || ip.IsUnspecified() || ip.IsInterfaceLocalMulticast() {
		return false
	}
	for _, p := range blocked {
		if p.Contains(ip) {
			return false
		}
	}
	return true
}

var blocked = []netip.Prefix{
	netip.MustParsePrefix("100.64.0.0/10"), // CGNAT
	netip.MustParsePrefix("192.0.0.0/24"),
	netip.MustParsePrefix("198.18.0.0/15"),
	netip.MustParsePrefix("240.0.0.0/4"),
	netip.MustParsePrefix("64:ff9b::/96"),
}

func (s *Sender) Send(ctx context.Context, w domain.Webhook, d domain.Delivery) app.Result {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, w.URL, bytes.NewReader(d.Payload))
	if err != nil {
		return app.Result{Err: err}
	}
	ts := s.now()
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", "CongThanhApp-Webhook/1.0")
	req.Header.Set("X-Webhook-Id", d.EventID)
	req.Header.Set("X-Webhook-Delivery", d.ID)
	req.Header.Set("X-Webhook-Event", d.EventType)
	req.Header.Set("X-Webhook-Timestamp", fmt.Sprint(ts.Unix()))
	req.Header.Set("X-Webhook-Signature", domain.Sign(w.Secret, ts, d.Payload))
	req.Header.Set("X-Hub-Signature-256", domain.SignLegacy(w.Secret, d.Payload))
	res, err := s.client.Do(req)
	if err != nil {
		return app.Result{Err: err}
	}
	defer res.Body.Close()
	_, _ = io.Copy(io.Discard, io.LimitReader(res.Body, 64<<10))
	return app.Result{StatusCode: res.StatusCode}
}
