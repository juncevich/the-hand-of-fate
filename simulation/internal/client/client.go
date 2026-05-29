package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/cookiejar"
	"strings"
	"time"

	"go.uber.org/zap"
)

type Client struct {
	base        string
	http        *http.Client
	accessToken string
	log         *zap.Logger
}

func New(base string, log *zap.Logger) (*Client, error) {
	jar, err := cookiejar.New(nil)
	if err != nil {
		return nil, err
	}
	return &Client{
		base: strings.TrimRight(base, "/"),
		http: &http.Client{
			Jar:     jar,
			Timeout: 10 * time.Second,
		},
		log: log,
	}, nil
}

func (c *Client) SetAccessToken(token string) {
	c.accessToken = token
}

func (c *Client) do(method, path string, body any) (*http.Response, error) {
	var r io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("marshal: %w", err)
		}
		r = bytes.NewReader(b)
	}

	req, err := http.NewRequest(method, c.base+path, r)
	if err != nil {
		return nil, err
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if c.accessToken != "" {
		req.Header.Set("Authorization", "Bearer "+c.accessToken)
	}

	c.log.Debug("request", zap.String("method", method), zap.String("path", path))
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	c.log.Debug("response", zap.String("path", path), zap.Int("status", resp.StatusCode))
	return resp, nil
}

