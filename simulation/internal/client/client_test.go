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

func TestCreateVote(t *testing.T) {
	expected := client.VoteDetailDto{ID: "vote-1", Title: "Lunch Spot", Mode: "SIMPLE"}
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
	mode := client.VoteModeSIMPLE
	got, err := c.CreateVote(client.CreateVoteWebRequest{Title: "Lunch Spot", Mode: &mode})
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
		_, _ = w.Write(toJSON(client.VoteDetailDto{ID: "v"}))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	_, _ = c.CreateVote(client.CreateVoteWebRequest{Title: "T"})
	if gotCT != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", gotCT)
	}
}

func TestListVotes(t *testing.T) {
	page := client.PageResponseVoteSummaryDto{
		Content:       []client.VoteSummaryDto{{ID: "v1", Title: "Vote 1"}},
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
	detail := client.VoteDetailDto{ID: "vote-42", Title: "Decisions"}
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
		if r.URL.Path != "/api/v1/votes/v1/history/page" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(client.PageResponseDrawHistoryDto{Content: history, TotalPages: 1}))
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

func TestGetHistoryCollectsAllPages(t *testing.T) {
	requests := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/votes/v1/history/page" || r.URL.Query().Get("size") != "100" {
			t.Errorf("unexpected history request: %s", r.URL)
		}
		page := r.URL.Query().Get("page")
		requests++
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(toJSON(client.PageResponseDrawHistoryDto{
			Content: []client.DrawHistoryDto{{ID: "h" + page}}, TotalPages: 2, TotalElements: 2,
		}))
	}))
	defer srv.Close()
	c, _ := client.New(srv.URL, newLogger(t))
	got, err := c.GetHistory("v1")
	if err != nil || len(got) != 2 || got[0].ID != "h0" || got[1].ID != "h1" || requests != 2 {
		t.Fatalf("history=%#v error=%v requests=%d", got, err, requests)
	}
}

func TestGetHistoryRejectsMalformedPage(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte("invalid JSON"))
	}))
	defer srv.Close()
	c, _ := client.New(srv.URL, newLogger(t))
	if _, err := c.GetHistory("v1"); err == nil {
		t.Fatal("expected malformed page error")
	}
}

func TestGetProfile(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet || r.URL.Path != "/api/v1/users/me" {
			t.Errorf("unexpected %s %s", r.Method, r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer kc-token" {
			t.Errorf("Authorization = %q", got)
		}
		_ = json.NewEncoder(w).Encode(client.ProfileResponse{UserID: "u1", Email: "ann@test.com", DisplayName: "Ann"})
	}))
	defer srv.Close()
	c, _ := client.New(srv.URL, newLogger(t))
	c.SetAccessToken("kc-token")

	profile, err := c.GetProfile()
	if err != nil || profile.Email != "ann@test.com" {
		t.Fatalf("GetProfile() = %+v, %v", profile, err)
	}
}

func TestLogoutAll(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/v1/auth/logout-all" {
			t.Errorf("unexpected %s %s", r.Method, r.URL.Path)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if err := c.LogoutAll(); err != nil {
		t.Fatalf("LogoutAll() error: %v", err)
	}
}
