package scenario

import (
	"regexp"
	"strings"
	"testing"
)

var emailPattern = regexp.MustCompile(`^sim_[0-9a-f]{8}@example\.com$`)

func TestRandomEmail_Format(t *testing.T) {
	for i := 0; i < 30; i++ {
		email := randomEmail()
		if !emailPattern.MatchString(email) {
			t.Errorf("randomEmail() = %q, does not match sim_<8hex>@example.com", email)
		}
	}
}

func TestRandomEmail_Unique(t *testing.T) {
	seen := make(map[string]bool, 100)
	for i := 0; i < 100; i++ {
		email := randomEmail()
		if seen[email] {
			t.Errorf("randomEmail() produced duplicate: %q", email)
		}
		seen[email] = true
	}
}

func TestRandomName_TwoWords(t *testing.T) {
	for i := 0; i < 30; i++ {
		name := randomName()
		parts := strings.Fields(name)
		if len(parts) != 2 {
			t.Errorf("randomName() = %q, want exactly two words", name)
		}
	}
}

func TestRandomName_KnownNames(t *testing.T) {
	for i := 0; i < 50; i++ {
		name := randomName()
		parts := strings.Fields(name)
		first, last := parts[0], parts[1]
		if !contains(firstNames, first) {
			t.Errorf("first name %q not in pool", first)
		}
		if !contains(lastNames, last) {
			t.Errorf("last name %q not in pool", last)
		}
	}
}

func TestRandomVoteTitle_NotEmpty(t *testing.T) {
	for i := 0; i < 30; i++ {
		title := randomVoteTitle()
		if title == "" {
			t.Error("randomVoteTitle() returned empty string")
		}
	}
}

func TestRandomOptions_ExactCount(t *testing.T) {
	for n := 1; n <= 8; n++ {
		opts := randomOptions(n)
		if len(opts) != n {
			t.Errorf("randomOptions(%d) returned %d items", n, len(opts))
		}
	}
}

func TestRandomOptions_AllUnique(t *testing.T) {
	opts := randomOptions(8)
	seen := make(map[string]bool)
	for _, o := range opts {
		if seen[o] {
			t.Errorf("randomOptions(8) contains duplicate: %q", o)
		}
		seen[o] = true
	}
}

func TestRandomOptions_ClampsToPollSize(t *testing.T) {
	opts := randomOptions(100)
	if len(opts) > 8 {
		t.Errorf("randomOptions(100) returned %d items, expected ≤ 8", len(opts))
	}
}

func TestRandomOptions_Zero(t *testing.T) {
	opts := randomOptions(0)
	if len(opts) != 0 {
		t.Errorf("randomOptions(0) = %v, want empty", opts)
	}
}

func TestPtr_Int(t *testing.T) {
	p := ptr(42)
	if p == nil {
		t.Fatal("ptr(42) returned nil")
	}
	if *p != 42 {
		t.Errorf("*ptr(42) = %d", *p)
	}
}

func TestPtr_String(t *testing.T) {
	s := "hello"
	p := ptr(s)
	if p == nil {
		t.Fatal("ptr(string) returned nil")
	}
	if *p != s {
		t.Errorf("*ptr(%q) = %q", s, *p)
	}
}

func TestPtr_Bool(t *testing.T) {
	p := ptr(true)
	if p == nil || !*p {
		t.Errorf("ptr(true) = %v", p)
	}
}

func contains(slice []string, s string) bool {
	for _, v := range slice {
		if v == s {
			return true
		}
	}
	return false
}
