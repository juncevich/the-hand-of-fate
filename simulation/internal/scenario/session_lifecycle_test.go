package scenario

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/juncevich/fate/simulation/internal/client"
)

func newSessionServer(t *testing.T) *httptest.Server {
	t.Helper()
	var refreshCount atomic.Int32
	accounts := &fakeAccounts{}

	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if accounts.handle(w, r) {
			return
		}
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/login":
			var req client.LoginRequest
			_ = json.NewDecoder(r.Body).Decode(&req)
			if req.Password != "Lifecycle#99" {
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusUnauthorized)
				writeJSON(w, map[string]string{"error": "invalid credentials"})
				return
			}
			http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-login"})
			writeJSON(w, client.AuthResponse{AccessToken: "at-login", UserID: "u-1"})

		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/refresh":
			n := refreshCount.Add(1)
			if n > 1 {
				// second refresh (after logout) correctly rejected
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusUnauthorized)
				writeJSON(w, map[string]string{"error": "token revoked"})
				return
			}
			http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-refreshed"})
			writeJSON(w, client.AuthResponse{AccessToken: "at-refreshed"})

		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/telegram/link-token":
			writeJSON(w, client.LinkTokenResponse{
				Token:     "tok-12345678abc",
				ExpiresAt: time.Now().Add(5 * time.Minute),
			})

		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/logout":
			w.WriteHeader(http.StatusNoContent)

		default:
			t.Logf("unexpected request: %s %s", r.Method, r.URL.Path)
			w.WriteHeader(http.StatusNotFound)
		}
	}))
}

func TestSessionLifecycleScenario_HappyPath(t *testing.T) {
	srv := newSessionServer(t)
	defer srv.Close()

	if err := SessionLifecycleScenario(srv.URL, &fakeMailbox{token: "vt-1"}, testLogger(t)); err != nil {
		t.Errorf("SessionLifecycleScenario() error: %v", err)
	}
}

func TestSessionLifecycleScenario_RegisterError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusConflict)
		writeJSON(w, map[string]string{"error": "email already taken"})
	}))
	defer srv.Close()

	err := SessionLifecycleScenario(srv.URL, &fakeMailbox{token: "vt-1"}, testLogger(t))
	if err == nil {
		t.Fatal("expected error when Register fails")
	}
}

func TestSessionLifecycleScenario_LoginError(t *testing.T) {
	accounts := &fakeAccounts{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if accounts.handle(w, r) {
			return
		}
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/login":
			// always reject — even the correct password attempt fails
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusInternalServerError)
			writeJSON(w, map[string]string{"error": "auth service unavailable"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	err := SessionLifecycleScenario(srv.URL, &fakeMailbox{token: "vt-1"}, testLogger(t))
	if err == nil {
		t.Fatal("expected error when Login fails")
	}
}

func TestSessionLifecycleScenario_RefreshError(t *testing.T) {
	accounts := &fakeAccounts{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if accounts.handle(w, r) {
			return
		}
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/login":
			var req client.LoginRequest
			_ = json.NewDecoder(r.Body).Decode(&req)
			if req.Password != "Lifecycle#99" {
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusUnauthorized)
				writeJSON(w, map[string]string{"error": "invalid credentials"})
				return
			}
			http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-login"})
			writeJSON(w, client.AuthResponse{AccessToken: "at-login", UserID: "u-1"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/refresh":
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusUnauthorized)
			writeJSON(w, map[string]string{"error": "token expired"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	err := SessionLifecycleScenario(srv.URL, &fakeMailbox{token: "vt-1"}, testLogger(t))
	if err == nil {
		t.Fatal("expected error when Refresh fails")
	}
}

func TestSessionLifecycleScenario_GetLinkTokenError(t *testing.T) {
	accounts := &fakeAccounts{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if accounts.handle(w, r) {
			return
		}
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/login":
			var req client.LoginRequest
			_ = json.NewDecoder(r.Body).Decode(&req)
			if req.Password != "Lifecycle#99" {
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusUnauthorized)
				writeJSON(w, map[string]string{"error": "invalid credentials"})
				return
			}
			http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-login"})
			writeJSON(w, client.AuthResponse{AccessToken: "at-login", UserID: "u-1"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/refresh":
			http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-refreshed"})
			writeJSON(w, client.AuthResponse{AccessToken: "at-refreshed"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/telegram/link-token":
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusInternalServerError)
			writeJSON(w, map[string]string{"error": "internal error"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	err := SessionLifecycleScenario(srv.URL, &fakeMailbox{token: "vt-1"}, testLogger(t))
	if err == nil {
		t.Fatal("expected error when GetLinkToken fails")
	}
}

func TestSessionLifecycleScenario_LogoutFailsGracefully(t *testing.T) {
	accounts := &fakeAccounts{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if accounts.handle(w, r) {
			return
		}
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/login":
			var req client.LoginRequest
			_ = json.NewDecoder(r.Body).Decode(&req)
			if req.Password != "Lifecycle#99" {
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusUnauthorized)
				return
			}
			http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-login"})
			writeJSON(w, client.AuthResponse{AccessToken: "at-login", UserID: "u-1"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/refresh":
			http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-refreshed"})
			writeJSON(w, client.AuthResponse{AccessToken: "at-refreshed"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/telegram/link-token":
			writeJSON(w, client.LinkTokenResponse{
				Token:     "tok-12345678abc",
				ExpiresAt: time.Now().Add(5 * time.Minute),
			})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/auth/logout":
			// logout fails — scenario should still succeed (just logs a warning)
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	// Logout failure is non-fatal — scenario should complete without error.
	if err := SessionLifecycleScenario(srv.URL, &fakeMailbox{token: "vt-1"}, testLogger(t)); err != nil {
		t.Errorf("SessionLifecycleScenario() returned error on logout failure: %v", err)
	}
}

func TestSessionLifecycleScenario_FailsWhenLoginDoesNotWaitForVerification(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/v1/auth/register":
			w.WriteHeader(http.StatusAccepted)
			writeJSON(w, client.RegistrationResponse{Email: "x@example.com"})
		case "/api/v1/auth/login":
			writeJSON(w, client.AuthResponse{AccessToken: "at"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	err := SessionLifecycleScenario(srv.URL, &fakeMailbox{token: "vt-1"}, testLogger(t))
	if err == nil || !strings.Contains(err.Error(), "wait for email verification") {
		t.Fatalf("error = %v, want the verification check to fail", err)
	}
}
