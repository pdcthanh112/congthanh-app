package s3store

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// Fake S3 tối giản: HEAD trả 404 cho key "missing", 200 kèm metadata cho key khác.
func TestStoreAgainstFakeS3(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasPrefix(r.Header.Get("Authorization"), "AWS4-HMAC-SHA256") {
			t.Errorf("request not signed: %s %s", r.Method, r.URL.Path)
		}
		switch {
		case r.Method == http.MethodHead && strings.HasSuffix(r.URL.Path, "/missing"):
			w.WriteHeader(http.StatusNotFound)
		case r.Method == http.MethodHead:
			w.Header().Set("Content-Length", "1234")
			w.Header().Set("Content-Type", "image/png")
		case r.Method == http.MethodDelete:
			w.WriteHeader(http.StatusNoContent)
		}
	}))
	defer srv.Close()
	ctx := context.Background()
	s, err := New(ctx, Config{Bucket: "media", Region: "us-east-1", Endpoint: srv.URL, PathStyle: true,
		AccessKey: "k", SecretKey: "s", PublicURL: "https://cdn.test/", PresignEndpoint: "http://localhost:9000"})
	if err != nil {
		t.Fatal(err)
	}
	url, headers, err := s.PresignPut(ctx, "avatar/2026/11/a.png", "image/png", 1234, 15*time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(url, "http://localhost:9000/media/avatar/2026/11/a.png?") || !strings.Contains(url, "X-Amz-Signature=") ||
		!strings.Contains(url, "X-Amz-Expires=900") || headers["Content-Type"] != "image/png" {
		t.Fatalf("presign: %s %v", url, headers)
	}
	if _, ok, err := s.Stat(ctx, "missing"); ok || err != nil {
		t.Fatalf("missing: ok=%v err=%v", ok, err)
	}
	info, ok, err := s.Stat(ctx, "x.png")
	if !ok || err != nil || info.Size != 1234 || info.ContentType != "image/png" {
		t.Fatalf("stat: %+v %v %v", info, ok, err)
	}
	if err := s.Remove(ctx, "x.png"); err != nil {
		t.Fatal(err)
	}
	if got := s.PublicURL("a/b.png"); got != "https://cdn.test/a/b.png" {
		t.Fatal(got)
	}
}
