package scenario

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/juncevich/fate/simulation/internal/client"
)

func TestWinnerLabel_OptionTitleFirst(t *testing.T) {
	title, name, email := "Option Alpha", "Alice", "a@a.com"
	r := client.DrawResultResponse{
		WinnerOptionTitle: &title,
		WinnerDisplayName: &name,
		WinnerEmail:       &email,
	}
	if got := winnerLabel(r); got != title {
		t.Errorf("winnerLabel = %q, want option title %q", got, title)
	}
}

func TestWinnerLabel_DisplayNameOverEmail(t *testing.T) {
	name, email := "Alice", "a@a.com"
	r := client.DrawResultResponse{WinnerDisplayName: &name, WinnerEmail: &email}
	if got := winnerLabel(r); got != name {
		t.Errorf("winnerLabel = %q, want display name %q", got, name)
	}
}

func TestWinnerLabel_EmailFallback(t *testing.T) {
	email := "a@a.com"
	r := client.DrawResultResponse{WinnerEmail: &email}
	if got := winnerLabel(r); got != email {
		t.Errorf("winnerLabel = %q, want email %q", got, email)
	}
}

func TestWinnerLabel_AllNil(t *testing.T) {
	if got := winnerLabel(client.DrawResultResponse{}); got != "<unknown>" {
		t.Errorf("winnerLabel = %q, want '<unknown>'", got)
	}
}

func TestWinnerLabel_OptionTitleOnly(t *testing.T) {
	title := "Option Beta"
	r := client.DrawResultResponse{WinnerOptionTitle: &title}
	if got := winnerLabel(r); got != title {
		t.Errorf("winnerLabel = %q, want %q", got, title)
	}
}

// ── SimpleVoteScenario ────────────────────────────────────────────────────────

func newSimpleVoteServer(t *testing.T) *httptest.Server {
	t.Helper()
	optTitle := "Option Alpha"
	draw := client.DrawResultResponse{WinnerOptionTitle: &optTitle, Round: 1}
	email := "winner@example.com"
	history := []client.DrawHistoryDto{{ID: "h1", WinnerEmail: &email, Round: 1, DrawnAt: time.Now()}}

	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{
				ID:      "v1",
				Title:   "Test Vote",
				Mode:    "SIMPLE",
				Status:  "OPEN",
				Options: []client.VoteOptionDto{{ID: "o1", Title: "Option Alpha"}},
			})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.Page[client.VoteSummary]{
				Content:       []client.VoteSummary{{ID: "v1", Title: "Test Vote"}},
				TotalElements: 1,
				TotalPages:    1,
			})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1":
			writeJSON(w, client.VoteDetail{ID: "v1", Title: "Test Vote", Mode: "SIMPLE"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/draw":
			writeJSON(w, draw)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1/history":
			writeJSON(w, history)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/reopen":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/close":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodDelete && r.URL.Path == "/api/v1/votes/v1":
			w.WriteHeader(http.StatusNoContent)
		default:
			t.Logf("unexpected request: %s %s", r.Method, r.URL.Path)
			w.WriteHeader(http.StatusNotFound)
		}
	}))
}

func TestSimpleVoteScenario_HappyPath(t *testing.T) {
	srv := newSimpleVoteServer(t)
	defer srv.Close()

	c := testClient(t, srv)
	if err := SimpleVoteScenario(c, testLogger(t)); err != nil {
		t.Errorf("SimpleVoteScenario() error: %v", err)
	}
}

func TestSimpleVoteScenario_CreateVoteError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusInternalServerError)
		writeJSON(w, map[string]string{"error": "server error"})
	}))
	defer srv.Close()

	c := testClient(t, srv)
	err := SimpleVoteScenario(c, testLogger(t))
	if err == nil {
		t.Fatal("expected error when CreateVote fails")
	}
}

func TestSimpleVoteScenario_DrawError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v1", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.Page[client.VoteSummary]{Content: []client.VoteSummary{{ID: "v1"}}})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1":
			writeJSON(w, client.VoteDetail{ID: "v1"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/draw":
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadRequest)
			writeJSON(w, map[string]string{"error": "no options"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	err := SimpleVoteScenario(c, testLogger(t))
	if err == nil {
		t.Fatal("expected error when Draw fails")
	}
}

func TestSimpleVoteScenario_ListVotesError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v1", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes":
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusInternalServerError)
			writeJSON(w, map[string]string{"error": "db error"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := SimpleVoteScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when ListVotes fails")
	}
}

func TestSimpleVoteScenario_GetVoteError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v1", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.Page[client.VoteSummary]{Content: []client.VoteSummary{{ID: "v1"}}})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1":
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusNotFound)
			writeJSON(w, map[string]string{"error": "not found"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := SimpleVoteScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when GetVote fails")
	}
}

func TestSimpleVoteScenario_GetHistoryError(t *testing.T) {
	optTitle := "Option Alpha"
	draw := client.DrawResultResponse{WinnerOptionTitle: &optTitle, Round: 1}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v1", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.Page[client.VoteSummary]{Content: []client.VoteSummary{{ID: "v1"}}})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1":
			writeJSON(w, client.VoteDetail{ID: "v1"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/draw":
			writeJSON(w, draw)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1/history":
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusInternalServerError)
			writeJSON(w, map[string]string{"error": "db error"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := SimpleVoteScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when GetHistory fails")
	}
}

func TestSimpleVoteScenario_ReopenError(t *testing.T) {
	optTitle := "Option Alpha"
	draw := client.DrawResultResponse{WinnerOptionTitle: &optTitle, Round: 1}
	email := "winner@example.com"
	history := []client.DrawHistoryDto{{ID: "h1", WinnerEmail: &email, Round: 1}}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v1", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.Page[client.VoteSummary]{Content: []client.VoteSummary{{ID: "v1"}}})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1":
			writeJSON(w, client.VoteDetail{ID: "v1"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/draw":
			writeJSON(w, draw)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1/history":
			writeJSON(w, history)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/reopen":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := SimpleVoteScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when Reopen fails")
	}
}

func TestSimpleVoteScenario_CloseError(t *testing.T) {
	optTitle := "Option Alpha"
	draw := client.DrawResultResponse{WinnerOptionTitle: &optTitle, Round: 1}
	email := "winner@example.com"
	history := []client.DrawHistoryDto{{ID: "h1", WinnerEmail: &email, Round: 1}}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v1", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.Page[client.VoteSummary]{Content: []client.VoteSummary{{ID: "v1"}}})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1":
			writeJSON(w, client.VoteDetail{ID: "v1"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/draw":
			writeJSON(w, draw)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1/history":
			writeJSON(w, history)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/reopen":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/close":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := SimpleVoteScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when Close fails")
	}
}

func TestSimpleVoteScenario_DeleteError(t *testing.T) {
	optTitle := "Option Alpha"
	draw := client.DrawResultResponse{WinnerOptionTitle: &optTitle, Round: 1}
	email := "winner@example.com"
	history := []client.DrawHistoryDto{{ID: "h1", WinnerEmail: &email, Round: 1}}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v1", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.Page[client.VoteSummary]{Content: []client.VoteSummary{{ID: "v1"}}})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1":
			writeJSON(w, client.VoteDetail{ID: "v1"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/draw":
			writeJSON(w, draw)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v1/history":
			writeJSON(w, history)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/reopen":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v1/close":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodDelete && r.URL.Path == "/api/v1/votes/v1":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := SimpleVoteScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when DeleteVote fails")
	}
}
