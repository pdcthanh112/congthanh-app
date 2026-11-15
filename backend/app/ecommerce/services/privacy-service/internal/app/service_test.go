package app

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/domain"
)

// memStore là Store giả trong bộ nhớ; InTx áp thay đổi ngay (đủ cho test use case).
type memStore struct {
	consents []domain.Consent
	requests map[string]*domain.Request
	events   []domain.Event
}

func newMem() *memStore { return &memStore{requests: map[string]*domain.Request{}} }

func (m *memStore) InTx(_ context.Context, fn func(Tx) error) error { return fn(m) }

func (m *memStore) LatestConsents(_ context.Context, s string) (map[domain.Purpose]domain.Consent, error) {
	out := map[domain.Purpose]domain.Consent{}
	for _, c := range m.consents {
		if c.SubjectID == s {
			out[c.Purpose] = c
		}
	}
	return out, nil
}
func (m *memStore) GetRequest(_ context.Context, id string) (*domain.Request, error) {
	if r, ok := m.requests[id]; ok {
		cp := *r
		cp.Tasks = append([]domain.Task(nil), r.Tasks...)
		return &cp, nil
	}
	return nil, apperr.NotFound("request %s not found", id)
}
func (m *memStore) RequestsBySubject(context.Context, string, int) ([]domain.Request, error) {
	return nil, nil
}
func (m *memStore) RequestsByStatus(context.Context, domain.RequestStatus, int) ([]domain.Request, error) {
	return nil, nil
}
func (m *memStore) StaleOpenRequests(_ context.Context, _ time.Time, _ int) ([]domain.Request, error) {
	var out []domain.Request
	for _, r := range m.requests {
		if r.Status.Open() {
			out = append(out, *r)
		}
	}
	return out, nil
}
func (m *memStore) AppendConsent(_ context.Context, c domain.Consent) error {
	m.consents = append(m.consents, c)
	return nil
}
func (m *memStore) OpenRequestOfType(_ context.Context, s string, t domain.RequestType) (bool, error) {
	for _, r := range m.requests {
		if r.SubjectID == s && r.Type == t && r.Status.Open() {
			return true, nil
		}
	}
	return false, nil
}
func (m *memStore) InsertRequest(_ context.Context, r *domain.Request) error {
	cp := *r
	m.requests[r.ID] = &cp
	return nil
}
func (m *memStore) LockRequest(ctx context.Context, id string) (*domain.Request, error) {
	return m.GetRequest(ctx, id)
}
func (m *memStore) UpdateRequest(_ context.Context, r *domain.Request) error {
	cp := *r
	m.requests[r.ID] = &cp
	return nil
}
func (m *memStore) MarkReminded(context.Context, string, time.Time) error { return nil }
func (m *memStore) Emit(_ context.Context, e ...domain.Event) error {
	m.events = append(m.events, e...)
	return nil
}

type seqIDs struct{ n int }

func (s *seqIDs) New() string { s.n++; return fmt.Sprintf("%d", 700000000000000000+s.n) }

func eventNames(es []domain.Event) []string {
	var out []string
	for _, e := range es {
		out = append(out, fmt.Sprintf("%T", e))
	}
	return out
}

func TestDeleteRequestFansOutAndCompletesWhenAllProcessorsReply(t *testing.T) {
	ctx := context.Background()
	st := newMem()
	svc := NewService(st, &seqIDs{}, []string{"customer-service", "order-service"}, "v1")

	r, err := svc.OpenRequest(ctx, "c-1", "delete", " Jane@Example.com ")
	if err != nil {
		t.Fatal(err)
	}
	if r.EmailSHA256 != HashEmail("jane@example.com") || r.EmailSHA256 == "" {
		t.Fatal("email must be normalised and hashed")
	}
	if got := eventNames(st.events); fmt.Sprint(got) != "[domain.RequestOpened domain.ErasureRequested]" {
		t.Fatalf("events = %v", got)
	}
	if _, err := svc.OpenRequest(ctx, "c-1", "DELETE", ""); apperr.KindOf(err) != apperr.KindConflict {
		t.Fatalf("second open delete request: err = %v, want conflict", err)
	}

	st.events = nil
	_ = svc.ProcessorReply(ctx, r.ID, "customer-service", false, "", "")
	_ = svc.ProcessorReply(ctx, r.ID, "customer-service", false, "", "") // giao lại
	_ = svc.ProcessorReply(ctx, r.ID, "unknown-service", false, "", "")  // bị bỏ qua, không làm kẹt consumer
	if len(st.events) != 0 {
		t.Fatalf("request must stay open until every processor replies, events = %v", eventNames(st.events))
	}
	_ = svc.ProcessorReply(ctx, r.ID, "order-service", true, "tax records", "")
	got, _ := st.GetRequest(ctx, r.ID)
	if got.Status != domain.StatusPartiallyCompleted {
		t.Fatalf("status = %v", got.Status)
	}
	if fmt.Sprint(eventNames(st.events)) != "[domain.RequestCompleted]" {
		t.Fatalf("events = %v", eventNames(st.events))
	}
}

