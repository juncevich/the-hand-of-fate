package mailbox

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func fakeMailpit(t *testing.T, messages map[string]string, order []string) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.URL.Path == "/api/v1/search":
			if q := r.URL.Query().Get("query"); q != `to:"ann@example.com"` {
				t.Errorf("query = %q", q)
			}
			list := make([]map[string]string, 0, len(order))
			for _, id := range order {
				list = append(list, map[string]string{"ID": id})
			}
			_ = json.NewEncoder(w).Encode(map[string]any{"messages": list})
		case strings.HasPrefix(r.URL.Path, "/api/v1/message/"):
			_ = json.NewEncoder(w).Encode(map[string]string{"HTML": messages[strings.TrimPrefix(r.URL.Path, "/api/v1/message/")]})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	t.Cleanup(srv.Close)
	return srv
}

func TestLinkTokenPicksTheNewestMatchingLinkAndDecodesIt(t *testing.T) {
	srv := fakeMailpit(t, map[string]string{
		"new-reset": `<a href="http://app/reset-password?token=reset1">x</a>`,
		"verify":    `<a href="http://app/verify-email?token=a%2Bb">Confirm</a>`,
		"old":       `<a href="http://app/verify-email?token=stale">Confirm</a>`,
	}, []string{"new-reset", "verify", "old"})

	token, err := NewMailpit(srv.URL).LinkToken("ann@example.com", "/verify-email")
	if err != nil || token != "a+b" {
		t.Fatalf("LinkToken() = %q, %v; want a+b", token, err)
	}
}

func TestLinkTokenTimesOutWithoutAMatchingEmail(t *testing.T) {
	srv := fakeMailpit(t, map[string]string{"other": `<a href="http://app/votes/1">Vote</a>`}, []string{"other"})
	m := NewMailpit(srv.URL)
	m.Timeout, m.Poll = 50*time.Millisecond, 10*time.Millisecond

	if _, err := m.LinkToken("ann@example.com", "/verify-email"); err == nil {
		t.Fatal("expected a timeout error")
	}
}

func TestLinkTokenReportsMailpitErrors(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	if _, err := NewMailpit(srv.URL).LinkToken("ann@example.com", "/verify-email"); err == nil {
		t.Fatal("expected an error")
	}
}
