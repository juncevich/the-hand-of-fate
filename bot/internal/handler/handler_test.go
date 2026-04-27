package handler

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.uber.org/zap/zaptest"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
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

	linkResp   *fatev1.LinkTelegramAccountResponse
	linkErr    error
	votesResp  *fatev1.GetMyVotesResponse
	votesErr   error
	drawResp   *fatev1.DrawVoteResponse
	drawErr    error
	unlinkResp *fatev1.UnlinkTelegramAccountResponse
	unlinkErr  error
}

func (f *fakeFateClient) LinkTelegramAccount(_ context.Context, _ *fatev1.LinkTelegramAccountRequest, _ ...grpc.CallOption) (*fatev1.LinkTelegramAccountResponse, error) {
	if f.linkErr != nil {
		return nil, f.linkErr
	}
	if f.linkResp != nil {
		return f.linkResp, nil
	}
	return &fatev1.LinkTelegramAccountResponse{Success: true, DisplayName: "User"}, nil
}

func (f *fakeFateClient) UnlinkTelegramAccount(_ context.Context, _ *fatev1.UnlinkTelegramAccountRequest, _ ...grpc.CallOption) (*fatev1.UnlinkTelegramAccountResponse, error) {
	if f.unlinkErr != nil {
		return nil, f.unlinkErr
	}
	if f.unlinkResp != nil {
		return f.unlinkResp, nil
	}
	return &fatev1.UnlinkTelegramAccountResponse{Success: true}, nil
}

func (f *fakeFateClient) GetMyVotes(_ context.Context, _ *fatev1.GetMyVotesRequest, _ ...grpc.CallOption) (*fatev1.GetMyVotesResponse, error) {
	if f.votesErr != nil {
		return nil, f.votesErr
	}
	if f.votesResp != nil {
		return f.votesResp, nil
	}
	return &fatev1.GetMyVotesResponse{}, nil
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

func (f *fakeFateClient) DrawVote(_ context.Context, _ *fatev1.DrawVoteRequest, _ ...grpc.CallOption) (*fatev1.DrawVoteResponse, error) {
	if f.drawErr != nil {
		return nil, f.drawErr
	}
	if f.drawResp != nil {
		return f.drawResp, nil
	}
	return &fatev1.DrawVoteResponse{Success: true, Round: 1, Message: "Winner"}, nil
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

// makeCommandMsg builds a message that looks like a bot command to tgbotapi.
func makeCommandMsg(chatID int64, cmd string) *tgbotapi.Message {
	text := "/" + cmd
	return &tgbotapi.Message{
		Chat: &tgbotapi.Chat{ID: chatID},
		Text: text,
		Entities: []tgbotapi.MessageEntity{
			{Type: "bot_command", Offset: 0, Length: len(text)},
		},
	}
}

func TestGrpcErrMsg(t *testing.T) {
	h := New(&fakeTelegram{}, &fakeFateClient{}, zaptest.NewLogger(t))

	tests := []struct {
		name     string
		err      error
		contains string
	}{
		{"NOT_FOUND", status.Error(codes.NotFound, "x"), "не привязан"},
		{"PERMISSION_DENIED", status.Error(codes.PermissionDenied, "x"), "нет прав"},
		{"INVALID_ARGUMENT", status.Error(codes.InvalidArgument, "x"), "Некорректные"},
		{"generic gRPC", status.Error(codes.Internal, "x"), "Ошибка сервера"},
		{"non-gRPC error", errors.New("plain"), "Ошибка сервера"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got := h.grpcErrMsg(tc.err)
			if !strings.Contains(got, tc.contains) {
				t.Fatalf("grpcErrMsg() = %q, want containing %q", got, tc.contains)
			}
		})
	}
}

func TestHandleMessageDispatch(t *testing.T) {
	tests := []struct {
		name         string
		msg          *tgbotapi.Message
		wantContains string
		wantMsgCount int
	}{
		{"start", makeCommandMsg(42, "start"), "The Hand of Fate", 1},
		{"help", makeCommandMsg(42, "help"), "The Hand of Fate", 1},
		{"unknown command", makeCommandMsg(42, "bogus"), "Неизвестная команда", 1},
		{"non-command text", &tgbotapi.Message{Chat: &tgbotapi.Chat{ID: 42}, Text: "hello"}, "", 0},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			bot := &fakeTelegram{}
			h := New(bot, &fakeFateClient{}, zaptest.NewLogger(t))

			h.handleMessage(context.Background(), tc.msg)

			if len(bot.messages) != tc.wantMsgCount {
				t.Fatalf("message count = %d, want %d", len(bot.messages), tc.wantMsgCount)
			}
			if tc.wantContains != "" && !strings.Contains(bot.messages[0].Text, tc.wantContains) {
				t.Fatalf("message = %q, want containing %q", bot.messages[0].Text, tc.wantContains)
			}
		})
	}
}