func decode[T any](resp *http.Response) (T, error) {
	defer resp.Body.Close()
	var v T
	if err := json.NewDecoder(resp.Body).Decode(&v); err != nil {
		return v, fmt.Errorf("decode (status %d): %w", resp.StatusCode, err)
	}
	if resp.StatusCode >= 400 {
		return v, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	return v, nil
}

func expectStatus(resp *http.Response, want int) error {
	defer resp.Body.Close()
	if resp.StatusCode != want {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("expected %d, got %d: %s", want, resp.StatusCode, body)
	}
	return nil
}

// extractRefreshToken reads the Set-Cookie header for fate_refresh_token.
func extractRefreshToken(resp *http.Response) string {
	for _, c := range resp.Cookies() {
		if c.Name == "fate_refresh_token" {
			return c.Value
		}
	}
	return ""
}

// ── Auth ─────────────────────────────────────────────────────────────────────

func (c *Client) Register(req RegisterRequest) (AuthResponse, error) {
	resp, err := c.do("POST", "/api/v1/auth/register", req)
	if err != nil {
		return AuthResponse{}, err
	}
	auth, err := decode[AuthResponse](resp)
	if err != nil {
		return AuthResponse{}, err
	}
	auth.RefreshToken = extractRefreshToken(resp)
	return auth, nil
}

func (c *Client) Login(req LoginRequest) (AuthResponse, error) {
	resp, err := c.do("POST", "/api/v1/auth/login", req)
	if err != nil {
		return AuthResponse{}, err
	}
	auth, err := decode[AuthResponse](resp)
	if err != nil {
		return AuthResponse{}, err
	}
	auth.RefreshToken = extractRefreshToken(resp)
	return auth, nil
}

func (c *Client) Refresh(refreshToken string) (AuthResponse, error) {
	var body *RefreshRequest
	if refreshToken != "" {
		body = &RefreshRequest{RefreshToken: refreshToken}
	}
	resp, err := c.do("POST", "/api/v1/auth/refresh", body)
	if err != nil {
		return AuthResponse{}, err
	}
	auth, err := decode[AuthResponse](resp)
	if err != nil {
		return AuthResponse{}, err
	}
	auth.RefreshToken = extractRefreshToken(resp)
	return auth, nil
}

func (c *Client) Logout(refreshToken string) error {
	var body *RefreshRequest
	if refreshToken != "" {
		body = &RefreshRequest{RefreshToken: refreshToken}
	}
	resp, err := c.do("POST", "/api/v1/auth/logout", body)
	if err != nil {
		return err
	}
	return expectStatus(resp, http.StatusNoContent)
}

// ── Votes ─────────────────────────────────────────────────────────────────────

func (c *Client) CreateVote(req CreateVoteRequest) (VoteDetail, error) {
	resp, err := c.do("POST", "/api/v1/votes", req)
	if err != nil {
		return VoteDetail{}, err
	}
	return decode[VoteDetail](resp)
}

func (c *Client) ListVotes(page, size int) (Page[VoteSummary], error) {
	path := fmt.Sprintf("/api/v1/votes?page=%d&size=%d&sort=createdAt,desc", page, size)
	resp, err := c.do("GET", path, nil)
	if err != nil {
		return Page[VoteSummary]{}, err
	}
	return decode[Page[VoteSummary]](resp)
}

func (c *Client) GetVote(id string) (VoteDetail, error) {
	resp, err := c.do("GET", "/api/v1/votes/"+id, nil)
	if err != nil {
		return VoteDetail{}, err
	}
	return decode[VoteDetail](resp)
}

func (c *Client) DeleteVote(id string) error {
	resp, err := c.do("DELETE", "/api/v1/votes/"+id, nil)
	if err != nil {
		return err
	}
	return expectStatus(resp, http.StatusNoContent)
}

func (c *Client) AddParticipant(voteID, email string) error {
	resp, err := c.do("POST", "/api/v1/votes/"+voteID+"/participants", AddParticipantRequest{Email: email})
	if err != nil {
		return err
	}
	return expectStatus(resp, http.StatusNoContent)
}

func (c *Client) RemoveParticipant(voteID, email string) error {
	resp, err := c.do("DELETE", "/api/v1/votes/"+voteID+"/participants/"+email, nil)
	if err != nil {
		return err
	}
	return expectStatus(resp, http.StatusNoContent)
}

func (c *Client) AddOption(voteID, title string) error {
	resp, err := c.do("POST", "/api/v1/votes/"+voteID+"/options", AddOptionRequest{Title: title})
	if err != nil {
		return err
	}
	return expectStatus(resp, http.StatusNoContent)
}

func (c *Client) RemoveOption(voteID, optionID string) error {
	resp, err := c.do("DELETE", "/api/v1/votes/"+voteID+"/options/"+optionID, nil)
	if err != nil {
		return err
	}
	return expectStatus(resp, http.StatusNoContent)
}

func (c *Client) Draw(voteID string) (DrawResultResponse, error) {
	resp, err := c.do("POST", "/api/v1/votes/"+voteID+"/draw", nil)
	if err != nil {
		return DrawResultResponse{}, err
	}
	return decode[DrawResultResponse](resp)
}

func (c *Client) Reopen(voteID string) error {
	resp, err := c.do("POST", "/api/v1/votes/"+voteID+"/reopen", nil)
	if err != nil {
		return err
	}
	return expectStatus(resp, http.StatusNoContent)
}

func (c *Client) Close(voteID string) error {
	resp, err := c.do("POST", "/api/v1/votes/"+voteID+"/close", nil)
	if err != nil {
		return err
	}
	return expectStatus(resp, http.StatusNoContent)
}

func (c *Client) GetHistory(voteID string) ([]DrawHistoryDto, error) {
	resp, err := c.do("GET", "/api/v1/votes/"+voteID+"/history", nil)
	if err != nil {
		return nil, err
	}
	return decode[[]DrawHistoryDto](resp)
}

// ── Telegram ──────────────────────────────────────────────────────────────────

func (c *Client) GetLinkToken() (LinkTokenResponse, error) {
	resp, err := c.do("GET", "/api/v1/telegram/link-token", nil)
	if err != nil {
		return LinkTokenResponse{}, err
	}
	return decode[LinkTokenResponse](resp)
}

func (c *Client) UnlinkTelegram() error {
	resp, err := c.do("DELETE", "/api/v1/telegram/unlink", nil)
	if err != nil {
		return err
	}
	return expectStatus(resp, http.StatusNoContent)
}
