package client_test

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/juncevich/fate/simulation/internal/client"
)

// TestMethods_TransportError checks every endpoint wrapper surfaces a
// connection failure instead of trying to decode a missing response.
func TestMethods_TransportError(t *testing.T) {
	srv := httptest.NewServer(http.NotFoundHandler())
	url := srv.URL
	srv.Close() // nothing listens on url any more

	c, err := client.New(url, newLogger(t))
	if err != nil {
		t.Fatalf("New() error: %v", err)
	}

	calls := map[string]func() error{
		"GetProfile": func() error {
			_, err := c.GetProfile()
			return err
		},
		"LogoutAll": func() error { return c.LogoutAll() },
		"CreateVote": func() error {
			_, err := c.CreateVote(client.CreateVoteWebRequest{Title: "t"})
			return err
		},
		"ListVotes": func() error {
			_, err := c.ListVotes(0, 10)
			return err
		},
		"GetVote": func() error {
			_, err := c.GetVote("v1")
			return err
		},
		"DeleteVote":        func() error { return c.DeleteVote("v1") },
		"AddParticipant":    func() error { return c.AddParticipant("v1", "a@b.c") },
		"RemoveParticipant": func() error { return c.RemoveParticipant("v1", "a@b.c") },
		"AddOption":         func() error { return c.AddOption("v1", "Pizza") },
		"RemoveOption":      func() error { return c.RemoveOption("v1", "o1") },
		"Draw": func() error {
			_, err := c.Draw("v1")
			return err
		},
		"Reopen": func() error { return c.Reopen("v1") },
		"Close":  func() error { return c.Close("v1") },
		"GetHistory": func() error {
			_, err := c.GetHistory("v1")
			return err
		},
		"GetLinkToken": func() error {
			_, err := c.GetLinkToken()
			return err
		},
		"UnlinkTelegram": func() error { return c.UnlinkTelegram() },
	}

	for name, call := range calls {
		t.Run(name, func(t *testing.T) {
			if err := call(); err == nil {
				t.Fatalf("%s() error = nil, want connection error", name)
			}
		})
	}
}

func TestDo_InvalidURL(t *testing.T) {
	c, _ := client.New("http://bad host", newLogger(t))

	if err := c.DeleteVote("v1"); err == nil {
		t.Fatal("expected error for malformed base URL")
	}
}

func TestDecode_InvalidJSON(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte("not json"))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	_, err := c.GetVote("v1")
	if err == nil || !strings.Contains(err.Error(), "decode (status 200)") {
		t.Fatalf("error = %v, want decode error", err)
	}
}