func TestHandleLink(t *testing.T) {
	tests := []struct {
		name         string
		token        string
		from         *tgbotapi.User
		client       *fakeFateClient
		wantContains string
	}{
		{
			name:         "no token sends usage",
			token:        "",
			client:       &fakeFateClient{},
			wantContains: "токен",
		},
		{
			name:  "success",
			token: "abc123",
			from:  &tgbotapi.User{UserName: "alex"},
			client: &fakeFateClient{
				linkResp: &fatev1.LinkTelegramAccountResponse{Success: true, DisplayName: "Alex"},
			},
			wantContains: "Аккаунт привязан",
		},
		{
			name:  "gRPC error",
			token: "abc123",
			from:  &tgbotapi.User{},
			client: &fakeFateClient{
				linkErr: status.Error(codes.Internal, "x"),
			},
			wantContains: "Ошибка сервера",
		},
		{
			name:  "nil From does not panic",
			token: "abc123",
			from:  nil,
			client: &fakeFateClient{
				linkResp: &fatev1.LinkTelegramAccountResponse{Success: true, DisplayName: "X"},
			},
			wantContains: "Аккаунт привязан",
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			bot := &fakeTelegram{}
			h := New(bot, tc.client, zaptest.NewLogger(t))
			msg := &tgbotapi.Message{
				Chat: &tgbotapi.Chat{ID: 42},
				From: tc.from,
			}

			h.handleLink(context.Background(), msg, tc.token)

			if len(bot.messages) == 0 {
				t.Fatal("no message sent")
			}
			if !strings.Contains(bot.messages[0].Text, tc.wantContains) {
				t.Fatalf("message = %q, want containing %q", bot.messages[0].Text, tc.wantContains)
			}
		})
	}
}

func TestHandleVotes(t *testing.T) {
	tests := []struct {
		name         string
		client       *fakeFateClient
		wantContains string
	}{
		{
			name: "empty list",
			client: &fakeFateClient{
				votesResp: &fatev1.GetMyVotesResponse{Votes: []*fatev1.VoteSummary{}},
			},
			wantContains: "нет голосований",
		},
		{
			name: "vote listed with title and id",
			client: &fakeFateClient{
				votesResp: &fatev1.GetMyVotesResponse{
					Votes: []*fatev1.VoteSummary{
						{Title: "MyVote", VoteId: "v1", Status: fatev1.VoteStatus_VOTE_STATUS_PENDING, Mode: fatev1.VoteMode_VOTE_MODE_SIMPLE},
					},
				},
			},
			wantContains: "MyVote",
		},
		{
			name: "fair rotation shows round",
			client: &fakeFateClient{
				votesResp: &fatev1.GetMyVotesResponse{
					Votes: []*fatev1.VoteSummary{
						{Title: "T", VoteId: "v2", Status: fatev1.VoteStatus_VOTE_STATUS_PENDING, Mode: fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION, CurrentRound: 3},
					},
				},
			},
			wantContains: "Раунд 3",
		},
		{
			name: "creator shows crown",
			client: &fakeFateClient{
				votesResp: &fatev1.GetMyVotesResponse{
					Votes: []*fatev1.VoteSummary{
						{Title: "T", VoteId: "v3", Status: fatev1.VoteStatus_VOTE_STATUS_PENDING, Mode: fatev1.VoteMode_VOTE_MODE_SIMPLE, IsCreator: true},
					},
				},
			},
			wantContains: "👑",
		},
		{
			name: "gRPC error",
			client: &fakeFateClient{
				votesErr: status.Error(codes.NotFound, "x"),
			},
			wantContains: "не привязан",
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			bot := &fakeTelegram{}
			h := New(bot, tc.client, zaptest.NewLogger(t))
			msg := &tgbotapi.Message{Chat: &tgbotapi.Chat{ID: 42}}

			h.handleVotes(context.Background(), msg)

			if len(bot.messages) == 0 {
				t.Fatal("no message sent")
			}
			if !strings.Contains(bot.messages[0].Text, tc.wantContains) {
				t.Fatalf("message = %q, want containing %q", bot.messages[0].Text, tc.wantContains)
			}
		})
	}
}

