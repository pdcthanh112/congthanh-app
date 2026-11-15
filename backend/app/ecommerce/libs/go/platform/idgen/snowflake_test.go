package idgen

import "testing"

func TestValidAndArg(t *testing.T) {
	if !Valid("613220074087484628") || Valid("") || Valid("0198c5a0-1234-7abc-8def-000000000001") || Valid("12345678901234567890") {
		t.Fatal("Valid")
	}
	if Arg("abc") != "-1" || Arg("42") != "42" {
		t.Fatal("Arg")
	}
}

func TestNodeFromEnv(t *testing.T) {
	t.Setenv("NODE_ID", "7")
	if n, err := NodeFromEnv(); err != nil || n != 7 {
		t.Fatalf("got %d %v", n, err)
	}
	t.Setenv("NODE_ID", "1024")
	if _, err := NodeFromEnv(); err == nil {
		t.Fatal("expected error")
	}
	t.Setenv("NODE_ID", "")
	t.Setenv("HOSTNAME", "pod-a")
	a, _ := NodeFromEnv()
	b, _ := NodeFromEnv()
	if a != b || a < 0 || a > 1023 {
		t.Fatalf("hash node %d %d", a, b)
	}
}

func TestIDsIncrease(t *testing.T) {
	g, _ := New(1)
	ids := IDs{g}
	a, b := ids.NewID(), ids.NewID()
	if !Valid(a) || a >= b && len(a) == len(b) {
		t.Fatalf("%s %s", a, b)
	}
}
