package domain

import (
	"strings"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
)

// RequestType là loại yêu cầu của chủ thể dữ liệu (DSAR).
type RequestType string

const (
	RequestAccess RequestType = "ACCESS" // quyền được biết/nhận bản sao
	RequestDelete RequestType = "DELETE" // quyền yêu cầu xoá
	RequestOptOut RequestType = "OPT_OUT"
)

func ParseRequestType(s string) (RequestType, error) {
	t := RequestType(strings.ToUpper(strings.TrimSpace(s)))
	switch t {
	case RequestAccess, RequestDelete, RequestOptOut:
		return t, nil
	}
	return "", apperr.Invalid("unsupported request type %q (ACCESS, DELETE, OPT_OUT)", s)
}

type RequestStatus string

const (
	StatusInProgress         RequestStatus = "IN_PROGRESS"
	StatusCompleted          RequestStatus = "COMPLETED"
	StatusPartiallyCompleted RequestStatus = "PARTIALLY_COMPLETED"
	StatusRejected           RequestStatus = "REJECTED"
)

func (s RequestStatus) Open() bool { return s == StatusInProgress }

type TaskStatus string

const (
	TaskPending   TaskStatus = "PENDING"
	TaskCompleted TaskStatus = "COMPLETED"
	TaskRetained  TaskStatus = "RETAINED" // xử lý xong nhưng giữ lại một phần theo luật
)

// ResponseWindow: CCPA cho 45 ngày, gia hạn thêm 45 ngày một lần nếu báo khách lý do.
const ResponseWindow = 45 * 24 * time.Hour

// Task là phần việc của một processor (service hoặc hệ thống giữ dữ liệu cá nhân).
type Task struct {
	Processor       string     `json:"processor"`
	Status          TaskStatus `json:"status"`
	RetentionReason string     `json:"retentionReason,omitempty"`
	ObjectKey       string     `json:"objectKey,omitempty"`
	CompletedAt     *time.Time `json:"completedAt,omitempty"`
}

// Request là một yêu cầu DSAR và các task của nó.
type Request struct {
	ID              string        `json:"id"`
	SubjectID       string        `json:"subjectId"`
	Type            RequestType   `json:"type"`
	Status          RequestStatus `json:"status"`
	EmailSHA256     string        `json:"-"`
	DueAt           time.Time     `json:"dueAt"`
	Extended        bool          `json:"extended"`
	ExtensionReason string        `json:"extensionReason,omitempty"`
	CreatedAt       time.Time     `json:"createdAt"`
	CompletedAt     *time.Time    `json:"completedAt,omitempty"`
	Tasks           []Task        `json:"tasks"`
	Version         int64         `json:"-"`
}

// NewRequest mở yêu cầu với một task cho mỗi processor đã đăng ký. OPT_OUT không cần processor
// (consent được ghi ngay, service khác nghe ConsentChanged) nên hoàn tất luôn.
func NewRequest(id, subject string, t RequestType, emailSHA256 string, processors []string, now time.Time) (*Request, error) {
	if strings.TrimSpace(subject) == "" {
		return nil, apperr.Invalid("subject is required")
	}
	r := &Request{ID: id, SubjectID: subject, Type: t, Status: StatusInProgress, EmailSHA256: emailSHA256,
		DueAt: now.Add(ResponseWindow), CreatedAt: now}
	if t == RequestOptOut {
		r.Status = StatusCompleted
		r.CompletedAt = &now
		return r, nil
	}
	if len(processors) == 0 {
		return nil, apperr.Invalid("no data processors registered")
	}
	seen := map[string]bool{}
	for _, p := range processors {
		if p = strings.TrimSpace(p); p != "" && !seen[p] {
			seen[p] = true
			r.Tasks = append(r.Tasks, Task{Processor: p, Status: TaskPending})
		}
	}
	return r, nil
}

// Complete ghi kết quả của một processor. Trả về true nếu yêu cầu vừa hoàn tất.
// Idempotent: processor gửi lại kết quả không đổi gì.
func (r *Request) Complete(processor string, retained bool, retentionReason, objectKey string, now time.Time) (bool, error) {
	if !r.Status.Open() {
		return false, nil
	}
	idx := -1
	for i := range r.Tasks {
		if r.Tasks[i].Processor == processor {
			idx = i
			break
		}
	}
	if idx < 0 {
		return false, apperr.Invalid("processor %q is not part of request %s", processor, r.ID)
	}
	t := &r.Tasks[idx]
	if t.Status != TaskPending {
		return false, nil
	}
	if retained && strings.TrimSpace(retentionReason) == "" {
		return false, apperr.Invalid("retaining data needs a legal reason")
	}
	t.Status, t.RetentionReason, t.ObjectKey, t.CompletedAt = TaskCompleted, retentionReason, objectKey, &now
	if retained {
		t.Status = TaskRetained
	}
	for _, x := range r.Tasks {
		if x.Status == TaskPending {
			return false, nil
		}
	}
	r.Status = StatusCompleted
	for _, x := range r.Tasks {
		if x.Status == TaskRetained {
			r.Status = StatusPartiallyCompleted
		}
	}
	r.CompletedAt = &now
	return true, nil
}

// Extend gia hạn một lần thêm 45 ngày, bắt buộc có lý do (phải báo khách).
func (r *Request) Extend(reason string) error {
	if !r.Status.Open() {
		return apperr.Conflict("request %s is %s", r.ID, r.Status)
	}
	if r.Extended {
		return apperr.Conflict("request %s was already extended once", r.ID)
	}
	if strings.TrimSpace(reason) == "" {
		return apperr.Invalid("an extension needs a reason the customer will be told")
	}
	r.Extended, r.ExtensionReason, r.DueAt = true, reason, r.DueAt.Add(ResponseWindow)
	return nil
}

// Overdue: còn mở mà đã quá hạn chót.
func (r *Request) Overdue(now time.Time) bool { return r.Status.Open() && now.After(r.DueAt) }

// PendingProcessors liệt kê processor chưa trả lời.
func (r *Request) PendingProcessors() []string {
	var out []string
	for _, t := range r.Tasks {
		if t.Status == TaskPending {
			out = append(out, t.Processor)
		}
	}
	return out
}
