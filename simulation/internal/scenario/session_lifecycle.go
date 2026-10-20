package scenario

import (
	"errors"
	"fmt"

	"github.com/juncevich/fate/simulation/internal/client"
	"github.com/juncevich/fate/simulation/internal/keycloak"
	"go.uber.org/zap"
)

// SessionLifecycleScenario walks through signing in with Keycloak against the backend:
//  1. Sign up (Keycloak's form, email confirmed from the mailbox, password set afterwards)
//  2. A wrong password is rejected on the login form
//  3. Sign in in a second browser; the backend accepts its token
//  4. Renew silently with the session cookie (prompt=none), as the SPA's iframe does
//  5. Get a Telegram link token
//  6. Sign out in the first browser: its silent renewal fails
//  7. Log out everywhere through the backend: the second browser's renewal fails too
func SessionLifecycleScenario(app, base string, mail keycloak.Mailbox, log *zap.Logger) error {
	log.Info("=== SessionLifecycleScenario start ===")

	// 1. Sign up
	account, err := SignUp(app, base, mail, log)
	if err != nil {
		return err
	}

	// 2. Wrong password
	other, err := keycloak.New(app)
	if err != nil {
		return err
	}
	if _, err = other.SignIn(account.Email, "wrong-password"); err == nil {
		return fmt.Errorf("expected the wrong password to be rejected")
	}
	log.Info("wrong password correctly rejected", zap.Error(err))

	// 3. Second browser
	tokens, err := other.SignIn(account.Email, password)
	if err != nil {
		return fmt.Errorf("sign in: %w", err)
	}
	api, err := client.New(base, log)
	if err != nil {
		return err
	}
	api.SetAccessToken(tokens.AccessToken)
	if _, err = api.GetProfile(); err != nil {
		return fmt.Errorf("profile with the second session: %w", err)
	}
	log.Info("signed in in a second browser")

	// 4. Silent renewal
	renewed, err := other.Renew()
	if err != nil {
		return fmt.Errorf("silent renewal: %w", err)
	}
	api.SetAccessToken(renewed.AccessToken)
	if _, err = api.GetProfile(); err != nil {
		return fmt.Errorf("profile with the renewed token: %w", err)
	}
	log.Info("token renewed silently")

	// 5. Telegram link token
	lt, err := api.GetLinkToken()
	if err != nil {
		return fmt.Errorf("get link token: %w", err)
	}
	log.Info("telegram link token obtained", zap.String("token", lt.Token[:8]+"…"))

	// 6. Sign out in the first browser
	if err = account.Browser.SignOut(account.Tokens.IDToken); err != nil {
		return fmt.Errorf("sign out: %w", err)
	}
	if _, err = account.Browser.Renew(); !errors.Is(err, keycloak.ErrSignedOut) {
		return fmt.Errorf("renewal after signing out: got %v, want a signed-out session", err)
	}
	log.Info("signed out of the first browser")

	// 7. Log out everywhere
	if err = api.LogoutAll(); err != nil {
		return fmt.Errorf("logout-all: %w", err)
	}
	if _, err = other.Renew(); !errors.Is(err, keycloak.ErrSignedOut) {
		return fmt.Errorf("renewal after logout-all: got %v, want a signed-out session", err)
	}
	log.Info("logged out everywhere")

	log.Info("=== SessionLifecycleScenario done ===")
	return nil
}
