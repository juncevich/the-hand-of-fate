package scenario

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/juncevich/fate/simulation/internal/client"
	"github.com/juncevich/fate/simulation/internal/keycloak/keycloaktest"
)

func TestSignUp_CreatesTheAccountThroughKeycloakAndTheBackend(t *testing.T) {
	kc := keycloaktest.New(t)
	backend := fakeBackend(t, kc)

	account, err := SignUp(kc.URL, backend.URL, kc, testLogger(t))
	if err != nil {
		t.Fatalf("SignUp() error: %v", err)
	}
	if !strings.HasPrefix(account.Email, "sim_") || kc.EmailOf(account.Tokens.AccessToken) != account.Email {
		t.Fatalf("account = %+v", account)
	}
	if _, err := account.API.GetProfile(); err != nil {
		t.Fatalf("the client does not act as the new user: %v", err)
	}
}

func TestSignUp_ReportsAProfileOfSomeoneElse(t *testing.T) {
	kc := keycloaktest.New(t)
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, client.ProfileResponse{Email: "other@test.com"})
	}))
	t.Cleanup(backend.Close)

	if _, err := SignedUpClient(kc.URL, backend.URL, kc, testLogger(t)); err == nil || !strings.Contains(err.Error(), "profile email") {
		t.Fatalf("err = %v, want a profile mismatch", err)
	}
}

func TestSignUp_ReportsAFailedRegistration(t *testing.T) {
	kc := keycloaktest.New(t)
	backend := fakeBackend(t, kc)

	// No mailbox link: the verification email never arrives.
	if _, err := SignUp(kc.URL, backend.URL, noMail{}, testLogger(t)); err == nil || !strings.Contains(err.Error(), "sign up") {
		t.Fatalf("err = %v, want a sign-up error", err)
	}
}

func TestSessionLifecycleScenario_HappyPath(t *testing.T) {
	kc := keycloaktest.New(t)
	backend := fakeBackend(t, kc)

	if err := SessionLifecycleScenario(kc.URL, backend.URL, kc, testLogger(t)); err != nil {
		t.Fatalf("SessionLifecycleScenario() error: %v", err)
	}
}

func TestSessionLifecycleScenario_FailsWhenLogoutAllKeepsSessions(t *testing.T) {
	kc := keycloaktest.New(t)
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		email := kc.EmailOf(strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer "))
		switch r.URL.Path {
		case "/api/v1/users/me":
			writeJSON(w, client.ProfileResponse{Email: email})
		case "/api/v1/telegram/link-token":
			writeJSON(w, client.LinkTokenResponse{Token: "link-token-123"})
		case "/api/v1/auth/logout-all":
			w.WriteHeader(http.StatusNoContent) // but Keycloak's sessions stay
		}
	}))
	t.Cleanup(backend.Close)

	err := SessionLifecycleScenario(kc.URL, backend.URL, kc, testLogger(t))
	if err == nil || !strings.Contains(err.Error(), "after logout-all") {
		t.Fatalf("err = %v, want the second session to survive logout-all", err)
	}
}

type noMail struct{}

func (noMail) Link(string, string) (string, error) { return "", errors.New("no email") }
