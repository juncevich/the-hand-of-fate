package client_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/juncevich/fate/simulation/internal/client"
	"go.uber.org/zap"
)

func newLogger(t *testing.T) *zap.Logger {
	t.Helper()
	log, _ := zap.NewDevelopment()
	t.Cleanup(func() { _ = log.Sync() })
	return log
}

func toJSON(v any) []byte {
	b, _ := json.Marshal(v)
	return b
}

func TestNew(t *testing.T) {
	c, err := client.New("http://localhost:8080", newLogger(t))
	if err != nil {
		t.Fatalf("New() error: %v", err)
	}
	if c == nil {
		t.Fatal("New() returned nil")
	}
}

func TestNew_TrimsTrailingSlash(t *testing.T) {
	var gotPath string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL+"/", newLogger(t))
	_ = c.DeleteVote("abc")
	if gotPath != "/api/v1/votes/abc" {
		t.Errorf("path = %q, want /api/v1/votes/abc", gotPath)
	}
}

func TestSetAccessToken(t *testing.T) {
	var gotAuth string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	c.SetAccessToken("my-token")
	_ = c.DeleteVote("1")

	if gotAuth != "Bearer my-token" {
		t.Errorf("Authorization = %q, want 'Bearer my-token'", gotAuth)
	}
}

func TestSetAccessToken_Empty(t *testing.T) {
	var gotAuth string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	_ = c.DeleteVote("1")

	if gotAuth != "" {
		t.Errorf("Authorization = %q, want empty", gotAuth)
	}
}

// ── Auth ──────────────────────────────────────────────────────────────────────

func TestRegister(t *testing.T) {
	payload := client.AuthResponse{AccessToken: "at-reg", UserID: "u-1", Email: "a@a.com", DisplayName: "Alice"}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/auth/register" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-reg"})
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(payload))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	auth, err := c.Register(client.RegisterRequest{Email: "a@a.com", Password: "p", DisplayName: "Alice"})
	if err != nil {
		t.Fatalf("Register() error: %v", err)
	}
	if auth.AccessToken != "at-reg" {
		t.Errorf("AccessToken = %q", auth.AccessToken)
	}
	if auth.RefreshToken != "rt-reg" {
		t.Errorf("RefreshToken = %q, want rt-reg", auth.RefreshToken)
	}
}

