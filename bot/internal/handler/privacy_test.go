package handler

import (
	"context"
	"fmt"
	"strings"
	"testing"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.uber.org/zap/zaptest"
)

func groupCommandMsg(cmd, args string) *tgbotapi.Message {
	return &tgbotapi.Message{
		MessageID: 7,
		Chat:      &tgbotapi.Chat{ID: -100, Type: "supergroup"},
		From:      &tgbotapi.User{ID: 42},
		Text:      strings.TrimSpace("/" + cmd + " " + args),
		Entities:  []tgbotapi.MessageEntity{{Type: "bot_command", Offset: 0, Length: len(cmd) + 1}},
	}
}

func TestCommandsInGroupChatsAreRejectedWithoutCallingBackend(t *testing.T) {
	for _, cmd := range []string{"votes", "draw", "newvote", "unlink"} {
		t.Run(cmd, func(t *testing.T) {
			client := &fakeFateClient{}
			texts := runCommand(t, client, groupCommandMsg(cmd, "x"))

			assertSingleReply(t, texts, "только в личных сообщениях")
			if client.votesReq != nil || client.createReq != nil {
				t.Fatal("backend was called for a group command")
			}
		})
	}
}

func TestLinkInGroupWarnsAndTriesToDeleteTheToken(t *testing.T) {
	bot := &fakeTelegram{}
	client := &fakeFateClient{}
	h := New(bot, client, zaptest.NewLogger(t))

	h.handleMessage(context.Background(), groupCommandMsg("link", "secret-token"))

	if client.linkReq != nil {
		t.Fatal("link token posted in a group must not be consumed")
	}
	if len(bot.messages) != 1 || !strings.Contains(bot.messages[0].Text, "Сгенерируйте новый") {
		t.Fatalf("unexpected replies: %+v", bot.messages)
	}
	if len(bot.requests) != 1 {
		t.Fatalf("requests = %d, want a delete-message request", len(bot.requests))
	}
	del, ok := bot.requests[0].(tgbotapi.DeleteMessageConfig)
	if !ok || del.ChatID != -100 || del.MessageID != 7 {
		t.Fatalf("request = %#v, want delete of message 7 in chat -100", bot.requests[0])
	}
}

func TestPrivateMessageWithoutSenderIsRejected(t *testing.T) {
	msg := makeCommandMsg(42, "votes")
	msg.From = nil
	client := &fakeFateClient{}

	assertSingleReply(t, runCommand(t, client, msg), "только в личных сообщениях")
	if client.votesReq != nil {
		t.Fatal("backend was called without a sender")
	}
}

func TestBackendIdentityIsTheSenderNotTheChat(t *testing.T) {
	client := &fakeFateClient{}
	msg := makeCommandMsgWithArgs(42, "link", "tok")
	msg.From = &tgbotapi.User{ID: 7, UserName: "alex"}

	runCommand(t, client, msg)

	if client.linkReq == nil || client.linkReq.TelegramId != 7 || client.linkReq.TelegramName != "alex" {
		t.Fatalf("link request = %+v, want telegram id 7 of the sender", client.linkReq)
	}
}

func TestUserContentIsHTMLEscaped(t *testing.T) {
	client := &fakeFateClient{votesResp: &fatev1.GetMyVotesResponse{Votes: []*fatev1.VoteSummary{
		{VoteId: "v1", Title: `<b>Tea</b> & "_coffee_"`},
	}}}

	texts := runCommand(t, client, makeCommandMsg(42, "votes"))

	assertSingleReply(t, texts, "<b>&lt;b&gt;Tea&lt;/b&gt; &amp; &#34;_coffee_&#34;</b>")
}

func TestRejectedMarkupIsResentAsPlainText(t *testing.T) {
	bot := &fakeTelegram{sendErrs: []error{&tgbotapi.Error{Code: 400, Message: "can't parse entities"}}}
	h := New(bot, &fakeFateClient{}, zaptest.NewLogger(t))

	h.send(42, "<b>Tea &amp; coffee</b>", true)

	if len(bot.messages) != 2 {
		t.Fatalf("sent %d messages, want the formatted attempt and a plain retry", len(bot.messages))
	}
	retry := bot.messages[1]
	if retry.ParseMode != "" || retry.Text != "Tea & coffee" {
		t.Fatalf("retry = %q (mode %q), want plain text", retry.Text, retry.ParseMode)
	}
}

func TestNonMarkupSendErrorsAreNotRetried(t *testing.T) {
	bot := &fakeTelegram{sendErr: fmt.Errorf("network down")}
	h := New(bot, &fakeFateClient{}, zaptest.NewLogger(t))

	h.send(42, "<b>x</b>", true)

	if len(bot.messages) != 1 {
		t.Fatalf("sent %d messages, want 1", len(bot.messages))
	}
}

func TestLongMessagesAreSplitBetweenLines(t *testing.T) {
	line := "<b>" + strings.Repeat("я", 90) + "</b>\n" // 98 UTF-16 units
	text := strings.Repeat(line, 100)

	chunks := splitMessage(text, maxMessageLength)

	if len(chunks) < 2 {
		t.Fatalf("got %d chunks, want the text split", len(chunks))
	}
	var joined strings.Builder
	for _, c := range chunks {
		if utf16Len(c) > maxMessageLength {
			t.Fatalf("chunk of %d units exceeds the limit", utf16Len(c))
		}
		if strings.Count(c, "<b>") != strings.Count(c, "</b>") {
			t.Fatal("chunk splits a tag pair")
		}
		joined.WriteString(c + "\n")
	}
	if joined.String() != text {
		t.Fatal("splitting lost or reordered content")
	}
}

