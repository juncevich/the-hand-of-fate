package client

import "time"

type RegisterRequest struct {
	Email       string `json:"email"`
	Password    string `json:"password"`
	DisplayName string `json:"displayName"`
}

type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type RefreshRequest struct {
	RefreshToken string `json:"refreshToken,omitempty"`
}

type AuthResponse struct {
	AccessToken string `json:"accessToken"`
	UserID      string `json:"userId"`
	Email       string `json:"email"`
	DisplayName string `json:"displayName"`
	// Refresh token is extracted from Set-Cookie header
	RefreshToken string `json:"-"`
}

type CreateVoteRequest struct {
	Title             string   `json:"title"`
	Description       string   `json:"description,omitempty"`
	Mode              string   `json:"mode,omitempty"`
	ParticipantEmails []string `json:"participantEmails,omitempty"`
	Options           []string `json:"options,omitempty"`
}

type AddParticipantRequest struct {
	Email string `json:"email"`
}

type AddOptionRequest struct {
	Title string `json:"title"`
}

type VoteSummary struct {
	ID               string    `json:"id"`
	Title            string    `json:"title"`
	Mode             string    `json:"mode"`
	Status           string    `json:"status"`
	CurrentRound     int       `json:"currentRound"`
	ParticipantCount int64     `json:"participantCount"`
	IsCreator        bool      `json:"isCreator"`
	CreatedAt        time.Time `json:"createdAt"`
}

type ParticipantDto struct {
	Email       string  `json:"email"`
	DisplayName *string `json:"displayName"`
}

type VoteOptionDto struct {
	ID    string `json:"id"`
	Title string `json:"title"`
}

type DrawHistoryDto struct {
	ID                string    `json:"id"`
	WinnerEmail       *string   `json:"winnerEmail"`
	WinnerDisplayName *string   `json:"winnerDisplayName"`
	WinnerOptionTitle *string   `json:"winnerOptionTitle"`
	Round             int       `json:"round"`
	DrawnAt           time.Time `json:"drawnAt"`
}

type VoteDetail struct {
	ID           string           `json:"id"`
	Title        string           `json:"title"`
	Description  *string          `json:"description"`
	Mode         string           `json:"mode"`
	Status       string           `json:"status"`
	CurrentRound int              `json:"currentRound"`
	Participants []ParticipantDto `json:"participants"`
	Options      []VoteOptionDto  `json:"options"`
	LastResult   *DrawHistoryDto  `json:"lastResult"`
	IsCreator    bool             `json:"isCreator"`
	CreatedAt    time.Time        `json:"createdAt"`
}

type DrawResultResponse struct {
	WinnerEmail       *string `json:"winnerEmail"`
	WinnerDisplayName *string `json:"winnerDisplayName"`
	WinnerOptionTitle *string `json:"winnerOptionTitle"`
	Round             int     `json:"round"`
	NewRoundStarted   bool    `json:"newRoundStarted"`
}

type Page[T any] struct {
	Content       []T `json:"content"`
	TotalElements int `json:"totalElements"`
	TotalPages    int `json:"totalPages"`
	Number        int `json:"number"`
	Size          int `json:"size"`
}

type LinkTokenResponse struct {
	Token     string    `json:"token"`
	ExpiresAt time.Time `json:"expiresAt"`
}
