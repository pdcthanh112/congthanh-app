package domain

// Event là sự kiện miền; adapter đổi sang protobuf ecommerce.privacy.v1 và ghi outbox trong cùng transaction.
type Event interface{ subject() string }

type ConsentChanged struct{ Consent Consent }
type RequestOpened struct{ Request Request }
type ErasureRequested struct{ Request Request }
type AccessExportRequested struct{ Request Request }
type RequestCompleted struct{ Request Request }

func (e ConsentChanged) subject() string        { return e.Consent.SubjectID }
func (e RequestOpened) subject() string         { return e.Request.SubjectID }
func (e ErasureRequested) subject() string      { return e.Request.SubjectID }
func (e AccessExportRequested) subject() string { return e.Request.SubjectID }
func (e RequestCompleted) subject() string      { return e.Request.SubjectID }

// SubjectOf trả về khoá Kafka (subject id) của event.
func SubjectOf(e Event) string { return e.subject() }
