// Package keycloaktest is a stand-in for Keycloak's pages and endpoints that keycloak.Browser
// uses, for tests: the same forms, redirects and PKCE check, with mailed links kept in memory.
package keycloaktest

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync"
	"testing"
)

const realm = "/auth/realms/fate"

type user struct {
	email, name, password string
}

type pending struct {
	redirectURI, state, challenge, email string
}

type Server struct {
	*httptest.Server

	mu           sync.Mutex
	users        map[string]*user   // by email
	sessions     map[string]string  // SSO session cookie → email
	flows        map[string]pending // auth session cookie → the authorization request in progress
	codes        map[string]pending // authorization code → request (email set)
	mails        map[string]string  // email → last mailed link
	tokens       int
	sessionCount int
}

func New(t *testing.T) *Server {
	t.Helper()
	s := &Server{
		users: map[string]*user{}, sessions: map[string]string{}, flows: map[string]pending{},
		codes: map[string]pending{}, mails: map[string]string{},
	}
	s.Server = httptest.NewServer(http.HandlerFunc(s.handle))
	t.Cleanup(s.Close)
	return s
}

// AddUser creates a confirmed account with a password.
func (s *Server) AddUser(email, password string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.users[email] = &user{email: email, name: "Test User", password: password}
}

// EndSessions logs the user out everywhere, as the Admin API does for the backend's logout-all.
func (s *Server) EndSessions(email string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	for id, owner := range s.sessions {
		if owner == email {
			delete(s.sessions, id)
		}
	}
}

// EmailOf the user an access token was issued to.
func (s *Server) EmailOf(accessToken string) string {
	email, _, _ := strings.Cut(strings.TrimPrefix(accessToken, "at:"), "#")
	return email
}

// Link is the mailbox: the last link mailed to `to` under `pathPrefix`.
func (s *Server) Link(to, pathPrefix string) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	link := s.mails[to]
	if parsed, err := url.Parse(link); err != nil || link == "" || !strings.HasPrefix(parsed.Path, pathPrefix) {
		return "", fmt.Errorf("no email to %s", to)
	}
	return link, nil
}

func (s *Server) handle(w http.ResponseWriter, r *http.Request) {
	s.mu.Lock()
	defer s.mu.Unlock()
	switch r.URL.Path {
	case realm + "/protocol/openid-connect/auth":
		s.authorize(w, r)
	case realm + "/login-actions/registration":
		s.register(w, r)
	case realm + "/login-actions/action-token":
		s.page(w, "Обновление пароля", realm+"/login-actions/required-action", "")
	case realm + "/login-actions/required-action":
		s.setPassword(w, r)
	case realm + "/login-actions/authenticate":
		s.authenticate(w, r)
	case realm + "/protocol/openid-connect/token":
		s.token(w, r)
	case realm + "/protocol/openid-connect/logout":
		if c, err := r.Cookie("KEYCLOAK_SESSION"); err == nil {
			delete(s.sessions, c.Value)
		}
		http.Redirect(w, r, r.URL.Query().Get("post_logout_redirect_uri"), http.StatusFound)
	default:
		http.NotFound(w, r)
	}
}

func (s *Server) authorize(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	if q.Get("client_id") != "fate-spa" || q.Get("code_challenge_method") != "S256" {
		http.Error(w, "invalid_request", http.StatusBadRequest)
		return
	}
	flow := pending{redirectURI: q.Get("redirect_uri"), state: q.Get("state"), challenge: q.Get("code_challenge")}
	if c, err := r.Cookie("KEYCLOAK_SESSION"); err == nil && s.sessions[c.Value] != "" && q.Get("prompt") != "create" {
		flow.email = s.sessions[c.Value]
		s.redirectWithCode(w, r, flow)
		return
	}
	if q.Get("prompt") == "none" {
		http.Redirect(w, r, flow.redirectURI+"?error=login_required&state="+flow.state, http.StatusFound)
		return
	}
	id := fmt.Sprintf("flow-%d", len(s.flows)+1)
	s.flows[id] = flow
	http.SetCookie(w, &http.Cookie{Name: "AUTH_SESSION", Value: id, Path: "/"})
	if q.Get("prompt") == "create" {
		s.page(w, "Регистрация", realm+"/login-actions/registration", "")
	} else {
		s.page(w, "Вход", realm+"/login-actions/authenticate", "")
	}
}

