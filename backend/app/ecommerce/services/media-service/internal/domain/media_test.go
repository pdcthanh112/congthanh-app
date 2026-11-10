package domain

import (
	"testing"
	"time"
)

func TestUploadValidate(t *testing.T) {
	ok := UploadRequest{OwnerID: "u1", Purpose: PurposeReview, FileName: "a.mp4", ContentType: "video/mp4", SizeBytes: 1 << 20}
	if err := ok.Validate(); err != nil {
		t.Fatal(err)
	}
	bad := []UploadRequest{
		{Purpose: "X", FileName: "a", ContentType: "image/png", SizeBytes: 1},
		{Purpose: PurposeAvatar, FileName: "a", ContentType: "image/svg+xml", SizeBytes: 1},
		{Purpose: PurposeAvatar, FileName: "a", ContentType: "image/png", SizeBytes: 6 << 20},
		{Purpose: PurposeProductImage, FileName: "a", ContentType: "image/png", SizeBytes: 1},
	}
	for _, b := range bad {
		if b.Validate() == nil {
			t.Errorf("%+v must fail", b)
		}
	}
}

func TestObjectKeyAndName(t *testing.T) {
	k := ObjectKey(PurposeReview, "abc", "image/webp", time.Date(2026, 3, 5, 0, 0, 0, 0, time.UTC))
	if k != "review/2026/03/abc.webp" {
		t.Fatal(k)
	}
	if got := SafeFileName(`..\..\etc/pa"ss wd.png`); got != "pass wd.png" {
		t.Fatal(got)
	}
}
