// Package domain: quy tắc file tải lên theo mục đích (loại, dung lượng) và vòng đời PENDING → READY.
package domain

import (
	"fmt"
	"path"
	"strings"
	"time"
	"unicode"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
)

type Purpose string

const (
	PurposeProductImage Purpose = "PRODUCT_IMAGE"
	PurposeReview       Purpose = "REVIEW"
	PurposeAvatar       Purpose = "AVATAR"
	PurposeChat         Purpose = "CHAT"
)

type Status string

const (
	StatusPending Status = "PENDING"
	StatusReady   Status = "READY"
)

type Rule struct {
	MaxBytes int64
	Types    map[string]bool
	// AdminOnly: chỉ ADMIN/SELLER được tải (ảnh sản phẩm).
	SellerOnly bool
}

var images = map[string]bool{"image/jpeg": true, "image/png": true, "image/webp": true, "image/avif": true}

var Rules = map[Purpose]Rule{
	PurposeProductImage: {MaxBytes: 10 << 20, Types: images, SellerOnly: true},
	PurposeReview:       {MaxBytes: 50 << 20, Types: merge(images, map[string]bool{"video/mp4": true, "video/webm": true})},
	PurposeAvatar:       {MaxBytes: 5 << 20, Types: images},
	PurposeChat:         {MaxBytes: 20 << 20, Types: merge(images, map[string]bool{"application/pdf": true})},
}

func merge(a, b map[string]bool) map[string]bool {
	out := map[string]bool{}
	for k := range a {
		out[k] = true
	}
	for k := range b {
		out[k] = true
	}
	return out
}

type Media struct {
	ID          string     `json:"id"`
	OwnerID     string     `json:"ownerId"`
	Purpose     Purpose    `json:"purpose"`
	ObjectKey   string     `json:"objectKey"`
	FileName    string     `json:"fileName"`
	ContentType string     `json:"contentType"`
	SizeBytes   int64      `json:"sizeBytes"`
	Status      Status     `json:"status"`
	CreatedAt   time.Time  `json:"createdAt"`
	CompletedAt *time.Time `json:"completedAt,omitempty"`
	URL         string     `json:"url,omitempty"`
}

type UploadRequest struct {
	OwnerID     string
	IsSeller    bool
	Purpose     Purpose
	FileName    string
	ContentType string
	SizeBytes   int64
}

// Validate kiểm tra loại file và dung lượng theo mục đích.
func (r UploadRequest) Validate() error {
	rule, ok := Rules[r.Purpose]
	if !ok {
		return apperr.Invalid("purpose must be one of PRODUCT_IMAGE, REVIEW, AVATAR, CHAT")
	}
	if rule.SellerOnly && !r.IsSeller {
		return apperr.Forbidden("only sellers or admins can upload %s", r.Purpose)
	}
	if !rule.Types[strings.ToLower(r.ContentType)] {
		return apperr.Invalid("content type %q is not allowed for %s", r.ContentType, r.Purpose)
	}
	if r.SizeBytes <= 0 || r.SizeBytes > rule.MaxBytes {
		return apperr.Invalid("size must be between 1 byte and %d MB", rule.MaxBytes>>20)
	}
	if strings.TrimSpace(r.FileName) == "" || len(r.FileName) > 255 {
		return apperr.Invalid("fileName is required (max 255 characters)")
	}
	return nil
}

var extByType = map[string]string{
	"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp", "image/avif": ".avif",
	"video/mp4": ".mp4", "video/webm": ".webm", "application/pdf": ".pdf",
}

// ObjectKey: <purpose>/<yyyy>/<mm>/<id><ext>. Tên gốc của người dùng không nằm trong key
// (tránh path traversal và lộ thông tin); phần mở rộng lấy theo content type.
func ObjectKey(p Purpose, id, contentType string, at time.Time) string {
	return fmt.Sprintf("%s/%04d/%02d/%s%s", strings.ToLower(string(p)), at.Year(), at.Month(), id, extByType[strings.ToLower(contentType)])
}

// SafeFileName chỉ giữ tên file (bỏ thư mục) và ký tự in được, dùng cho Content-Disposition.
func SafeFileName(name string) string {
	name = path.Base(strings.ReplaceAll(name, "\\", "/"))
	return strings.Map(func(r rune) rune {
		if unicode.IsPrint(r) && r != '"' && r != '/' {
			return r
		}
		return -1
	}, name)
}

// CanManage: chủ file hoặc ADMIN.
func (m Media) CanManage(subject string, admin bool) bool { return admin || m.OwnerID == subject }
