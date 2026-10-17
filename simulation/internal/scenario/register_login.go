package scenario

import (
	"fmt"

	"github.com/juncevich/fate/simulation/internal/client"
	"go.uber.org/zap"
)

// Mailbox finds the token of a link the backend mailed (see mailbox.Mailpit).
type Mailbox interface {
	LinkToken(to, path string) (string, error)
}

// RegisterVerified registers an account and confirms it through the mailed link, which signs it in.
func RegisterVerified(c *client.Client, mail Mailbox, req client.RegisterRequest, log *zap.Logger) (client.Session, error) {
	log.Info("registering user", zap.String("email", req.Email), zap.String("name", req.DisplayName))
	registered, err := c.Register(req)
	if err != nil {
		return client.Session{}, fmt.Errorf("register: %w", err)
	}
	token, err := mail.LinkToken(registered.Email, "/verify-email")
	if err != nil {
		return client.Session{}, fmt.Errorf("verification email: %w", err)
	}
	auth, err := c.VerifyEmail(token)
	if err != nil {
		return client.Session{}, fmt.Errorf("verify email: %w", err)
	}
	log.Info("registered and verified", zap.String("userId", auth.UserID))
	return auth, nil
}

// RegisterAndLogin registers a new random user, confirms the address, then refreshes the token.
// Returns the authenticated client and AuthResponse.
func RegisterAndLogin(base string, mail Mailbox, log *zap.Logger) (*client.Client, client.Session, error) {
	c, err := client.New(base, log)
	if err != nil {
		return nil, client.Session{}, fmt.Errorf("new client: %w", err)
	}

	auth, err := RegisterVerified(c, mail, client.RegisterRequest{
		Email:       randomEmail(),
		Password:    "Simulation#1",
		DisplayName: randomName(),
	}, log)
	if err != nil {
		return nil, auth, err
	}
	c.SetAccessToken(auth.AccessToken)

	log.Info("refreshing token")
	auth, err = c.Refresh(auth.RefreshToken)
	if err != nil {
		return nil, auth, fmt.Errorf("refresh: %w", err)
	}
	c.SetAccessToken(auth.AccessToken)
	log.Info("token refreshed")

	return c, auth, nil
}
