package scenario

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/juncevich/fate/simulation/internal/client"
)

// newFairRotationServer returns a server that:
//   - serves draws for vote "vf"
//   - returns NewRoundStarted=true on the 3rd draw (simulating 3-participant cycle)
func newFairRotationServer(t *testing.T) *httptest.Server {
	t.Helper()
	var drawCount atomic.Int32
	emails := []string{"a@x.com", "b@x.com", "c@x.com"}
	history := []client.DrawHistoryDto{
		{ID: "h1", WinnerEmail: &emails[0], Round: 1, DrawnAt: time.Now()},
		{ID: "h2", WinnerEmail: &emails[1], Round: 1, DrawnAt: time.Now()},
		{ID: "h3", WinnerEmail: &emails[2], Round: 1, DrawnAt: time.Now()},
		{ID: "h4", WinnerEmail: &emails[0], Round: 1, DrawnAt: time.Now()},
	}

	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "vf", Title: "Fair Vote", Mode: "FAIR_ROTATION", Status: "OPEN"})

		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/draw":
			n := drawCount.Add(1)
			winnerEmail := emails[(n-1)%3]
			newRound := n == 3 // round ends on 3rd draw
			writeJSON(w, client.DrawResultResponse{
				WinnerEmail:     &winnerEmail,
				Round:           1,
				NewRoundStarted: newRound,
			})

		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/reopen":
			w.WriteHeader(http.StatusNoContent)

		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/participants":
			w.WriteHeader(http.StatusNoContent)

		case r.Method == http.MethodDelete && strings.HasPrefix(r.URL.Path, "/api/v1/votes/vf/participants/"):
			w.WriteHeader(http.StatusNoContent)

		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/vf/history":
			writeJSON(w, history)

		case r.Method == http.MethodDelete && r.URL.Path == "/api/v1/votes/vf":
			w.WriteHeader(http.StatusNoContent)

		default:
			t.Logf("unexpected request: %s %s", r.Method, r.URL.Path)
			w.WriteHeader(http.StatusNotFound)
		}
	}))
}

func TestFairRotationScenario_HappyPath(t *testing.T) {
	srv := newFairRotationServer(t)
	defer srv.Close()

	c := testClient(t, srv)
	if err := FairRotationScenario(c, testLogger(t)); err != nil {
		t.Errorf("FairRotationScenario() error: %v", err)
	}
}

func TestFairRotationScenario_CreateVoteError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusInternalServerError)
		writeJSON(w, map[string]string{"error": "server error"})
	}))
	defer srv.Close()

	c := testClient(t, srv)
	err := FairRotationScenario(c, testLogger(t))
	if err == nil {
		t.Fatal("expected error when CreateVote fails")
	}
}

func TestFairRotationScenario_DrawError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "vf", Title: "T", Mode: "FAIR_ROTATION"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/draw":
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadRequest)
			writeJSON(w, map[string]string{"error": "no eligible participants"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	err := FairRotationScenario(c, testLogger(t))
	if err == nil {
		t.Fatal("expected error when Draw fails")
	}
}

func TestFairRotationScenario_ReopenError(t *testing.T) {
	var drawCount atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "vf", Title: "T", Mode: "FAIR_ROTATION"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/draw":
			drawCount.Add(1)
			email := "a@x.com"
			writeJSON(w, client.DrawResultResponse{WinnerEmail: &email, Round: 1, NewRoundStarted: false})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/reopen":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	err := FairRotationScenario(c, testLogger(t))
	if err == nil {
		t.Fatal("expected error when Reopen fails")
	}
}

func TestFairRotationScenario_AddParticipantError(t *testing.T) {
	var drawCount atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "vf", Title: "T", Mode: "FAIR_ROTATION"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/draw":
			n := drawCount.Add(1)
			email := "a@x.com"
			writeJSON(w, client.DrawResultResponse{
				WinnerEmail:     &email,
				Round:           1,
				NewRoundStarted: n == 3,
			})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/reopen":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/participants":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	err := FairRotationScenario(c, testLogger(t))
	if err == nil {
		t.Fatal("expected error when AddParticipant fails")
	}
}

func TestFairRotationScenario_ReopenBeforeExtraDrawError(t *testing.T) {
	var drawCount atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "vf", Title: "T", Mode: "FAIR_ROTATION"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/draw":
			n := drawCount.Add(1)
			email := "a@x.com"
			writeJSON(w, client.DrawResultResponse{
				WinnerEmail:     &email,
				Round:           1,
				NewRoundStarted: n == 3,
			})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/reopen":
			// first two reopens succeed (between round draws), third fails (before extra draw)
			if drawCount.Load() < 3 {
				w.WriteHeader(http.StatusNoContent)
			} else {
				w.WriteHeader(http.StatusInternalServerError)
			}
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/participants":
			w.WriteHeader(http.StatusNoContent)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := FairRotationScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when Reopen before extra draw fails")
	}
}

