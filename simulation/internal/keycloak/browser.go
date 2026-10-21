// Package keycloak signs users up and in on Keycloak's own pages, as a browser would
// (docs/adr/0001): realm `fate`, the SPA's client `fate-spa`, Authorization Code + PKCE. Keycloak
// is served on the SPA's origin under /auth, so a Browser is created for that origin — the dev
// server (http://localhost:3000) or the gateway (http://localhost:30080).
package keycloak

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"regexp"
	"strings"
	"time"
)

const (
	realmPath = "/auth/realms/fate"
	clientID  = "fate-spa"
	// ActionTokenPath is where the links Keycloak mails (email verification) point.
	ActionTokenPath = realmPath + "/login-actions/action-token"
)

// ErrSignedOut means Keycloak has no session for the browser: a silent sign-in needs a login.
var ErrSignedOut = errors.New("keycloak: no session (login_required)")

// Mailbox finds a link Keycloak mailed (see mailbox.Mailpit).
type Mailbox interface {
	Link(to, pathPrefix string) (string, error)
}

// Tokens is what the SPA gets after signing in; no refresh token is issued to `fate-spa`.
type Tokens struct {
	AccessToken string `json:"access_token"`
	IDToken     string `json:"id_token"`
}

// Browser keeps Keycloak's session cookies like a browser tab does.
type Browser struct {
	app  string
	http *http.Client
}

func New(app string) (*Browser, error) {
	jar, err := cookiejar.New(nil)
	if err != nil {
		return nil, err
	}
	app = strings.TrimRight(app, "/")
	appURL, err := url.Parse(app)
	if err != nil {
		return nil, err
	}
	return &Browser{
		app: app,
		http: &http.Client{
			Jar:     jar,
			Timeout: 15 * time.Second,
			// Follow Keycloak's own redirects, but stop where it hands the browser back to the app.
			CheckRedirect: func(req *http.Request, _ []*http.Request) error {
				if req.URL.Host == appURL.Host && !strings.HasPrefix(req.URL.Path, "/auth/") {
					return http.ErrUseLastResponse
				}
				return nil
			},
		},
	}, nil
}

// Register fills in Keycloak's registration form, follows the verification link it mails and
// sets the password — with email verification on, Keycloak asks for it only then.
func (b *Browser) Register(email, firstName, lastName, password string, mail Mailbox) (Tokens, error) {
	flow, err := b.authorize("create")
	if err != nil {
		return Tokens{}, err
	}
	page, err := b.submit(flow.page, url.Values{"email": {email}, "firstName": {firstName}, "lastName": {lastName}})
	if err != nil {
		return Tokens{}, fmt.Errorf("registration form: %w", err)
	}
	// A rejected form comes back with its errors; an accepted one asks to confirm the email.
	if page.fieldError() != "" {
		return Tokens{}, fmt.Errorf("registration form: %s", page.problem())
	}
	link, err := mail.Link(email, ActionTokenPath)
	if err != nil {
		return Tokens{}, fmt.Errorf("verification email: %w", err)
	}
	page, err = b.open(link)
	if err != nil {
		return Tokens{}, fmt.Errorf("verification link: %w", err)
	}
	page, err = b.submit(page, url.Values{"password-new": {password}, "password-confirm": {password}})
	if err != nil {
		return Tokens{}, fmt.Errorf("password form: %w", err)
	}
	return b.exchange(page, flow.verifier)
}

// SignIn fills in Keycloak's login form.
func (b *Browser) SignIn(email, password string) (Tokens, error) {
	flow, err := b.authorize("")
	if err != nil {
		return Tokens{}, err
	}
	page, err := b.submit(flow.page, url.Values{"username": {email}, "password": {password}})
	if err != nil {
		return Tokens{}, fmt.Errorf("login form: %w", err)
	}
	return b.exchange(page, flow.verifier)
}

// Renew signs in silently with Keycloak's session cookie, as the SPA's hidden iframe does.
func (b *Browser) Renew() (Tokens, error) {
	flow, err := b.authorize("none")
	if err != nil {
		return Tokens{}, err
	}
	if flow.page.callbackErr == "login_required" {
		return Tokens{}, ErrSignedOut
	}
	return b.exchange(flow.page, flow.verifier)
}

// SignOut ends the Keycloak session (RP-initiated logout) and comes back to the app's /login.
func (b *Browser) SignOut(idToken string) error {
	query := url.Values{
		"client_id":                {clientID},
		"id_token_hint":            {idToken},
		"post_logout_redirect_uri": {b.app + "/login"},
	}
	page, err := b.open(b.app + realmPath + "/protocol/openid-connect/logout?" + query.Encode())
	if err != nil {
		return err
	}
	if page.location != b.app+"/login" {
		return fmt.Errorf("logout did not return to the app: %s", page.problem())
	}
	return nil
}

// ── Pages ────────────────────────────────────────────────────────────────────

// page is where a request ended: a Keycloak page (body, its form) or a redirect back to the app.
type page struct {
	status      int
	body        string
	form        string // where the page's form posts
	location    string // set when Keycloak sent the browser back to the app
	code        string // authorization code in that redirect
	callbackErr string // OAuth error in that redirect
}

