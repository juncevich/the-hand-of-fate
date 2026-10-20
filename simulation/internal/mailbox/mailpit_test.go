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

func TestLinkPicksTheNewestMatchingLinkAndUnescapesIt(t *testing.T) {
	srv := fakeMailpit(t, map[string]string{
		"invite": `<a href="http://app/votes/1">Vote</a>`,
		"verify": `<a href="http://app/auth/realms/fate/login-actions/action-token?key=new&amp;client_id=fate-spa">Confirm</a>`,
		"old":    `<a href="http://app/auth/realms/fate/login-actions/action-token?key=stale">Confirm</a>`,
	}, []string{"invite", "verify", "old"})

	link, err := NewMailpit(srv.URL).Link("ann@example.com", "/auth/realms/fate/login-actions/action-token")
	want := "http://app/auth/realms/fate/login-actions/action-token?key=new&client_id=fate-spa"
	if err != nil || link != want {
		t.Fatalf("Link() = %q, %v; want %q", link, err, want)
	}
}

func TestLinkTimesOutWithoutAMatchingEmail(t *testing.T) {
	srv := fakeMailpit(t, map[string]string{"other": `<a href="http://app/votes/1">Vote</a>`}, []string{"other"})
	m := NewMailpit(srv.URL)
	m.Timeout, m.Poll = 50*time.Millisecond, 10*time.Millisecond

	if _, err := m.Link("ann@example.com", "/auth/"); err == nil {
		t.Fatal("expected a timeout error")
	}
}

func TestLinkReportsMailpitErrors(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	if _, err := NewMailpit(srv.URL).Link("ann@example.com", "/auth/"); err == nil {
		t.Fatal("expected an error")
	}
}