func TestRegister_HTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusConflict)
		_, _ = w.Write(toJSON(map[string]string{"error": "email taken"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	_, err := c.Register(client.RegisterRequest{Email: "dup@a.com", Password: "p", DisplayName: "X"})
	if err == nil {
		t.Fatal("expected error on HTTP 409")
	}
}

func TestLogin(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/auth/login" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "rt-login"})
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(client.AuthResponse{AccessToken: "at-login", UserID: "u-2"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	auth, err := c.Login(client.LoginRequest{Email: "u@u.com", Password: "pass"})
	if err != nil {
		t.Fatalf("Login() error: %v", err)
	}
	if auth.AccessToken != "at-login" {
		t.Errorf("AccessToken = %q", auth.AccessToken)
	}
	if auth.RefreshToken != "rt-login" {
		t.Errorf("RefreshToken = %q", auth.RefreshToken)
	}
}

func TestLogin_Unauthorized(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write(toJSON(map[string]string{"error": "invalid credentials"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	_, err := c.Login(client.LoginRequest{Email: "u@u.com", Password: "wrong"})
	if err == nil {
		t.Fatal("expected error on 401")
	}
}

func TestRefresh_WithToken(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/auth/refresh" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		http.SetCookie(w, &http.Cookie{Name: "fate_refresh_token", Value: "new-rt"})
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(client.AuthResponse{AccessToken: "new-at"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	auth, err := c.Refresh("old-rt")
	if err != nil {
		t.Fatalf("Refresh() error: %v", err)
	}
	if auth.AccessToken != "new-at" {
		t.Errorf("AccessToken = %q", auth.AccessToken)
	}
	if auth.RefreshToken != "new-rt" {
		t.Errorf("RefreshToken = %q", auth.RefreshToken)
	}
}

func TestRefresh_EmptyToken(t *testing.T) {
	called := false
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called = true
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(client.AuthResponse{AccessToken: "at-empty"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	_, err := c.Refresh("")
	if err != nil {
		t.Fatalf("Refresh('') error: %v", err)
	}
	if !called {
		t.Error("expected handler to be called")
	}
}

func TestRefresh_Error(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write(toJSON(map[string]string{"error": "token expired"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	_, err := c.Refresh("expired-token")
	if err == nil {
		t.Fatal("expected error on 401")
	}
}

func TestLogout(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/auth/logout" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.Logout("rt-abc"); err != nil {
		t.Fatalf("Logout() error: %v", err)
	}
}

func TestLogout_ServerError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte("internal error"))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.Logout("rt"); err == nil {
		t.Fatal("expected error on 500")
	}
}

// ── Votes ─────────────────────────────────────────────────────────────────────

func TestCreateVote(t *testing.T) {
	expected := client.VoteDetail{ID: "vote-1", Title: "Lunch Spot", Mode: "SIMPLE"}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/votes" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(expected))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	got, err := c.CreateVote(client.CreateVoteRequest{Title: "Lunch Spot", Mode: "SIMPLE"})
	if err != nil {
		t.Fatalf("CreateVote() error: %v", err)
	}
	if got.ID != "vote-1" || got.Title != "Lunch Spot" {
		t.Errorf("CreateVote() = %+v", got)
	}
}

func TestCreateVote_SetsContentType(t *testing.T) {
	var gotCT string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotCT = r.Header.Get("Content-Type")
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(client.VoteDetail{ID: "v"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	_, _ = c.CreateVote(client.CreateVoteRequest{Title: "T", Mode: "SIMPLE"})
	if gotCT != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", gotCT)
	}
}

func TestListVotes(t *testing.T) {
	page := client.Page[client.VoteSummary]{
		Content:       []client.VoteSummary{{ID: "v1", Title: "Vote 1"}},
		TotalElements: 1,
		TotalPages:    1,
		Number:        0,
		Size:          20,
	}
	var gotQuery string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotQuery = r.URL.RawQuery
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(page))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	got, err := c.ListVotes(0, 20)
	if err != nil {
		t.Fatalf("ListVotes() error: %v", err)
	}
	if got.TotalElements != 1 || len(got.Content) != 1 || got.Content[0].ID != "v1" {
		t.Errorf("ListVotes() = %+v", got)
	}
	if gotQuery == "" {
		t.Error("expected query params in request")
	}
}

func TestGetVote(t *testing.T) {
	detail := client.VoteDetail{ID: "vote-42", Title: "Decisions"}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/votes/vote-42" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(detail))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	got, err := c.GetVote("vote-42")
	if err != nil {
		t.Fatalf("GetVote() error: %v", err)
	}
	if got.ID != "vote-42" {
		t.Errorf("ID = %q", got.ID)
	}
}

func TestGetVote_NotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusNotFound)
		_, _ = w.Write(toJSON(map[string]string{"error": "not found"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	_, err := c.GetVote("missing")
	if err == nil {
		t.Fatal("expected error on 404")
	}
}

func TestDeleteVote(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodDelete || r.URL.Path != "/api/v1/votes/vote-99" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.DeleteVote("vote-99"); err != nil {
		t.Fatalf("DeleteVote() error: %v", err)
	}
}

func TestAddParticipant(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/votes/v1/participants" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.AddParticipant("v1", "user@example.com"); err != nil {
		t.Fatalf("AddParticipant() error: %v", err)
	}
}

func TestRemoveParticipant(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodDelete || r.URL.Path != "/api/v1/votes/v1/participants/user@example.com" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.RemoveParticipant("v1", "user@example.com"); err != nil {
		t.Fatalf("RemoveParticipant() error: %v", err)
	}
}

func TestAddOption(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/votes/v1/options" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.AddOption("v1", "Option Alpha"); err != nil {
		t.Fatalf("AddOption() error: %v", err)
	}
}

func TestRemoveOption(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodDelete || r.URL.Path != "/api/v1/votes/v1/options/opt-5" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.RemoveOption("v1", "opt-5"); err != nil {
		t.Fatalf("RemoveOption() error: %v", err)
	}
}

func TestDraw(t *testing.T) {
	title := "Option Alpha"
	result := client.DrawResultResponse{WinnerOptionTitle: &title, Round: 1}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/votes/v1/draw" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(result))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	got, err := c.Draw("v1")
	if err != nil {
		t.Fatalf("Draw() error: %v", err)
	}
	if got.Round != 1 {
		t.Errorf("Round = %d", got.Round)
	}
	if got.WinnerOptionTitle == nil || *got.WinnerOptionTitle != "Option Alpha" {
		t.Errorf("WinnerOptionTitle = %v", got.WinnerOptionTitle)
	}
}

func TestDraw_Error(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write(toJSON(map[string]string{"error": "vote is closed"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	_, err := c.Draw("v1")
	if err == nil {
		t.Fatal("expected error on 400")
	}
}

func TestReopen(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/votes/v1/reopen" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.Reopen("v1"); err != nil {
		t.Fatalf("Reopen() error: %v", err)
	}
}

func TestClose(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/votes/v1/close" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.Close("v1"); err != nil {
		t.Fatalf("Close() error: %v", err)
	}
}

func TestGetHistory(t *testing.T) {
	email := "winner@example.com"
	history := []client.DrawHistoryDto{
		{ID: "h1", WinnerEmail: &email, Round: 1, DrawnAt: time.Now()},
		{ID: "h2", WinnerEmail: &email, Round: 1, DrawnAt: time.Now()},
	}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/votes/v1/history" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(history))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	got, err := c.GetHistory("v1")
	if err != nil {
		t.Fatalf("GetHistory() error: %v", err)
	}
	if len(got) != 2 {
		t.Errorf("len(history) = %d, want 2", len(got))
	}
	if got[0].ID != "h1" {
		t.Errorf("history[0].ID = %q", got[0].ID)
	}
}

// ── Telegram ──────────────────────────────────────────────────────────────────

func TestGetLinkToken(t *testing.T) {
	lt := client.LinkTokenResponse{Token: "tok-abc", ExpiresAt: time.Now().Add(5 * time.Minute)}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet || r.URL.Path != "/api/v1/telegram/link-token" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(lt))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	got, err := c.GetLinkToken()
	if err != nil {
		t.Fatalf("GetLinkToken() error: %v", err)
	}
	if got.Token != "tok-abc" {
		t.Errorf("Token = %q", got.Token)
	}
}

func TestUnlinkTelegram(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodDelete || r.URL.Path != "/api/v1/telegram/unlink" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.UnlinkTelegram(); err != nil {
		t.Fatalf("UnlinkTelegram() error: %v", err)
	}
}

func TestUnlinkTelegram_NotLinked(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write(toJSON(map[string]string{"error": "not linked"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.UnlinkTelegram(); err == nil {
		t.Fatal("expected error on 400")
	}
}
