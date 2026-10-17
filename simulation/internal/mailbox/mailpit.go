// Package mailbox reads the emails the backend sends, so scenarios can follow mailed links
// (email verification, password reset) like a user would. Locally the SMTP server is Mailpit.
package mailbox

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
)

// Mailpit queries Mailpit's HTTP API (https://mailpit.axllent.org/docs/api-v1/).
type Mailpit struct {
	base string
	http *http.Client
	// Timeout bounds the wait for an email: the backend sends them asynchronously from its outbox.
	Timeout time.Duration
	Poll    time.Duration
}

func NewMailpit(base string) *Mailpit {
	return &Mailpit{
		base:    strings.TrimRight(base, "/"),
		http:    &http.Client{Timeout: 10 * time.Second},
		Timeout: 30 * time.Second,
		Poll:    500 * time.Millisecond,
	}
}

var hrefPattern = regexp.MustCompile(`href="([^"]+)"`)

// LinkToken waits for the newest email to `to` that links to `path` (e.g. "/verify-email") and
// returns the link's token query parameter.
func (m *Mailpit) LinkToken(to, path string) (string, error) {
	deadline := time.Now().Add(m.Timeout)
	for {
		token, err := m.findToken(to, path)
		if err != nil || token != "" {
			return token, err
		}
		if time.Now().After(deadline) {
			return "", fmt.Errorf("no email to %s linking to %s within %s", to, path, m.Timeout)
		}
		time.Sleep(m.Poll)
	}
}

func (m *Mailpit) findToken(to, path string) (string, error) {
	var found struct {
		Messages []struct {
			ID string `json:"ID"`
		} `json:"messages"`
	}
	query := url.Values{"query": {fmt.Sprintf("to:%q", to)}}
	if err := m.get("/api/v1/search?"+query.Encode(), &found); err != nil {
		return "", err
	}
	// Newest first.
	for _, msg := range found.Messages {
		var body struct {
			HTML string `json:"HTML"`
		}
		if err := m.get("/api/v1/message/"+url.PathEscape(msg.ID), &body); err != nil {
			return "", err
		}
		if token := tokenFromHTML(body.HTML, path); token != "" {
			return token, nil
		}
	}
	return "", nil
}

func tokenFromHTML(html, path string) string {
	for _, match := range hrefPattern.FindAllStringSubmatch(html, -1) {
		link, err := url.Parse(match[1])
		if err != nil || link.Path != path {
			continue
		}
		if token := link.Query().Get("token"); token != "" {
			return token
		}
	}
	return ""
}

func (m *Mailpit) get(path string, out any) error {
	resp, err := m.http.Get(m.base + path)
	if err != nil {
		return fmt.Errorf("mailpit %s: %w", path, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("mailpit %s: HTTP %d", path, resp.StatusCode)
	}
	return json.NewDecoder(resp.Body).Decode(out)
}