func (s *Server) register(w http.ResponseWriter, r *http.Request) {
	flow, id, ok := s.flow(r)
	email := r.PostFormValue("email")
	if !ok || email == "" || s.users[email] != nil {
		s.page(w, "Регистрация", realm+"/login-actions/registration", "Email already exists.")
		return
	}
	s.users[email] = &user{email: email, name: r.PostFormValue("firstName") + " " + r.PostFormValue("lastName")}
	flow.email = email
	s.flows[id] = flow
	s.mails[email] = s.URL + realm + "/login-actions/action-token?key=" + id
	s.page(w, "Подтверждение адреса E-mail", "", "")
}

func (s *Server) setPassword(w http.ResponseWriter, r *http.Request) {
	flow, _, ok := s.flow(r)
	if !ok || r.PostFormValue("password-new") == "" || r.PostFormValue("password-new") != r.PostFormValue("password-confirm") {
		s.page(w, "Обновление пароля", realm+"/login-actions/required-action", "Passwords don't match.")
		return
	}
	s.users[flow.email].password = r.PostFormValue("password-new")
	s.redirectWithCode(w, r, flow)
}

func (s *Server) authenticate(w http.ResponseWriter, r *http.Request) {
	flow, _, ok := s.flow(r)
	u := s.users[r.PostFormValue("username")]
	if !ok || u == nil || u.password == "" || u.password != r.PostFormValue("password") {
		s.page(w, "Вход", realm+"/login-actions/authenticate", "Invalid username or password.")
		return
	}
	flow.email = u.email
	s.redirectWithCode(w, r, flow)
}

func (s *Server) redirectWithCode(w http.ResponseWriter, r *http.Request, flow pending) {
	// Each browser keeps its own session; a silent sign-in continues the one it has.
	session := ""
	if c, err := r.Cookie("KEYCLOAK_SESSION"); err == nil && s.sessions[c.Value] == flow.email {
		session = c.Value
	} else {
		s.sessionCount++
		session = fmt.Sprintf("sso-%d", s.sessionCount)
		s.sessions[session] = flow.email
	}
	http.SetCookie(w, &http.Cookie{Name: "KEYCLOAK_SESSION", Value: session, Path: realm})
	code := fmt.Sprintf("code-%d", len(s.codes)+1)
	s.codes[code] = flow
	http.Redirect(w, r, flow.redirectURI+"?state="+flow.state+"&code="+code, http.StatusFound)
}

func (s *Server) token(w http.ResponseWriter, r *http.Request) {
	flow, ok := s.codes[r.PostFormValue("code")]
	delete(s.codes, r.PostFormValue("code"))
	sum := sha256.Sum256([]byte(r.PostFormValue("code_verifier")))
	if !ok || base64.RawURLEncoding.EncodeToString(sum[:]) != flow.challenge || r.PostFormValue("redirect_uri") != flow.redirectURI {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte(`{"error":"invalid_grant"}`))
		return
	}
	s.tokens++
	_ = json.NewEncoder(w).Encode(map[string]any{
		"access_token": fmt.Sprintf("at:%s#%d", flow.email, s.tokens),
		"id_token":     "idt:" + flow.email,
		"expires_in":   300,
	})
}

func (s *Server) flow(r *http.Request) (pending, string, bool) {
	c, err := r.Cookie("AUTH_SESSION")
	if err != nil {
		return pending{}, "", false
	}
	if key := r.URL.Query().Get("key"); key != "" {
		c.Value = key
	}
	flow, ok := s.flows[c.Value]
	return flow, c.Value, ok
}

func (s *Server) page(w http.ResponseWriter, title, action, problem string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	body := "<html><head><title>" + title + "</title></head><body>"
	if problem != "" {
		body += `<span id="input-error" class="pf-m-danger">` + problem + "</span>"
	}
	if action != "" {
		body += `<form id="kc-form" method="post" action="` + s.URL + action + `?session_code=x&amp;tab_id=y"><input name="x"/></form>`
	}
	_, _ = w.Write([]byte(body + "</body></html>"))
}