func TestOverlongLineIsHardSplitWithoutBreakingRunes(t *testing.T) {
	text := strings.Repeat("😀", 30) // 2 UTF-16 units each

	chunks := splitMessage(text, 25)

	if strings.Join(chunks, "") != text {
		t.Fatal("hard split lost content")
	}
	for _, c := range chunks {
		if utf16Len(c) > 25 || !strings.HasPrefix(c, "😀") {
			t.Fatalf("bad chunk %q", c)
		}
	}
}

func TestSendSplitsLongMessages(t *testing.T) {
	bot := &fakeTelegram{}
	h := New(bot, &fakeFateClient{}, zaptest.NewLogger(t))

	h.send(42, strings.Repeat("строка\n", 1000), false)

	if len(bot.messages) != 2 {
		t.Fatalf("sent %d messages, want 2", len(bot.messages))
	}
}

func manyVotes(n int) *fatev1.GetMyVotesResponse {
	votes := make([]*fatev1.VoteSummary, n)
	for i := range votes {
		votes[i] = &fatev1.VoteSummary{VoteId: fmt.Sprintf("v%d", i+1), Title: fmt.Sprintf("Vote %d", i+1)}
	}
	return &fatev1.GetMyVotesResponse{Votes: votes}
}

func TestHandleVotesPaginates(t *testing.T) {
	tests := []struct {
		args   string
		want   []string
		reject []string
	}{
		{"", []string{"Vote 1<", "Vote 10<", "Страница 1 из 3", "<code>/votes 2</code>"}, []string{"Vote 11<"}},
		{"3", []string{"Vote 21<", "Vote 25<", "Страница 3 из 3"}, []string{"Vote 20<", "Следующая"}},
		{"4", []string{"Страницы 4 нет, всего страниц: 3"}, nil},
		{"abc", []string{"Используйте /votes [страница]"}, nil},
		{"0", []string{"Используйте /votes [страница]"}, nil},
	}
	for _, tc := range tests {
		t.Run(tc.args, func(t *testing.T) {
			client := &fakeFateClient{votesResp: manyVotes(25)}
			texts := runCommand(t, client, makeCommandMsgWithArgs(42, "votes", tc.args))

			assertSingleReply(t, texts, tc.want...)
			for _, r := range tc.reject {
				if strings.Contains(texts[0], r) {
					t.Fatalf("message = %q, must not contain %q", texts[0], r)
				}
			}
		})
	}
}

func TestHandleVotesSinglePageHasNoPager(t *testing.T) {
	texts := runCommand(t, &fakeFateClient{votesResp: manyVotes(3)}, makeCommandMsg(42, "votes"))

	assertSingleReply(t, texts, "Vote 3")
	if strings.Contains(texts[0], "Страница") {
		t.Fatalf("message = %q, want no pager", texts[0])
	}
}

func TestVoteInfoShowsDrawTargetAndObservers(t *testing.T) {
	client := &fakeFateClient{detailsResp: &fatev1.GetVoteDetailsResponse{
		VoteId:       "v1",
		Title:        "Lunch",
		DrawTarget:   fatev1.DrawTarget_DRAW_TARGET_OPTIONS,
		Options:      []*fatev1.VoteOptionInfo{{OptionId: "o1", Title: "Pizza"}},
		Participants: []*fatev1.ParticipantInfo{{Email: "a@example.com"}},
	}}

	texts := runCommand(t, client, makeCommandMsgWithArgs(42, "vote", "v1"))

	assertSingleReply(t, texts, "Жеребьёвка: из вариантов", "Наблюдатели (1)")
}

func TestVoteInfoForParticipantsVote(t *testing.T) {
	client := &fakeFateClient{detailsResp: &fatev1.GetVoteDetailsResponse{
		VoteId:       "v1",
		Title:        "Lunch",
		DrawTarget:   fatev1.DrawTarget_DRAW_TARGET_PARTICIPANTS,
		Participants: []*fatev1.ParticipantInfo{{Email: "a@example.com"}},
	}}

	texts := runCommand(t, client, makeCommandMsgWithArgs(42, "vote", "v1"))

	assertSingleReply(t, texts, "Жеребьёвка: из участников", "Участники (1)")
}

func TestNewVoteReplyShowsDrawTarget(t *testing.T) {
	client := &fakeFateClient{createResp: &fatev1.CreateVoteResponse{Success: true, Vote: &fatev1.GetVoteDetailsResponse{
		VoteId:     "v1",
		Title:      "Lunch",
		DrawTarget: fatev1.DrawTarget_DRAW_TARGET_OPTIONS,
		Options:    []*fatev1.VoteOptionInfo{{Title: "Pizza"}},
	}}}

	texts := runCommand(t, client, makeCommandMsgWithArgs(42, "newvote", "Lunch | | simple | Pizza"))

	assertSingleReply(t, texts, "Жеребьёвка: из вариантов")
}
