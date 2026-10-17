package scenario

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	"github.com/juncevich/fate/simulation/internal/client"
	"go.uber.org/zap"
)

func testLogger(t *testing.T) *zap.Logger {
	t.Helper()
	log, _ := zap.NewDevelopment()
	t.Cleanup(func() { _ = log.Sync() })
	return log
}

func testClient(t *testing.T, srv *httptest.Server) *client.Client {
	t.Helper()
	c, err := client.New(srv.URL, testLogger(t))
	if err != nil {
		t.Fatalf("client.New: %v", err)
	}
	c.SetAccessToken("test-token")
	return c
}

func writeJSON(w http.ResponseWriter, v any) {
	b, _ := json.Marshal(v)
	w.Header().Set("Content-Type", "application/json")
	_, _ = w.Write(b)
}

// fakeMailbox hands out a fixed verification token, as if read from the mailed link.
type fakeMailbox struct {
	token string
	err   error
	calls []string
}

func (m *fakeMailbox) LinkToken(to, path string) (string, error) {
	m.calls = append(m.calls, to+" "+path)
	return m.token, m.err
}

// fakeAccounts emulates registration with email verification: login answers 403 until
// verify-email has been called with token "vt-1". It handles only those three endpoints.
type fakeAccounts struct {
	verified atomic.Bool
	email    string
}

func (a *fakeAccounts) handle(w http.ResponseWriter, r *http.Request) bool {
	switch {
	case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/register":
		var req client.RegisterRequest
		_ = json.NewDecoder(r.Body).Decode(&req)
		a.email = req.Email
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusAccepted)
		writeJSON(w, client.RegistrationResponse{Email: req.Email})
	case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/verify-email":
		var req client.VerifyEmailRequest
		_ = json.NewDecoder(r.Body).Decode(&req)
		if req.Token != "vt-1" {
			w.WriteHeader(http.StatusBadRequest)
			return true
		}
		a.verified.Store(true)
		http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-reg", Path: "/"})
		writeJSON(w, client.AuthResponse{AccessToken: "at-reg", UserID: "u-1", Email: a.email})
	case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/login" && !a.verified.Load():
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusForbidden)
		writeJSON(w, map[string]string{"code": "EMAIL_NOT_VERIFIED"})
	default:
		return false
	}
	return true
}