func TestOptOutRecordsConsentAndGPCIsHonoured(t *testing.T) {
	ctx := context.Background()
	st := newMem()
	svc := NewService(st, &seqIDs{}, []string{"a"}, "v1")

	cs, err := svc.Consents(ctx, "c-2", true)
	if err != nil {
		t.Fatal(err)
	}
	for _, c := range cs {
		if (c.Purpose == domain.PurposeSaleSharing || c.Purpose == domain.PurposeTargetedAds) && (c.Granted || c.Source != domain.SourceGPC) {
			t.Fatalf("GPC must opt out of %s, got %+v", c.Purpose, c)
		}
	}
	n := len(st.consents)
	if _, err := svc.Consents(ctx, "c-2", true); err != nil || len(st.consents) != n {
		t.Fatal("repeated GPC signal must not write new consent rows")
	}

	r, err := svc.OpenRequest(ctx, "c-3", "OPT_OUT", "")
	if err != nil || r.Status != domain.StatusCompleted {
		t.Fatalf("opt-out: %v %v", r, err)
	}
	latest, _ := st.LatestConsents(ctx, "c-3")
	if latest[domain.PurposeSaleSharing].Granted {
		t.Fatal("opt-out request must record SALE_SHARING = false")
	}
}

func TestCustomerCannotSeeSomeoneElsesRequest(t *testing.T) {
	ctx := context.Background()
	st := newMem()
	svc := NewService(st, &seqIDs{}, []string{"a"}, "v1")
	r, _ := svc.OpenRequest(ctx, "c-1", "ACCESS", "")
	if _, err := svc.GetRequest(ctx, r.ID, "c-2", false); apperr.KindOf(err) != apperr.KindNotFound {
		t.Fatalf("err = %v, want not found", err)
	}
	if _, err := svc.GetRequest(ctx, r.ID, "staff", true); err != nil {
		t.Fatal(err)
	}
}

func TestSweepRepublishesPendingRequests(t *testing.T) {
	ctx := context.Background()
	st := newMem()
	svc := NewService(st, &seqIDs{}, []string{"a"}, "v1")
	_, _ = svc.OpenRequest(ctx, "c-1", "DELETE", "")
	svc.now = func() time.Time { return time.Now().Add(50 * 24 * time.Hour) }
	st.events = nil
	reminded, overdue, err := svc.Sweep(ctx)
	if err != nil || reminded != 1 || overdue != 1 {
		t.Fatalf("reminded=%d overdue=%d err=%v", reminded, overdue, err)
	}
	if fmt.Sprint(eventNames(st.events)) != "[domain.ErasureRequested]" {
		t.Fatalf("events = %v", eventNames(st.events))
	}
}

type fakeIdentity struct{ deleted []string }

func (f *fakeIdentity) DeleteUser(_ context.Context, id string) error {
	f.deleted = append(f.deleted, id)
	return nil
}

func (m *memStore) requestsByStatus(st domain.RequestStatus) []domain.Request {
	var out []domain.Request
	for _, r := range m.requests {
		if r.Status == st {
			out = append(out, *r)
		}
	}
	return out
}

func TestKeycloakAccountIsDeletedLastAndClosesTheRequest(t *testing.T) {
	ctx := context.Background()
	st := &statusStore{memStore: newMem()}
	id := &fakeIdentity{}
	svc := NewService(st, &seqIDs{}, []string{"customer-service", IdentityProcessor}, "v1").WithIdentity(id)
	r, _ := svc.OpenRequest(ctx, "c-1", "DELETE", "")

	if n, _ := svc.CloseIdentity(ctx); n != 0 || len(id.deleted) != 0 {
		t.Fatal("account must not be deleted while other services still hold data")
	}
	_ = svc.ProcessorReply(ctx, r.ID, "customer-service", false, "", "")
	if n, err := svc.CloseIdentity(ctx); err != nil || n != 1 || id.deleted[0] != "c-1" {
		t.Fatalf("n=%d err=%v deleted=%v", n, err, id.deleted)
	}
	got, _ := st.GetRequest(ctx, r.ID)
	if got.Status != domain.StatusCompleted {
		t.Fatalf("status = %v", got.Status)
	}
}

type statusStore struct{ *memStore }

func (s *statusStore) RequestsByStatus(_ context.Context, st domain.RequestStatus, _ int) ([]domain.Request, error) {
	return s.requestsByStatus(st), nil
}

func (s *statusStore) InTx(ctx context.Context, fn func(Tx) error) error { return fn(s.memStore) }
