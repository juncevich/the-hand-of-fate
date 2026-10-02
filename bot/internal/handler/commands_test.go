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

func makeCommandMsgWithArgs(chatID int64, cmd, args string) *tgbotapi.Message {
	msg := makeCommandMsg(chatID, cmd)
	msg.Text += " " + args
	return msg
}

// runCommand dispatches a single command through handleMessage and returns the
// text of every message the bot sent back.
func runCommand(t *testing.T, client fatev1.FateServiceClient, msg *tgbotapi.Message) []string {
	t.Helper()
	bot := &fakeTelegram{}
	h := New(bot, client, zaptest.NewLogger(t))

	h.handleMessage(context.Background(), msg)

	texts := make([]string, 0, len(bot.messages))
	for _, m := range bot.messages {
		texts = append(texts, m.Text)
	}
	return texts
}

func assertSingleReply(t *testing.T, texts []string, want ...string) {
	t.Helper()
	if len(texts) != 1 {
		t.Fatalf("sent %d messages, want 1: %q", len(texts), texts)
	}
	for _, w := range want {
		if !strings.Contains(texts[0], w) {
			t.Fatalf("message = %q, want containing %q", texts[0], w)
		}
	}
}

func TestHandleMessageDispatchesEveryCommand(t *testing.T) {
	client := &fakeFateClient{}
	tests := []struct {
		cmd  string
		args string
		want string
	}{
		{"link", "tok", "Аккаунт привязан"},
		{"votes", "", "нет голосований"},
		{"newvote", "Lunch", "Голосование создано"},
		{"vote", "v1", "Lunch"},
		{"draw", "v1", "Winner"},
		{"result", "v1", "Последний результат"},
		{"history", "v1", "История результатов"},
		{"unlink", "", "отвязан"},
	}
	for _, tc := range tests {
		t.Run(tc.cmd, func(t *testing.T) {
			msg := makeCommandMsg(42, tc.cmd)
			if tc.args != "" {
				msg = makeCommandMsgWithArgs(42, tc.cmd, tc.args)
			}
			texts := runCommand(t, client, msg)
			if len(texts) == 0 || !strings.Contains(texts[0], tc.want) {
				t.Fatalf("/%s replied %q, want containing %q", tc.cmd, texts, tc.want)
			}
		})
	}
}

func TestHandleMessageRecoversFromPanic(t *testing.T) {
	bot := &fakeTelegram{}
	h := New(bot, &panickingClient{fakeFateClient: &fakeFateClient{}}, zaptest.NewLogger(t))

	// Must not propagate the panic to the caller.
	h.handleMessage(context.Background(), makeCommandMsg(42, "votes"))

	if bot.messageCount() != 0 {
		t.Fatalf("sent %d messages, want 0", bot.messageCount())
	}
}

type panickingClient struct {
	*fakeFateClient
}

func (c *panickingClient) GetMyVotes(context.Context, *fatev1.GetMyVotesRequest, ...grpc.CallOption) (*fatev1.GetMyVotesResponse, error) {
	panic("boom")
}

func TestHandleNewVoteErrors(t *testing.T) {
	tests := []struct {
		name   string
		client *fakeFateClient
		args   string
		want   []string
	}{
		{
			name:   "invalid args shows usage",
			client: &fakeFateClient{},
			args:   "Lunch | | nope",
			want:   []string{"неизвестный режим", "Формат: `/newvote"},
		},
		{
			name:   "gRPC error",
			client: &fakeFateClient{createErr: notLinkedErr()},
			args:   "Lunch",
			want:   []string{"не привязан"},
		},
		{
			name:   "backend rejects",
			client: &fakeFateClient{createResp: &fatev1.CreateVoteResponse{Success: false, Message: "Invalid email"}},
			args:   "Lunch",
			want:   []string{"❌ Invalid email"},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			texts := runCommand(t, tc.client, makeCommandMsgWithArgs(42, "newvote", tc.args))
			assertSingleReply(t, texts, tc.want...)
		})
	}
}

func TestHandleNewVoteSendsOptions(t *testing.T) {
	client := &fakeFateClient{
		createResp: &fatev1.CreateVoteResponse{
			Success: true,
			Vote: &fatev1.GetVoteDetailsResponse{
				VoteId:  "vote-1",
				Title:   "Lunch",
				Mode:    fatev1.VoteMode_VOTE_MODE_SIMPLE,
				Options: []*fatev1.VoteOptionInfo{{OptionId: "o1", Title: "Pizza"}, {OptionId: "o2", Title: "Sushi"}},
			},
		},
	}

	texts := runCommand(t, client, makeCommandMsgWithArgs(42, "newvote", "Lunch | | simple | Pizza, Sushi,Pizza,"))

	assertSingleReply(t, texts, "Голосование создано", "Вариантов: 2")
	if got := strings.Join(client.createReq.Options, ","); got != "Pizza,Sushi" {
		t.Fatalf("options = %q, want %q", got, "Pizza,Sushi")
	}
	if client.createReq.TelegramId != 42 {
		t.Fatalf("telegram id = %d, want 42", client.createReq.TelegramId)
	}
}

