package handler

import (
	"context"
	"strings"
	"testing"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.uber.org/zap/zaptest"
	"google.golang.org/grpc"
)

type fakeTelegram struct {
	messages []tgbotapi.MessageConfig
	requests []tgbotapi.Chattable
}

func (f *fakeTelegram) GetUpdatesChan(tgbotapi.UpdateConfig) tgbotapi.UpdatesChannel {
	return make(tgbotapi.UpdatesChannel)
}

func (f *fakeTelegram) Request(c tgbotapi.Chattable) (*tgbotapi.APIResponse, error) {
	f.requests = append(f.requests, c)
	return &tgbotapi.APIResponse{Ok: true}, nil
}

func (f *fakeTelegram) Send(c tgbotapi.Chattable) (tgbotapi.Message, error) {
	if msg, ok := c.(tgbotapi.MessageConfig); ok {
		f.messages = append(f.messages, msg)
	}
	return tgbotapi.Message{}, nil
}

type fakeFateClient struct {
	createReq     *fatev1.CreateVoteRequest
	detailsReq    *fatev1.GetVoteDetailsRequest
	historyReq    *fatev1.GetVoteHistoryRequest
	lastResultReq *fatev1.GetLastDrawResultRequest
}

func (f *fakeFateClient) LinkTelegramAccount(context.Context, *fatev1.LinkTelegramAccountRequest, ...grpc.CallOption) (*fatev1.LinkTelegramAccountResponse, error) {
	return nil, nil
}

func (f *fakeFateClient) UnlinkTelegramAccount(context.Context, *fatev1.UnlinkTelegramAccountRequest, ...grpc.CallOption) (*fatev1.UnlinkTelegramAccountResponse, error) {
	return nil, nil
}

func (f *fakeFateClient) GetMyVotes(context.Context, *fatev1.GetMyVotesRequest, ...grpc.CallOption) (*fatev1.GetMyVotesResponse, error) {
	return nil, nil
}

func (f *fakeFateClient) CreateVote(_ context.Context, req *fatev1.CreateVoteRequest, _ ...grpc.CallOption) (*fatev1.CreateVoteResponse, error) {
	f.createReq = req
	return &fatev1.CreateVoteResponse{
		Success: true,
		Vote: &fatev1.GetVoteDetailsResponse{
			VoteId: "vote-1",
			Title:  req.Title,
			Mode:   req.Mode,
			Participants: []*fatev1.ParticipantInfo{
				{Email: "owner@example.com"},
				{Email: "a@example.com"},
				{Email: "b@example.com"},
			},
		},
	}, nil
}

func (f *fakeFateClient) GetVoteDetails(_ context.Context, req *fatev1.GetVoteDetailsRequest, _ ...grpc.CallOption) (*fatev1.GetVoteDetailsResponse, error) {
	f.detailsReq = req
	return &fatev1.GetVoteDetailsResponse{
		VoteId:      req.VoteId,
		Title:       "Lunch",
		Description: "Pick today's place",
		Status:      fatev1.VoteStatus_VOTE_STATUS_PENDING,
		Mode:        fatev1.VoteMode_VOTE_MODE_SIMPLE,
		Participants: []*fatev1.ParticipantInfo{
			{Email: "a@example.com", DisplayName: "Alex"},
		},
	}, nil
}

func (f *fakeFateClient) DrawVote(context.Context, *fatev1.DrawVoteRequest, ...grpc.CallOption) (*fatev1.DrawVoteResponse, error) {
	return nil, nil
}

func (f *fakeFateClient) GetLastDrawResult(_ context.Context, req *fatev1.GetLastDrawResultRequest, _ ...grpc.CallOption) (*fatev1.GetLastDrawResultResponse, error) {
	f.lastResultReq = req
	return &fatev1.GetLastDrawResultResponse{
		HasResult: true,
		Result: &fatev1.DrawResultInfo{
			WinnerEmail:       "a@example.com",
			WinnerDisplayName: "Alex",
			Round:             1,
			DrawnAt:           "2026-04-25T00:00:00Z",
		},
	}, nil
}

func (f *fakeFateClient) GetVoteHistory(_ context.Context, req *fatev1.GetVoteHistoryRequest, _ ...grpc.CallOption) (*fatev1.GetVoteHistoryResponse, error) {
	f.historyReq = req
	return &fatev1.GetVoteHistoryResponse{
		Results: []*fatev1.DrawResultInfo{
			{WinnerEmail: "a@example.com", WinnerDisplayName: "Alex", Round: 2, DrawnAt: "2026-04-25T00:00:00Z"},
			{WinnerEmail: "b@example.com", WinnerDisplayName: "Bob", Round: 1, DrawnAt: "2026-04-24T00:00:00Z"},
		},
	}, nil
}

