// Package domain chứa quy tắc quyền riêng tư (CCPA/CPRA và luật các bang Mỹ), không phụ thuộc hạ tầng.
package domain

import (
	"slices"
	"strings"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
)

// Purpose là mục đích xử lý dữ liệu mà khách đồng ý hoặc từ chối.
type Purpose string

const (
	PurposeSaleSharing    Purpose = "SALE_SHARING"    // CCPA "Do Not Sell or Share My Personal Information"
	PurposeTargetedAds    Purpose = "TARGETED_ADS"    // quảng cáo theo hành vi (luật VA/CO/CT gọi là targeted advertising)
	PurposeMarketingEmail Purpose = "MARKETING_EMAIL" // CAN-SPAM: được gửi cho tới khi khách opt-out, nhưng ta chọn opt-in
	PurposeMarketingSMS   Purpose = "MARKETING_SMS"   // TCPA: bắt buộc opt-in rõ ràng
	PurposeAnalytics      Purpose = "ANALYTICS"
)

// AllPurposes theo thứ tự hiển thị.
var AllPurposes = []Purpose{PurposeSaleSharing, PurposeTargetedAds, PurposeMarketingEmail, PurposeMarketingSMS, PurposeAnalytics}

// DefaultGranted: mô hình opt-out của CCPA cho chia sẻ/quảng cáo/phân tích; opt-in cho marketing (TCPA với SMS).
func DefaultGranted(p Purpose) bool {
	switch p {
	case PurposeMarketingEmail, PurposeMarketingSMS:
		return false
	default:
		return true
	}
}

func ParsePurpose(s string) (Purpose, error) {
	p := Purpose(strings.ToUpper(strings.TrimSpace(s)))
	if !slices.Contains(AllPurposes, p) {
		return "", apperr.Invalid("unknown consent purpose %q", s)
	}
	return p, nil
}

// Source cho biết lựa chọn đến từ đâu; GPC (Global Privacy Control) là tín hiệu trình duyệt, luật CA/CO bắt buộc tôn trọng.
type Source string

const (
	SourceBanner   Source = "BANNER"
	SourceSettings Source = "SETTINGS"
	SourceGPC      Source = "GPC"
	SourceSupport  Source = "SUPPORT"
	SourceRequest  Source = "REQUEST" // từ yêu cầu OPT_OUT
)

func ParseSource(s string) (Source, error) {
	src := Source(strings.ToUpper(strings.TrimSpace(s)))
	switch src {
	case SourceBanner, SourceSettings, SourceGPC, SourceSupport, SourceRequest:
		return src, nil
	case "":
		return SourceSettings, nil
	}
	return "", apperr.Invalid("unknown consent source %q", s)
}

// Consent là một lần ghi lựa chọn (chỉ ghi thêm, lịch sử dùng làm bằng chứng).
type Consent struct {
	SubjectID     string    `json:"subjectId"`
	Purpose       Purpose   `json:"purpose"`
	Granted       bool      `json:"granted"`
	Source        Source    `json:"source"`
	PolicyVersion string    `json:"policyVersion"`
	RecordedAt    time.Time `json:"recordedAt"`
}

// EffectiveConsents gộp lựa chọn mới nhất theo mục đích với mặc định.
func EffectiveConsents(subject string, latest map[Purpose]Consent) []Consent {
	out := make([]Consent, 0, len(AllPurposes))
	for _, p := range AllPurposes {
		if c, ok := latest[p]; ok {
			out = append(out, c)
			continue
		}
		out = append(out, Consent{SubjectID: subject, Purpose: p, Granted: DefaultGranted(p), Source: "DEFAULT"})
	}
	return out
}
