package scenario

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/juncevich/fate/simulation/internal/client"
)

func newOptionsVoteServer(t *testing.T) *httptest.Server {
	t.Helper()
	optTitle := "Option Alpha"
	draw := client.DrawResultResponse{WinnerOptionTitle: &optTitle, Round: 1}

	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v2", Title: "Options Vote", Mode: "SIMPLE", Status: "OPEN"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/options":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v2/votes/v2":
			writeJSON(w, client.VoteDetail{ID: "v2"})
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v2":
			writeJSON(w, client.VoteDetail{
				ID: "v2",
				Options: []client.VoteOptionDto{
					{ID: "o1", Title: "Option Alpha"},
					{ID: "o2", Title: "Option Beta"},
					{ID: "o3", Title: "Option Gamma"},
				},
			})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/draw":
			writeJSON(w, draw)
		case r.Method == http.MethodDelete && r.URL.Path == "/api/v1/votes/v2/options/o1":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/reopen":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodDelete && r.URL.Path == "/api/v1/votes/v2":
			w.WriteHeader(http.StatusNoContent)
		default:
			t.Logf("unexpected request: %s %s", r.Method, r.URL.Path)
			w.WriteHeader(http.StatusNotFound)
		}
	}))
}

func TestOptionsVoteScenario_HappyPath(t *testing.T) {
	srv := newOptionsVoteServer(t)
	defer srv.Close()

	c := testClient(t, srv)
	if err := OptionsVoteScenario(c, testLogger(t)); err != nil {
		t.Errorf("OptionsVoteScenario() error: %v", err)
	}
}

func TestOptionsVoteScenario_CreateVoteError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		writeJSON(w, map[string]string{"error": "bad request"})
	}))
	defer srv.Close()

	c := testClient(t, srv)
	err := OptionsVoteScenario(c, testLogger(t))
	if err == nil {
		t.Fatal("expected error when CreateVote fails")
	}
}

func TestOptionsVoteScenario_AddOptionError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v2", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/options":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	err := OptionsVoteScenario(c, testLogger(t))
	if err == nil {
		t.Fatal("expected error when AddOption fails")
	}
}

func TestOptionsVoteScenario_DrawError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v2", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/options":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v2":
			writeJSON(w, client.VoteDetail{ID: "v2", Options: []client.VoteOptionDto{{ID: "o1", Title: "T"}}})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/draw":
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadRequest)
			writeJSON(w, map[string]string{"error": "vote closed"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	err := OptionsVoteScenario(c, testLogger(t))
	if err == nil {
		t.Fatal("expected error when Draw fails")
	}
}

func TestOptionsVoteScenario_GetVoteError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v2", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/options":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v2":
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusNotFound)
			writeJSON(w, map[string]string{"error": "not found"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := OptionsVoteScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when GetVote fails")
	}
}

func TestOptionsVoteScenario_RemoveOptionError(t *testing.T) {
	optTitle := "Option Alpha"
	draw := client.DrawResultResponse{WinnerOptionTitle: &optTitle, Round: 1}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v2", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/options":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v2":
			writeJSON(w, client.VoteDetail{
				ID:      "v2",
				Options: []client.VoteOptionDto{{ID: "o1", Title: "Option Alpha"}},
			})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/draw":
			writeJSON(w, draw)
		case r.Method == http.MethodDelete && r.URL.Path == "/api/v1/votes/v2/options/o1":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := OptionsVoteScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when RemoveOption fails")
	}
}

func TestOptionsVoteScenario_ReopenError(t *testing.T) {
	optTitle := "Option Alpha"
	draw := client.DrawResultResponse{WinnerOptionTitle: &optTitle, Round: 1}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v2", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/options":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v2":
			writeJSON(w, client.VoteDetail{
				ID:      "v2",
				Options: []client.VoteOptionDto{{ID: "o1", Title: "Option Alpha"}},
			})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/draw":
			writeJSON(w, draw)
		case r.Method == http.MethodDelete && r.URL.Path == "/api/v1/votes/v2/options/o1":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/reopen":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := OptionsVoteScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when Reopen fails")
	}
}

func TestOptionsVoteScenario_DeleteError(t *testing.T) {
	optTitle := "Option Alpha"
	draw := client.DrawResultResponse{WinnerOptionTitle: &optTitle, Round: 1}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "v2", Title: "T", Mode: "SIMPLE"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/options":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/v2":
			writeJSON(w, client.VoteDetail{
				ID:      "v2",
				Options: []client.VoteOptionDto{{ID: "o1", Title: "Option Alpha"}},
			})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/draw":
			writeJSON(w, draw)
		case r.Method == http.MethodDelete && r.URL.Path == "/api/v1/votes/v2/options/o1":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/v2/reopen":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodDelete && r.URL.Path == "/api/v1/votes/v2":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := OptionsVoteScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when DeleteVote fails")
	}
}