func TestParseCreateVoteArgsKeepsMultiWordOptions(t *testing.T) {
	req, err := parseCreateVoteArgs("Lunch | a@example.com b@example.com | simple | Pizza Hut,  Burger King , Sushi,Pizza Hut")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got := strings.Join(req.Options, "|"); got != "Pizza Hut|Burger King|Sushi" {
		t.Fatalf("options = %q, want %q", got, "Pizza Hut|Burger King|Sushi")
	}
	// Emails are still separated by whitespace as well as commas
	if got := strings.Join(req.ParticipantEmails, ","); got != "a@example.com,b@example.com" {
		t.Fatalf("participant emails = %q", got)
	}
}

func TestParseCreateVoteArgsModeAliases(t *testing.T) {
	tests := map[string]fatev1.VoteMode{
		"simple":        fatev1.VoteMode_VOTE_MODE_SIMPLE,
		"обычный":       fatev1.VoteMode_VOTE_MODE_SIMPLE,
		"  ":            fatev1.VoteMode_VOTE_MODE_SIMPLE,
		"FAIR":          fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION,
		"fair_rotation": fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION,
		"rotation":      fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION,
		"rotate":        fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION,
		"честный":       fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION,
	}
	for raw, want := range tests {
		t.Run(raw, func(t *testing.T) {
			req, err := parseCreateVoteArgs("Title | | " + raw)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if req.Mode != want {
				t.Fatalf("mode = %v, want %v", req.Mode, want)
			}
		})
	}
}

func TestHandleVoteInfo(t *testing.T) {
	tests := []struct {
		name   string
		client *fakeFateClient
		args   string
		want   []string
	}{
		{
			name:   "missing id",
			client: &fakeFateClient{},
			want:   []string{"Укажите ID голосования: `/vote <id>`"},
		},
		{
			name:   "gRPC error",
			client: &fakeFateClient{detailsErr: status.Error(codes.PermissionDenied, "x")},
			args:   "v1",
			want:   []string{"нет прав"},
		},
		{
			name: "options, email fallback and last result",
			client: &fakeFateClient{detailsResp: &fatev1.GetVoteDetailsResponse{
				VoteId:       "v1",
				Title:        "Lunch",
				Status:       fatev1.VoteStatus_VOTE_STATUS_DRAWN,
				Mode:         fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION,
				Options:      []*fatev1.VoteOptionInfo{{OptionId: "o1", Title: "Pizza"}},
				Participants: []*fatev1.ParticipantInfo{{Email: "anon@example.com"}},
				LastResult:   &fatev1.DrawResultInfo{WinnerOptionTitle: "Pizza", Round: 2},
			}},
			args: "v1",
			want: []string{
				"Варианты (1)", "• Pizza",
				"• anon@example.com (`anon@example.com`)",
				"Последний победитель: *Pizza*, раунд 2",
				"завершено", "честная ротация",
			},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			msg := makeCommandMsg(42, "vote")
			if tc.args != "" {
				msg = makeCommandMsgWithArgs(42, "vote", tc.args)
			}
			assertSingleReply(t, runCommand(t, tc.client, msg), tc.want...)
		})
	}
}

func TestHandleResult(t *testing.T) {
	tests := []struct {
		name   string
		client *fakeFateClient
		args   string
		want   []string
		reject []string
	}{
		{
			name:   "missing id",
			client: &fakeFateClient{},
			want:   []string{"Укажите ID голосования: `/result <id>`"},
		},
		{
			name:   "gRPC error",
			client: &fakeFateClient{lastResultErr: status.Error(codes.NotFound, "x")},
			args:   "v1",
			want:   []string{"Голосование не найдено"},
		},
		{
			name:   "no result yet",
			client: &fakeFateClient{lastResultResp: &fatev1.GetLastDrawResultResponse{HasResult: false}},
			args:   "v1",
			want:   []string{"ещё не было жеребьёвки"},
		},
		{
			name: "option winner omits email line",
			client: &fakeFateClient{lastResultResp: &fatev1.GetLastDrawResultResponse{
				HasResult: true,
				Result:    &fatev1.DrawResultInfo{WinnerOptionTitle: "Pizza", Round: 4, DrawnAt: "2026-04-25T00:00:00Z"},
			}},
			args:   "v1",
			want:   []string{"раунда *4*", "*Pizza*", "2026-04-25T00:00:00Z"},
			reject: []string{"`"},
		},
		{
			name: "participant without display name falls back to email",
			client: &fakeFateClient{lastResultResp: &fatev1.GetLastDrawResultResponse{
				HasResult: true,
				Result:    &fatev1.DrawResultInfo{WinnerEmail: "a@example.com", Round: 1},
			}},
			args: "v1",
			want: []string{"*a@example.com*", "`a@example.com`"},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			msg := makeCommandMsg(42, "result")
			if tc.args != "" {
				msg = makeCommandMsgWithArgs(42, "result", tc.args)
			}
			texts := runCommand(t, tc.client, msg)
			assertSingleReply(t, texts, tc.want...)
			for _, r := range tc.reject {
				if strings.Contains(texts[0], r) {
					t.Fatalf("message = %q, must not contain %q", texts[0], r)
				}
			}
		})
	}
}

