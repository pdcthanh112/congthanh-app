package domain

import (
	"testing"
	"time"
)

var now = time.Date(2026, 11, 14, 12, 0, 0, 0, time.UTC)

func TestNewRequestCreatesOneTaskPerProcessorAndDueIn45Days(t *testing.T) {
	r, err := NewRequest("r1", "c-1", RequestDelete, "", []string{"customer-service", "order-service", "customer-service", " "}, now)
	if err != nil {
		t.Fatal(err)
	}
	if len(r.Tasks) != 2 {
		t.Fatalf("tasks = %d, want 2 (deduplicated)", len(r.Tasks))
	}
	if want := now.Add(45 * 24 * time.Hour); !r.DueAt.Equal(want) {
		t.Fatalf("due = %v, want %v", r.DueAt, want)
	}
}

func TestOptOutCompletesImmediately(t *testing.T) {
	r, err := NewRequest("r1", "c-1", RequestOptOut, "", nil, now)
	if err != nil || r.Status != StatusCompleted {
		t.Fatalf("status = %v err = %v", r.Status, err)
	}
}

func TestCompleteFinishesWhenAllProcessorsAnswerAndIsIdempotent(t *testing.T) {
	r, _ := NewRequest("r1", "c-1", RequestDelete, "", []string{"a", "b"}, now)
	done, err := r.Complete("a", false, "", "", now)
	if err != nil || done {
		t.Fatalf("after a: done=%v err=%v", done, err)
	}
	if done, _ := r.Complete("a", false, "", "", now); done {
		t.Fatal("repeat reply must not finish the request")
	}
	done, err = r.Complete("b", true, "tax records kept 7 years", "", now)
	if err != nil || !done {
		t.Fatalf("after b: done=%v err=%v", done, err)
	}
	if r.Status != StatusPartiallyCompleted {
		t.Fatalf("status = %v, want PARTIALLY_COMPLETED", r.Status)
	}
}

func TestRetentionNeedsReasonAndUnknownProcessorIsRejected(t *testing.T) {
	r, _ := NewRequest("r1", "c-1", RequestDelete, "", []string{"a"}, now)
	if _, err := r.Complete("a", true, "", "", now); err == nil {
		t.Fatal("expected error for retention without reason")
	}
	if _, err := r.Complete("zzz", false, "", "", now); err == nil {
		t.Fatal("expected error for unknown processor")
	}
}

func TestExtendOnlyOnceWithReason(t *testing.T) {
	r, _ := NewRequest("r1", "c-1", RequestAccess, "", []string{"a"}, now)
	if err := r.Extend(""); err == nil {
		t.Fatal("extension without reason must fail")
	}
	if err := r.Extend("large archive"); err != nil {
		t.Fatal(err)
	}
	if err := r.Extend("again"); err == nil {
		t.Fatal("second extension must fail")
	}
	if !r.Overdue(now.Add(91*24*time.Hour)) || r.Overdue(now.Add(89*24*time.Hour)) {
		t.Fatal("overdue should follow the extended due date")
	}
}

func TestDefaultsFollowOptOutAndOptInModels(t *testing.T) {
	got := EffectiveConsents("c-1", map[Purpose]Consent{PurposeSaleSharing: {Purpose: PurposeSaleSharing, Granted: false, Source: SourceGPC}})
	want := map[Purpose]bool{PurposeSaleSharing: false, PurposeTargetedAds: true, PurposeMarketingEmail: false, PurposeMarketingSMS: false, PurposeAnalytics: true}
	for _, c := range got {
		if c.Granted != want[c.Purpose] {
			t.Errorf("%s granted = %v, want %v", c.Purpose, c.Granted, want[c.Purpose])
		}
	}
	if _, err := ParsePurpose("selling"); err == nil {
		t.Fatal("unknown purpose must fail")
	}
}
