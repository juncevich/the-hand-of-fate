package client_test

import (
	"io"
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
		"Register": func() error {
			_, err := c.Register(client.RegisterRequest{Email: "a@b.c"})
			return err
		},
		"Login": func() error {
			_, err := c.Login(client.LoginRequest{Email: "a@b.c"})
			return err
		},
		"Refresh": func() error {
			_, err := c.Refresh("rt")
			return err
		},
		"Logout": func() error { return c.Logout("rt") },
		"CreateVote": func() error {
			_, err := c.CreateVote(client.CreateVoteRequest{Title: "t"})
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

// Without a refresh token the request must carry no body at all (the backend
// then falls back to the httpOnly cookie), not a JSON "null".
func TestRefreshAndLogout_EmptyTokenSendsNoBody(t *testing.T) {
	type captured struct{ body, contentType string }
	var got []captured
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		got = append(got, captured{string(b), r.Header.Get("Content-Type")})
		if r.URL.Path == "/api/v1/auth/logout" {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"accessToken":"at"}`))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if _, err := c.Refresh(""); err != nil {
		t.Fatalf("Refresh() error: %v", err)
	}
	if err := c.Logout(""); err != nil {
		t.Fatalf("Logout() error: %v", err)
	}

	for i, req := range got {
		if req.body != "" || req.contentType != "" {
			t.Errorf("request %d: body = %q, Content-Type = %q, want neither", i, req.body, req.contentType)
		}
	}
}

func TestRefresh_WithTokenSendsJSONBody(t *testing.T) {
	var body string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		body = string(b)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"accessToken":"at"}`))
	}))
	defer srv.Close()

	c, _ := client.New(srv.URL, newLogger(t))
	if _, err := c.Refresh("rt-1"); err != nil {
		t.Fatalf("Refresh() error: %v", err)
	}
	if body != `{"refreshToken":"rt-1"}` {
		t.Errorf("body = %q", body)
	}
}
