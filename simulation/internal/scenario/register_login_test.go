package scenario

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/juncevich/fate/simulation/internal/client"
)

func newAuthServer(t *testing.T, registerStatus, refreshStatus int) (*httptest.Server, *client.RegisterRequest) {
	t.Helper()
	var registered client.RegisterRequest
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/v1/auth/register":
			_ = json.NewDecoder(r.Body).Decode(&registered)
			http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-1", Path: "/"})
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(registerStatus)
			writeJSON(w, client.AuthResponse{AccessToken: "at-1", UserID: "u1", Email: registered.Email})
		case "/api/v1/auth/refresh":
			var body client.RefreshRequest
			_ = json.NewDecoder(r.Body).Decode(&body)
			if body.RefreshToken != "rt-1" {
				t.Errorf("refresh token = %q, want rt-1", body.RefreshToken)
			}
			if got := r.Header.Get("Authorization"); got != "Bearer at-1" {
				t.Errorf("Authorization = %q, want Bearer at-1", got)
			}
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(refreshStatus)
			writeJSON(w, client.AuthResponse{AccessToken: "at-2", UserID: "u1"})
		default:
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	t.Cleanup(srv.Close)
	return srv, &registered
}

func TestRegisterAndLogin_HappyPath(t *testing.T) {
	srv, registered := newAuthServer(t, http.StatusOK, http.StatusOK)

	c, auth, err := RegisterAndLogin(srv.URL, testLogger(t))
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
}

func TestRegisterAndLogin_RegisterError(t *testing.T) {
	srv, _ := newAuthServer(t, http.StatusConflict, http.StatusOK)

	_, _, err := RegisterAndLogin(srv.URL, testLogger(t))
	if err == nil || !strings.Contains(err.Error(), "register") {
		t.Fatalf("error = %v, want register error", err)
	}
}

func TestRegisterAndLogin_RefreshError(t *testing.T) {
	srv, _ := newAuthServer(t, http.StatusOK, http.StatusUnauthorized)

	_, _, err := RegisterAndLogin(srv.URL, testLogger(t))
	if err == nil || !strings.Contains(err.Error(), "refresh") {
		t.Fatalf("error = %v, want refresh error", err)
	}
}
