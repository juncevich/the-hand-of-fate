package scenario

import (
	"fmt"
	"strings"

	"github.com/juncevich/fate/simulation/internal/client"
	"github.com/juncevich/fate/simulation/internal/keycloak"
	"go.uber.org/zap"
)

const password = "Simulation#1"

// Account is a user signed up through Keycloak, with the backend client acting as them.
type Account struct {
	Email   string
	API     *client.Client
	Browser *keycloak.Browser
	Tokens  keycloak.Tokens
}

// SignUp registers a new random user on Keycloak's pages at `app` (the SPA's origin), confirms the
// email through the mailbox, and checks the backend accepts the token — its first request creates
// the account there.
func SignUp(app, base string, mail keycloak.Mailbox, log *zap.Logger) (Account, error) {
	browser, err := keycloak.New(app)
	if err != nil {
		return Account{}, fmt.Errorf("browser: %w", err)
	}
	email := randomEmail()
	first, last, _ := strings.Cut(randomName(), " ")
	log.Info("signing up through Keycloak", zap.String("email", email))
	tokens, err := browser.Register(email, first, last, password, mail)
	if err != nil {
		return Account{}, fmt.Errorf("sign up: %w", err)
	}

	api, err := client.New(base, log)
	if err != nil {
		return Account{}, fmt.Errorf("new client: %w", err)
	}
	api.SetAccessToken(tokens.AccessToken)
	profile, err := api.GetProfile()
	if err != nil {
		return Account{}, fmt.Errorf("profile: %w", err)
	}
	if profile.Email != email {
		return Account{}, fmt.Errorf("profile email = %q, want %q", profile.Email, email)
	}
	log.Info("signed up", zap.String("userId", profile.UserID))
	return Account{Email: email, API: api, Browser: browser, Tokens: tokens}, nil
}

// SignedUpClient is SignUp for scenarios that only need the backend client.
func SignedUpClient(app, base string, mail keycloak.Mailbox, log *zap.Logger) (*client.Client, error) {
	account, err := SignUp(app, base, mail, log)
	return account.API, err
}
