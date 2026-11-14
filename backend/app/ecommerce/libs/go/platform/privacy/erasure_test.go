package privacy

import "testing"

func TestErasedIDIsStableAndNotTheSubject(t *testing.T) {
	a, b := ErasedID("c-1"), ErasedID("c-1")
	if a != b || a == "c-1" || len(a) != len("erased-")+16 {
		t.Fatalf("ErasedID = %q / %q", a, b)
	}
	if ErasedID("c-2") == a {
		t.Fatal("different subjects must map to different ids")
	}
}