func TestParseCreateVoteArgs(t *testing.T) {
	req, err := parseCreateVoteArgs("Lunch | A@Example.com, b@example.com a@example.com | fair")
	if err != nil {
		t.Fatalf("parseCreateVoteArgs returned error: %v", err)
	}

	if req.Title != "Lunch" {
		t.Fatalf("title = %q", req.Title)
	}
	if req.Mode != fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION {
		t.Fatalf("mode = %v", req.Mode)
	}
	if got := strings.Join(req.ParticipantEmails, ","); got != "a@example.com,b@example.com" {
		t.Fatalf("participant emails = %q", got)
	}
}

func TestRegisterCommandsSetsTelegramMenu(t *testing.T) {
	bot := &fakeTelegram{}
	h := New(bot, &fakeFateClient{}, zaptest.NewLogger(t))

	if err := h.RegisterCommands(); err != nil {
		t.Fatalf("RegisterCommands returned error: %v", err)
	}

	if len(bot.requests) != 1 {
		t.Fatalf("requests = %d, want 1", len(bot.requests))
	}
	config, ok := bot.requests[0].(tgbotapi.SetMyCommandsConfig)
	if !ok {
		t.Fatalf("request type = %T, want tgbotapi.SetMyCommandsConfig", bot.requests[0])
	}

	want := []string{"start", "link", "newvote", "votes", "vote", "draw", "result", "history", "unlink", "help"}
	if len(config.Commands) != len(want) {
		t.Fatalf("commands = %d, want %d: %#v", len(config.Commands), len(want), config.Commands)
	}
	for i, command := range config.Commands {
		if command.Command != want[i] {
			t.Fatalf("command[%d] = %q, want %q", i, command.Command, want[i])
		}
		if command.Description == "" {
			t.Fatalf("command[%d] has empty description", i)
		}
	}
}

func TestHandleNewVoteCreatesVoteThroughBackend(t *testing.T) {
	bot := &fakeTelegram{}
	client := &fakeFateClient{}
	h := New(bot, client, zaptest.NewLogger(t))
	msg := &tgbotapi.Message{Chat: &tgbotapi.Chat{ID: 42}}

	h.handleNewVote(context.Background(), msg, "Lunch | a@example.com,b@example.com | fair")

	if client.createReq == nil {
		t.Fatal("CreateVote was not called")
	}
	if client.createReq.TelegramId != 42 {
		t.Fatalf("telegram id = %d", client.createReq.TelegramId)
	}
	if client.createReq.Mode != fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION {
		t.Fatalf("mode = %v", client.createReq.Mode)
	}
	if len(bot.messages) != 1 || !strings.Contains(bot.messages[0].Text, "Голосование создано") {
		t.Fatalf("unexpected sent messages: %#v", bot.messages)
	}
}

func TestHandleVoteInfoGetsDetailsFromBackend(t *testing.T) {
	bot := &fakeTelegram{}
	client := &fakeFateClient{}
	h := New(bot, client, zaptest.NewLogger(t))
	msg := &tgbotapi.Message{Chat: &tgbotapi.Chat{ID: 42}}

	h.handleVoteInfo(context.Background(), msg, "vote-1")

	if client.detailsReq == nil || client.detailsReq.TelegramId != 42 || client.detailsReq.VoteId != "vote-1" {
		t.Fatalf("unexpected details request: %#v", client.detailsReq)
	}
	if len(bot.messages) != 1 || !strings.Contains(bot.messages[0].Text, "Lunch") {
		t.Fatalf("unexpected sent messages: %#v", bot.messages)
	}
}

func TestHandleHistoryGetsHistoryFromBackend(t *testing.T) {
	bot := &fakeTelegram{}
	client := &fakeFateClient{}
	h := New(bot, client, zaptest.NewLogger(t))
	msg := &tgbotapi.Message{Chat: &tgbotapi.Chat{ID: 42}}

	h.handleHistory(context.Background(), msg, "vote-1")

	if client.historyReq == nil || client.historyReq.TelegramId != 42 || client.historyReq.VoteId != "vote-1" {
		t.Fatalf("unexpected history request: %#v", client.historyReq)
	}
	if len(bot.messages) != 1 || !strings.Contains(bot.messages[0].Text, "Раунд *2*") {
		t.Fatalf("unexpected sent messages: %#v", bot.messages)
	}
}

func TestHandleResultSendsTelegramIDToBackend(t *testing.T) {
	bot := &fakeTelegram{}
	client := &fakeFateClient{}
	h := New(bot, client, zaptest.NewLogger(t))
	msg := &tgbotapi.Message{Chat: &tgbotapi.Chat{ID: 42}}

	h.handleResult(context.Background(), msg, "vote-1")

	if client.lastResultReq == nil || client.lastResultReq.TelegramId != 42 || client.lastResultReq.VoteId != "vote-1" {
		t.Fatalf("unexpected result request: %#v", client.lastResultReq)
	}
}