// Keycloak's pages come from the app's Keycloakify theme (frontend/src/login/): the HTML is a
// shell, the form is rendered in the browser from `kcContext`, a JS object in the page. Its
// string values are JSON strings; field errors live in the body of `messagesPerField.get`.
var (
	pageID = regexp.MustCompile(`kcContext\.pageId = "([^"]+)"`)
	// The form of the registration page posts to registrationAction, every other one to loginAction.
	loginAction        = regexp.MustCompile(`"loginAction":\s*("(?:[^"\\]|\\.)*")`)
	registrationAction = regexp.MustCompile(`"registrationAction":\s*("(?:[^"\\]|\\.)*")`)
	// The page's message, errors and notices alike — to explain an unexpected page.
	messageSummary = regexp.MustCompile(`"summary":\s*("(?:[^"\\]|\\.)*")`)
	// A form field Keycloak rejected (a taken email, a weak password…).
	fieldError = regexp.MustCompile(`\{ return '((?:[^'\\]|\\.)+)'; \}`)
)

// jsonString decodes a quoted JSON string from kcContext; "" if it is not one.
func jsonString(quoted string) string {
	var s string
	if json.Unmarshal([]byte(quoted), &s) != nil {
		return ""
	}
	return s
}

// fieldError is the first field Keycloak rejected on the page, "" when none.
func (p page) fieldError() string {
	if m := fieldError.FindStringSubmatch(p.body); m != nil {
		return strings.ReplaceAll(m[1], `\'`, "'")
	}
	return ""
}

func (p page) problem() string {
	if m := messageSummary.FindStringSubmatch(p.body); m != nil {
		return jsonString(m[1])
	}
	if e := p.fieldError(); e != "" {
		return e
	}
	if m := pageID.FindStringSubmatch(p.body); m != nil {
		return fmt.Sprintf("HTTP %d, page %s", p.status, m[1])
	}
	return fmt.Sprintf("HTTP %d", p.status)
}

func (b *Browser) read(resp *http.Response) (page, error) {
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return page{}, err
	}
	p := page{status: resp.StatusCode, body: string(body)}
	if loc := resp.Header.Get("Location"); resp.StatusCode/100 == 3 && loc != "" {
		p.location = loc
		if target, err := url.Parse(loc); err == nil {
			p.code = target.Query().Get("code")
			p.callbackErr = target.Query().Get("error")
		}
	}
	action := loginAction
	if m := pageID.FindStringSubmatch(p.body); m != nil && m[1] == "register.ftl" {
		action = registrationAction
	}
	if m := action.FindStringSubmatch(p.body); m != nil {
		p.form = jsonString(m[1])
	}
	return p, nil
}

func (b *Browser) open(link string) (page, error) {
	resp, err := b.http.Get(link)
	if err != nil {
		return page{}, err
	}
	return b.read(resp)
}

func (b *Browser) submit(current page, fields url.Values) (page, error) {
	if current.form == "" {
		return page{}, fmt.Errorf("no form on the page: %s", current.problem())
	}
	resp, err := b.http.PostForm(current.form, fields)
	if err != nil {
		return page{}, err
	}
	return b.read(resp)
}

// ── OAuth ────────────────────────────────────────────────────────────────────

type flow struct {
	page     page
	verifier string
}

func (b *Browser) authorize(prompt string) (flow, error) {
	verifier := random()
	challenge := sha256.Sum256([]byte(verifier))
	query := url.Values{
		"client_id":             {clientID},
		"response_type":         {"code"},
		"scope":                 {"openid profile email"},
		"redirect_uri":          {b.app + "/oidc/callback"},
		"state":                 {random()},
		"code_challenge":        {base64.RawURLEncoding.EncodeToString(challenge[:])},
		"code_challenge_method": {"S256"},
	}
	if prompt != "" {
		query.Set("prompt", prompt)
	}
	p, err := b.open(b.app + realmPath + "/protocol/openid-connect/auth?" + query.Encode())
	if err != nil {
		return flow{}, fmt.Errorf("authorization request: %w", err)
	}
	return flow{page: p, verifier: verifier}, nil
}

func (b *Browser) exchange(p page, verifier string) (Tokens, error) {
	if p.code == "" {
		if p.callbackErr != "" {
			return Tokens{}, fmt.Errorf("sign-in failed: %s", p.callbackErr)
		}
		return Tokens{}, fmt.Errorf("sign-in did not complete: %s", p.problem())
	}
	resp, err := b.http.PostForm(b.app+realmPath+"/protocol/openid-connect/token", url.Values{
		"grant_type":    {"authorization_code"},
		"client_id":     {clientID},
		"code":          {p.code},
		"redirect_uri":  {b.app + "/oidc/callback"},
		"code_verifier": {verifier},
	})
	if err != nil {
		return Tokens{}, fmt.Errorf("token request: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return Tokens{}, fmt.Errorf("token request: HTTP %d: %s", resp.StatusCode, body)
	}
	var tokens Tokens
	if err := json.NewDecoder(resp.Body).Decode(&tokens); err != nil {
		return Tokens{}, fmt.Errorf("token response: %w", err)
	}
	return tokens, nil
}

func random() string {
	b := make([]byte, 32)
	_, _ = rand.Read(b)
	return base64.RawURLEncoding.EncodeToString(b)
}
