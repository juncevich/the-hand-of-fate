package keycloak_test

import (
	"errors"
	"strings"
	"testing"

	"github.com/juncevich/fate/simulation/internal/keycloak"
	"github.com/juncevich/fate/simulation/internal/keycloak/keycloaktest"
)

func newBrowser(t *testing.T, kc *keycloaktest.Server) *keycloak.Browser {
	t.Helper()
	b, err := keycloak.New(kc.URL + "/")
	if err != nil {
		t.Fatalf("New: %v", err)
	}
	return b
}

func TestRegisterSetsThePasswordAfterTheMailedLinkAndSignsIn(t *testing.T) {
	kc := keycloaktest.New(t)
	tokens, err := newBrowser(t, kc).Register("ann@test.com", "Ann", "Lee", "Secret#1", kc)
	if err != nil {
		t.Fatalf("Register: %v", err)
	}
	if kc.EmailOf(tokens.AccessToken) != "ann@test.com" || tokens.IDToken == "" {
		t.Fatalf("tokens = %+v", tokens)
	}
	// The password set after verification works in another browser.
	if _, err := newBrowser(t, kc).SignIn("ann@test.com", "Secret#1"); err != nil {
		t.Fatalf("SignIn with the new password: %v", err)
	}
}

func TestRegisterReportsARejectedForm(t *testing.T) {
	kc := keycloaktest.New(t)
	kc.AddUser("ann@test.com", "x")

	_, err := newBrowser(t, kc).Register("ann@test.com", "Ann", "Lee", "Secret#1", kc)
	if err == nil || !strings.Contains(err.Error(), "Email already exists") {
		t.Fatalf("err = %v, want the form's error", err)
	}
}

func TestSignInReportsWrongCredentials(t *testing.T) {
	kc := keycloaktest.New(t)
	kc.AddUser("ann@test.com", "right")

	_, err := newBrowser(t, kc).SignIn("ann@test.com", "wrong")
	if err == nil || !strings.Contains(err.Error(), "Invalid username or password") {
		t.Fatalf("err = %v, want the login form's error", err)
	}
}

func TestRenewUsesTheSessionUntilItEnds(t *testing.T) {
	kc := keycloaktest.New(t)
	kc.AddUser("ann@test.com", "pw")
	b := newBrowser(t, kc)
	if _, err := b.Renew(); !errors.Is(err, keycloak.ErrSignedOut) {
		t.Fatalf("Renew before signing in: err = %v, want ErrSignedOut", err)
	}
	first, err := b.SignIn("ann@test.com", "pw")
	if err != nil {
		t.Fatalf("SignIn: %v", err)
	}

	renewed, err := b.Renew()
	if err != nil || renewed.AccessToken == first.AccessToken || kc.EmailOf(renewed.AccessToken) != "ann@test.com" {
		t.Fatalf("Renew = %+v, %v; want a new token for ann", renewed, err)
	}

	if err := b.SignOut(first.IDToken); err != nil {
		t.Fatalf("SignOut: %v", err)
	}
	if _, err := b.Renew(); !errors.Is(err, keycloak.ErrSignedOut) {
		t.Fatalf("Renew after signing out: err = %v, want ErrSignedOut", err)
	}
}

func TestSignOutNeedsToComeBackToTheApp(t *testing.T) {
	kc := keycloaktest.New(t)
	b, err := keycloak.New(kc.URL + "/elsewhere")
	if err != nil {
		t.Fatal(err)
	}
	if err := b.SignOut("idt"); err == nil {
		t.Fatal("expected an error: the logout did not return to /elsewhere/login")
	}
}