func TestFairRotationScenario_RemoveParticipantError(t *testing.T) {
	var drawCount atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "vf", Title: "T", Mode: "FAIR_ROTATION"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/draw":
			n := drawCount.Add(1)
			email := "a@x.com"
			writeJSON(w, client.DrawResultResponse{
				WinnerEmail:     &email,
				Round:           1,
				NewRoundStarted: n == 3,
			})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/reopen":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/participants":
			w.WriteHeader(http.StatusNoContent)
		case strings.HasPrefix(r.URL.Path, "/api/v1/votes/vf/participants/") && r.Method == http.MethodDelete:
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := FairRotationScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when RemoveParticipant fails")
	}
}

func TestFairRotationScenario_GetHistoryError(t *testing.T) {
	var drawCount atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "vf", Title: "T", Mode: "FAIR_ROTATION"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/draw":
			n := drawCount.Add(1)
			email := "a@x.com"
			writeJSON(w, client.DrawResultResponse{
				WinnerEmail:     &email,
				Round:           1,
				NewRoundStarted: n == 3,
			})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/reopen":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/participants":
			w.WriteHeader(http.StatusNoContent)
		case strings.HasPrefix(r.URL.Path, "/api/v1/votes/vf/participants/") && r.Method == http.MethodDelete:
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/vf/history":
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusInternalServerError)
			writeJSON(w, map[string]string{"error": "db error"})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := FairRotationScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when GetHistory fails")
	}
}

func TestFairRotationScenario_DeleteError(t *testing.T) {
	var drawCount atomic.Int32
	emails := []string{"a@x.com", "b@x.com", "c@x.com"}
	history := []client.DrawHistoryDto{
		{ID: "h1", WinnerEmail: &emails[0], Round: 1, DrawnAt: time.Now()},
	}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes":
			writeJSON(w, client.VoteDetail{ID: "vf", Title: "T", Mode: "FAIR_ROTATION"})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/draw":
			n := drawCount.Add(1)
			winner := emails[(n-1)%3]
			writeJSON(w, client.DrawResultResponse{
				WinnerEmail:     &winner,
				Round:           1,
				NewRoundStarted: n == 3,
			})
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/reopen":
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/votes/vf/participants":
			w.WriteHeader(http.StatusNoContent)
		case strings.HasPrefix(r.URL.Path, "/api/v1/votes/vf/participants/") && r.Method == http.MethodDelete:
			w.WriteHeader(http.StatusNoContent)
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/votes/vf/history":
			writeJSON(w, history)
		case r.Method == http.MethodDelete && r.URL.Path == "/api/v1/votes/vf":
			w.WriteHeader(http.StatusInternalServerError)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	c := testClient(t, srv)
	if err := FairRotationScenario(c, testLogger(t)); err == nil {
		t.Fatal("expected error when DeleteVote fails")
	}
}
