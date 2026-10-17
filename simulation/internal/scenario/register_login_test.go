package scenario

import (
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/juncevich/fate/simulation/internal/client"
)

func newAuthServer(t *testing.T, registerStatus, refreshStatus int) (*httptest.Server, *client.RegisterRequest) {
	t.Helper()
	var registered client.RegisterRequest
	accounts := &fakeAccounts{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/v1/auth/register":
			_ = json.NewDecoder(r.Body).Decode(&registered)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(registerStatus)
			writeJSON(w, client.RegistrationResponse{Email: registered.Email})
		case "/api/v1/auth/refresh":
			var body client.RefreshRequest
			_ = json.NewDecoder(r.Body).Decode(&body)
			if body.RefreshToken == nil || *body.RefreshToken != "rt-reg" {
				t.Errorf("refresh token = %v, want rt-reg", body.RefreshToken)
			}
			if got := r.Header.Get("Authorization"); got != "Bearer at-reg" {
				t.Errorf("Authorization = %q, want Bearer at-reg", got)
			}
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(refreshStatus)
			writeJSON(w, client.AuthResponse{AccessToken: "at-2", UserID: "u1"})
		default:
			if !accounts.handle(w, r) {
				t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
				w.WriteHeader(http.StatusNotFound)
			}
		}
	}))
	t.Cleanup(srv.Close)
	return srv, &registered
}

func TestRegisterAndLogin_HappyPath(t *testing.T) {
	srv, registered := newAuthServer(t, http.StatusAccepted, http.StatusOK)
	mail := &fakeMailbox{token: "vt-1"}

	c, auth, err := RegisterAndLogin(srv.URL, mail, testLogger(t))
	if err != nil {
		t.Fatalf("RegisterAndLogin() error: %v", err)
	}
	if c == nil {
		t.Fatal("client is nil")
	}
	if auth.AccessToken != "at-2" {
		t.Errorf("access token = %q, want refreshed at-2", auth.AccessToken)
	}
	if !strings.HasPrefix(registered.Email, "sim_") || !strings.HasSuffix(registered.Email, "@example.com") {
		t.Errorf("registered email = %q, want a generated address", registered.Email)
	}
	if registered.Password == "" || registered.DisplayName == "" {
		t.Errorf("register request missing fields: %+v", *registered)
	}
	if len(mail.calls) != 1 || mail.calls[0] != registered.Email+" /verify-email" {
		t.Errorf("mailbox calls = %v, want the verification link for the new address", mail.calls)
	}
}

func TestRegisterAndLogin_RegisterError(t *testing.T) {
	srv, _ := newAuthServer(t, http.StatusConflict, http.StatusOK)

	_, _, err := RegisterAndLogin(srv.URL, &fakeMailbox{token: "vt-1"}, testLogger(t))
	if err == nil || !strings.Contains(err.Error(), "register") {
		t.Fatalf("error = %v, want register error", err)
	}
}

func TestRegisterAndLogin_MissingVerificationEmail(t *testing.T) {
	srv, _ := newAuthServer(t, http.StatusAccepted, http.StatusOK)

	_, _, err := RegisterAndLogin(srv.URL, &fakeMailbox{err: errors.New("timeout")}, testLogger(t))
	if err == nil || !strings.Contains(err.Error(), "verification email") {
		t.Fatalf("error = %v, want verification email error", err)
	}
}

func TestRegisterAndLogin_InvalidVerificationToken(t *testing.T) {
	srv, _ := newAuthServer(t, http.StatusAccepted, http.StatusOK)

	_, _, err := RegisterAndLogin(srv.URL, &fakeMailbox{token: "wrong"}, testLogger(t))
	if err == nil || !strings.Contains(err.Error(), "verify email") {
		t.Fatalf("error = %v, want verify email error", err)
	}
}

func TestRegisterAndLogin_RefreshError(t *testing.T) {
	srv, _ := newAuthServer(t, http.StatusAccepted, http.StatusUnauthorized)

	_, _, err := RegisterAndLogin(srv.URL, &fakeMailbox{token: "vt-1"}, testLogger(t))
	if err == nil || !strings.Contains(err.Error(), "refresh") {
		t.Fatalf("error = %v, want refresh error", err)
	}
}
