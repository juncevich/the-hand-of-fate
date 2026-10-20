package scenario

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/juncevich/fate/simulation/internal/client"
	"github.com/juncevich/fate/simulation/internal/keycloak/keycloaktest"
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

// fakeBackend accepts only access tokens the fake Keycloak issued; logout-all ends the user's
// Keycloak sessions, as the backend does through the Admin API.
func fakeBackend(t *testing.T, kc *keycloaktest.Server) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		email := kc.EmailOf(strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer "))
		if email == "" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		switch r.URL.Path {
		case "/api/v1/users/me":
			writeJSON(w, client.ProfileResponse{UserID: "u-" + email, Email: email, DisplayName: "Sim"})
		case "/api/v1/telegram/link-token":
			writeJSON(w, client.LinkTokenResponse{Token: "link-token-123"})
		case "/api/v1/auth/logout-all":
			kc.EndSessions(email)
			w.WriteHeader(http.StatusNoContent)
		default:
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	t.Cleanup(srv.Close)
	return srv
}
