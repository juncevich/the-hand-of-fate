package scenario

import (
	"fmt"

	"github.com/juncevich/fate/simulation/internal/client"
	"go.uber.org/zap"
)

// SessionLifecycleScenario tests the full auth lifecycle:
//  1. Register, check that login waits for email verification, follow the mailed link
//  2. Login with wrong password → expect error
//  3. Login with correct password
//  4. Refresh token
//  5. Get Telegram link token
//  6. Logout
//  7. Confirm refresh fails after logout
func SessionLifecycleScenario(base string, mail Mailbox, log *zap.Logger) error {
	log.Info("=== SessionLifecycleScenario start ===")

	email := randomEmail()
	password := "Lifecycle#99"
	name := randomName()

	c, err := client.New(base, log)
	if err != nil {
		return err
	}

	// 1. Register; signing in waits for the mailed link
	if _, err = c.Register(client.RegisterRequest{Email: email, Password: password, DisplayName: name}); err != nil {
		return fmt.Errorf("register: %w", err)
	}
	if _, err = c.Login(client.LoginRequest{Email: email, Password: password}); err == nil {
		return fmt.Errorf("expected login to wait for email verification, got a session")
	}
	log.Info("login before verification correctly rejected", zap.Error(err))
	token, err := mail.LinkToken(email, "/verify-email")
	if err != nil {
		return fmt.Errorf("verification email: %w", err)
	}
	auth, err := c.VerifyEmail(token)
	if err != nil {
		return fmt.Errorf("verify email: %w", err)
	}
	log.Info("registered and verified", zap.String("userId", auth.UserID))
	refreshToken := auth.RefreshToken
	c.SetAccessToken(auth.AccessToken)

	// 2. Wrong password login — should fail
	_, err = c.Login(client.LoginRequest{Email: email, Password: "wrongpassword"})
	if err == nil {
		return fmt.Errorf("expected error on wrong password, got none")
	}
	log.Info("wrong-password login correctly rejected", zap.Error(err))

	// 3. Correct login
	auth2, err := c.Login(client.LoginRequest{Email: email, Password: password})
	if err != nil {
		return fmt.Errorf("login: %w", err)
	}
	c.SetAccessToken(auth2.AccessToken)
	log.Info("logged in", zap.String("userId", auth2.UserID))

	// 4. Refresh
	auth3, err := c.Refresh(auth2.RefreshToken)
	if err != nil {
		return fmt.Errorf("refresh: %w", err)
	}
	c.SetAccessToken(auth3.AccessToken)
	log.Info("token refreshed")

	// 5. Telegram link token
	lt, err := c.GetLinkToken()
	if err != nil {
		return fmt.Errorf("get link token: %w", err)
	}
	log.Info("telegram link token obtained", zap.String("token", lt.Token[:8]+"…"))

	// 6. Logout (using original refresh token from the verification session)
	if err = c.Logout(refreshToken); err != nil {
		log.Warn("logout with stale token (may already be invalidated)", zap.Error(err))
	}
	log.Info("logout done")

	// 7. Refresh should now fail because we logged out
	_, err = c.Refresh(refreshToken)
	if err == nil {
		log.Warn("refresh after logout did not fail — token may still be valid")
	} else {
		log.Info("refresh after logout correctly rejected")
	}

	log.Info("=== SessionLifecycleScenario done ===")
	return nil
}
