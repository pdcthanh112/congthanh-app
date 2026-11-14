package keycloak

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestDeleteUserUsesClientCredentialsAndTreats404AsDone(t *testing.T) {
	var deleted []string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case strings.HasSuffix(r.URL.Path, "/protocol/openid-connect/token"):
			_ = r.ParseForm()
			if r.Form.Get("grant_type") != "client_credentials" || r.Form.Get("client_secret") != "s3cret" {
				w.WriteHeader(http.StatusUnauthorized)
				return
			}
			_, _ = w.Write([]byte(`{"access_token":"tok","expires_in":300}`))
		case r.Method == http.MethodDelete && r.Header.Get("Authorization") == "Bearer tok":
			deleted = append(deleted, r.URL.Path)
			if strings.HasSuffix(r.URL.Path, "/gone") {
				w.WriteHeader(http.StatusNotFound)
				return
			}
			w.WriteHeader(http.StatusNoContent)
		default:
			w.WriteHeader(http.StatusForbidden)
		}
	}))
	defer srv.Close()

	c := New(srv.URL, "ecommerce", "privacy-service", "s3cret")
	if err := c.DeleteUser(context.Background(), "u-1"); err != nil {
		t.Fatal(err)
	}
	if err := c.DeleteUser(context.Background(), "gone"); err != nil {
		t.Fatalf("404 must count as deleted: %v", err)
	}
	if len(deleted) != 2 || deleted[0] != "/admin/realms/ecommerce/users/u-1" {
		t.Fatalf("deleted = %v", deleted)
	}
}