func TestHandleHistory(t *testing.T) {
	tests := []struct {
		name   string
		client *fakeFateClient
		args   string
		want   []string
	}{
		{
			name:   "missing id",
			client: &fakeFateClient{},
			want:   []string{"Укажите ID голосования: `/history <id>`"},
		},
		{
			name:   "gRPC error",
			client: &fakeFateClient{historyErr: status.Error(codes.InvalidArgument, "x")},
			args:   "v1",
			want:   []string{"Некорректные"},
		},
		{
			name:   "empty history",
			client: &fakeFateClient{historyResp: &fatev1.GetVoteHistoryResponse{}},
			args:   "v1",
			want:   []string{"ещё нет истории"},
		},
		{
			name: "option winners",
			client: &fakeFateClient{historyResp: &fatev1.GetVoteHistoryResponse{Results: []*fatev1.DrawResultInfo{
				{WinnerOptionTitle: "Pizza", Round: 2, DrawnAt: "d2"},
				{WinnerOptionTitle: "Sushi", Round: 1, DrawnAt: "d1"},
			}}},
			args: "v1",
			want: []string{"Раунд *2*: Pizza\n_d2_", "Раунд *1*: Sushi\n_d1_"},
		},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			msg := makeCommandMsg(42, "history")
			if tc.args != "" {
				msg = makeCommandMsgWithArgs(42, "history", tc.args)
			}
			assertSingleReply(t, runCommand(t, tc.client, msg), tc.want...)
		})
	}
}

func TestHandleLinkBackendRejects(t *testing.T) {
	client := &fakeFateClient{linkResp: &fatev1.LinkTelegramAccountResponse{Success: false, Message: "Token expired"}}

	texts := runCommand(t, client, makeCommandMsgWithArgs(42, "link", "tok"))

	assertSingleReply(t, texts, "❌ Token expired")
}

func TestRegisterCommandsReturnsTelegramError(t *testing.T) {
	wantErr := errors.New("telegram down")
	h := New(&fakeTelegram{requestErr: wantErr}, &fakeFateClient{}, zaptest.NewLogger(t))

	if err := h.RegisterCommands(); !errors.Is(err, wantErr) {
		t.Fatalf("RegisterCommands() error = %v, want %v", err, wantErr)
	}
}

func TestSendLogsTelegramErrorWithoutPanicking(t *testing.T) {
	bot := &fakeTelegram{sendErr: errors.New("chat not found")}
	h := New(bot, &fakeFateClient{}, zaptest.NewLogger(t))

	h.send(42, "hello", true)

	if bot.messageCount() != 1 {
		t.Fatalf("send attempts = %d, want 1", bot.messageCount())
	}
	if bot.messages[0].ParseMode != tgbotapi.ModeMarkdown {
		t.Fatalf("parse mode = %q, want Markdown", bot.messages[0].ParseMode)
	}
}

func TestRunSkipsUpdatesWithoutMessage(t *testing.T) {
	updates := make(chan tgbotapi.Update, 2)
	bot := &fakeTelegram{updates: updates}
	h := New(bot, &fakeFateClient{}, zaptest.NewLogger(t))

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() {
		h.Run(ctx)
		close(done)
	}()

	updates <- tgbotapi.Update{UpdateID: 1} // e.g. a callback query — no Message
	updates <- tgbotapi.Update{UpdateID: 2, Message: makeCommandMsg(42, "help")}

	deadline := time.After(2 * time.Second)
	for bot.messageCount() == 0 {
		select {
		case <-deadline:
			t.Fatal("message update was never handled")
		case <-time.After(10 * time.Millisecond):
		}
	}
	cancel()
	<-done

	if bot.messageCount() != 1 {
		t.Fatalf("sent %d messages, want 1", bot.messageCount())
	}
}

func TestDrawResultLabelPriority(t *testing.T) {
	tests := []struct {
		name string
		in   *fatev1.DrawResultInfo
		want string
	}{
		{"option first", &fatev1.DrawResultInfo{WinnerOptionTitle: "Pizza", WinnerDisplayName: "Alex", WinnerEmail: "a@x"}, "Pizza"},
		{"display name second", &fatev1.DrawResultInfo{WinnerDisplayName: "Alex", WinnerEmail: "a@x"}, "Alex"},
		{"email last", &fatev1.DrawResultInfo{WinnerEmail: "a@x"}, "a@x"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := drawResultLabel(tc.in); got != tc.want {
				t.Fatalf("drawResultLabel() = %q, want %q", got, tc.want)
			}
		})
	}
}
