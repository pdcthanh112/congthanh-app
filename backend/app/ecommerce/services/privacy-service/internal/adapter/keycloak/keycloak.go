// Package keycloak xoá tài khoản qua Keycloak Admin REST API bằng service account (client credentials)
// có quyền realm-management/manage-users.
package keycloak

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

type Client struct {
	baseURL, realm, clientID, secret string
	http                             *http.Client

	mu      sync.Mutex
	token   string
	expires time.Time
}

func New(baseURL, realm, clientID, secret string) *Client {
	return &Client{baseURL: strings.TrimRight(baseURL, "/"), realm: realm, clientID: clientID, secret: secret,
		http: &http.Client{Timeout: 10 * time.Second}}
}

func (c *Client) accessToken(ctx context.Context) (string, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.token != "" && time.Now().Before(c.expires) {
		return c.token, nil
	}
	form := url.Values{"grant_type": {"client_credentials"}, "client_id": {c.clientID}, "client_secret": {c.secret}}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		fmt.Sprintf("%s/realms/%s/protocol/openid-connect/token", c.baseURL, url.PathEscape(c.realm)),
		strings.NewReader(form.Encode()))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	res, err := c.http.Do(req)
	if err != nil {
		return "", err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return "", fmt.Errorf("keycloak token: status %d", res.StatusCode)
	}
	var body struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
	}
	if err := json.NewDecoder(res.Body).Decode(&body); err != nil {
		return "", err
	}
	c.token, c.expires = body.AccessToken, time.Now().Add(time.Duration(max(body.ExpiresIn-30, 10))*time.Second)
	return c.token, nil
}

// DeleteUser xoá user theo id (claim sub của Keycloak). 404 = đã xoá.
func (c *Client) DeleteUser(ctx context.Context, subjectID string) error {
	tok, err := c.accessToken(ctx)
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodDelete,
		fmt.Sprintf("%s/admin/realms/%s/users/%s", c.baseURL, url.PathEscape(c.realm), url.PathEscape(subjectID)), nil)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+tok)
	res, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode == http.StatusNoContent || res.StatusCode == http.StatusNotFound {
		return nil
	}
	return fmt.Errorf("keycloak delete user: status %d", res.StatusCode)
}