func TestHandleDraw(t *testing.T) {
	tests := []struct {
		name         string
		voteID       string
		client       *fakeFateClient
		wantContains string
	}{
		{
			name:         "no voteID sends usage",
			voteID:       "",
			client:       &fakeFateClient{},
			wantContains: "Укажите ID",
		},
		{
			name:   "success shows winner and round",
			voteID: "v1",
			client: &fakeFateClient{
				drawResp: &fatev1.DrawVoteResponse{Success: true, Round: 2, Message: "Alice"},
			},
			wantContains: "Победитель раунда 2",
		},
		{
			name:   "success message contains winner name",
			voteID: "v1",
			client: &fakeFateClient{
				drawResp: &fatev1.DrawVoteResponse{Success: true, Round: 1, Message: "Alice"},
			},
			wantContains: "Alice",
		},
		{
			name:   "new round started appends message",
			voteID: "v1",
			client: &fakeFateClient{
				drawResp: &fatev1.DrawVoteResponse{Success: true, Round: 1, Message: "X", NewRoundStarted: true},
			},
			wantContains: "новый раунд",
		},
		{
			name:   "not successful sends resp message",
			voteID: "v1",
			client: &fakeFateClient{
				drawResp: &fatev1.DrawVoteResponse{Success: false, Message: "Нет участников"},
			},
			wantContains: "Нет участников",
		},
		{
			name:   "PERMISSION_DENIED",
			voteID: "v1",
			client: &fakeFateClient{drawErr: status.Error(codes.PermissionDenied, "x")},
			wantContains: "нет прав",
		},
		{
			name:         "NOT_FOUND",
			voteID:       "v1",
			client:       &fakeFateClient{drawErr: status.Error(codes.NotFound, "x")},
			wantContains: "не привязан",
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			bot := &fakeTelegram{}
			h := New(bot, tc.client, zaptest.NewLogger(t))
			msg := &tgbotapi.Message{Chat: &tgbotapi.Chat{ID: 42}}

			h.handleDraw(context.Background(), msg, tc.voteID)

			if len(bot.messages) == 0 {
				t.Fatal("no message sent")
			}
			if !strings.Contains(bot.messages[0].Text, tc.wantContains) {
				t.Fatalf("message = %q, want containing %q", bot.messages[0].Text, tc.wantContains)
			}
		})
	}
}

