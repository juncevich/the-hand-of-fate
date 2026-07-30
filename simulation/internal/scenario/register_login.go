package scenario

import (
	"fmt"

	"github.com/juncevich/fate/simulation/internal/client"
	"go.uber.org/zap"
)

// RegisterAndLogin registers a new random user, logs in, then refreshes the token.
// Returns the authenticated client and AuthResponse.
func RegisterAndLogin(base string, log *zap.Logger) (*client.Client, client.AuthResponse, error) {
	c, err := client.New(base, log)
	if err != nil {
		return nil, client.AuthResponse{}, fmt.Errorf("new client: %w", err)
	}

	email := randomEmail()
	password := "Simulation#1"
	name := randomName()

	log.Info("registering user", zap.String("email", email), zap.String("name", name))
	auth, err := c.Register(client.RegisterRequest{
		Email:       email,
		Password:    password,
		DisplayName: name,
	})
	if err != nil {
		return nil, auth, fmt.Errorf("register: %w", err)
	}
	log.Info("registered", zap.String("userId", auth.UserID))

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