func TestHandleUnlink(t *testing.T) {
	tests := []struct {
		name         string
		client       *fakeFateClient
		wantContains string
	}{
		{
			name:         "success",
			client:       &fakeFateClient{unlinkResp: &fatev1.UnlinkTelegramAccountResponse{Success: true}},
			wantContains: "отвязан",
		},
		{
			name:         "not successful sends message",
			client:       &fakeFateClient{unlinkResp: &fatev1.UnlinkTelegramAccountResponse{Success: false, Message: "Уже отвязан"}},
			wantContains: "Уже отвязан",
		},
		{
			name:         "gRPC error",
			client:       &fakeFateClient{unlinkErr: status.Error(codes.Internal, "x")},
			wantContains: "Ошибка сервера",
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			bot := &fakeTelegram{}
			h := New(bot, tc.client, zaptest.NewLogger(t))
			msg := &tgbotapi.Message{Chat: &tgbotapi.Chat{ID: 42}}

			h.handleUnlink(context.Background(), msg)

			if len(bot.messages) == 0 {
				t.Fatal("no message sent")
			}
			if !strings.Contains(bot.messages[0].Text, tc.wantContains) {
				t.Fatalf("message = %q, want containing %q", bot.messages[0].Text, tc.wantContains)
			}
		})
	}
}

func TestFormatStatus(t *testing.T) {
	tests := []struct {
		status fatev1.VoteStatus
		want   string
	}{
		{fatev1.VoteStatus_VOTE_STATUS_PENDING, "ожидает жеребьёвки"},
		{fatev1.VoteStatus_VOTE_STATUS_DRAWN, "завершено"},
		{fatev1.VoteStatus_VOTE_STATUS_CLOSED, "закрыто"},
		{fatev1.VoteStatus_VOTE_STATUS_UNSPECIFIED, "неизвестно"},
	}
	for _, tc := range tests {
		t.Run(tc.status.String(), func(t *testing.T) {
			got := formatStatus(tc.status)
			if got != tc.want {
				t.Fatalf("formatStatus(%v) = %q, want %q", tc.status, got, tc.want)
			}
		})
	}
}

func TestFormatMode(t *testing.T) {
	tests := []struct {
		mode fatev1.VoteMode
		want string
	}{
		{fatev1.VoteMode_VOTE_MODE_SIMPLE, "обычный"},
		{fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION, "честная ротация"},
		{fatev1.VoteMode_VOTE_MODE_UNSPECIFIED, "обычный"},
	}
	for _, tc := range tests {
		t.Run(tc.mode.String(), func(t *testing.T) {
			got := formatMode(tc.mode)
			if got != tc.want {
				t.Fatalf("formatMode(%v) = %q, want %q", tc.mode, got, tc.want)
			}
		})
	}
}

func TestParseCreateVoteArgsErrors(t *testing.T) {
	tests := []struct {
		name     string
		args     string
		wantErr  string
		wantMode fatev1.VoteMode
	}{
		{name: "empty string", args: "", wantErr: "название"},
		{name: "spaces only", args: "   ", wantErr: "название"},
		{name: "invalid mode", args: "Title | a@b.com | badmode", wantErr: "неизвестный режим"},
		{name: "valid no mode defaults to simple", args: "Title", wantMode: fatev1.VoteMode_VOTE_MODE_SIMPLE},
		{name: "valid fair mode", args: "Title | | fair", wantMode: fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			req, err := parseCreateVoteArgs(tc.args)
			if tc.wantErr != "" {
				if err == nil {
					t.Fatalf("parseCreateVoteArgs(%q) error = nil, want containing %q", tc.args, tc.wantErr)
				}
				if !strings.Contains(err.Error(), tc.wantErr) {
					t.Fatalf("error = %q, want containing %q", err.Error(), tc.wantErr)
				}
				return
			}
			if err != nil {
				t.Fatalf("parseCreateVoteArgs(%q) unexpected error: %v", tc.args, err)
			}
			if req.Mode != tc.wantMode {
				t.Fatalf("mode = %v, want %v", req.Mode, tc.wantMode)
			}
		})
	}
}

func TestRunCancelledContextExits(t *testing.T) {
	h := New(&fakeTelegram{}, &fakeFateClient{}, zaptest.NewLogger(t))

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() {
		h.Run(ctx)
		close(done)
	}()

	cancel()

	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("Run() did not return after context cancellation")
	}
}
