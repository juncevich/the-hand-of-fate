package handler

import (
	"context"
	"fmt"
	"regexp"
	"strings"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.uber.org/zap"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const helpText = `✦ *The Hand of Fate Bot*

Доступные команды:

/start — Приветствие
/link <токен> — Привязать Telegram к аккаунту
/newvote <название> | <email1,email2> | <simple|fair> — Создать голосование
/votes — Посмотреть свои голосования
/vote <id> — Информация о голосовании
/draw <id> — Выполнить жеребьёвку
/result <id> — Последний результат голосования
/history <id> — История результатов
/unlink — Отвязать аккаунт
/help — Эта справка`

type TelegramAPI interface {
	GetUpdatesChan(config tgbotapi.UpdateConfig) tgbotapi.UpdatesChannel
	Request(c tgbotapi.Chattable) (*tgbotapi.APIResponse, error)
	Send(c tgbotapi.Chattable) (tgbotapi.Message, error)
}

type Handler struct {
	bot    TelegramAPI
	client fatev1.FateServiceClient
	log    *zap.Logger
}

func New(bot TelegramAPI, client fatev1.FateServiceClient, log *zap.Logger) *Handler {
	return &Handler{bot: bot, client: client, log: log}
}

const grpcTimeout = 5 * time.Second

func grpcCtx(parent context.Context) (context.Context, context.CancelFunc) {
	return context.WithTimeout(parent, grpcTimeout)
}

func (h *Handler) RegisterCommands() error {
	_, err := h.bot.Request(tgbotapi.NewSetMyCommands(botCommands()...))
	if err != nil {
		h.log.Error("failed to register Telegram commands", zap.Error(err))
	}
	return err
}

func botCommands() []tgbotapi.BotCommand {
	return []tgbotapi.BotCommand{
		{Command: "start", Description: "Приветствие"},
		{Command: "link", Description: "Привязать Telegram к аккаунту"},
		{Command: "newvote", Description: "Создать голосование"},
		{Command: "votes", Description: "Посмотреть свои голосования"},
		{Command: "vote", Description: "Информация о голосовании"},
		{Command: "draw", Description: "Выполнить жеребьевку"},
		{Command: "result", Description: "Последний результат голосования"},
		{Command: "history", Description: "История результатов"},
		{Command: "unlink", Description: "Отвязать аккаунт"},
		{Command: "help", Description: "Справка по командам"},
	}
}

func (h *Handler) Run(ctx context.Context) {
	u := tgbotapi.NewUpdate(0)
	u.Timeout = 60
	updates := h.bot.GetUpdatesChan(u)

	for {
		select {
		case <-ctx.Done():
			return
		case update := <-updates:
			if update.Message == nil {
				continue
			}
			go h.handleMessage(ctx, update.Message)
		}
	}
}

func (h *Handler) handleMessage(ctx context.Context, msg *tgbotapi.Message) {
	defer func() {
		if r := recover(); r != nil {
			h.log.Error("panic in handler", zap.Any("recover", r))
		}
	}()

	h.log.Info("message received",
		zap.Int64("chat_id", msg.Chat.ID),
		zap.String("text", msg.Text),
	)

	cmd := msg.Command()
	args := strings.TrimSpace(msg.CommandArguments())

	switch cmd {
	case "start", "help":
		h.send(msg.Chat.ID, helpText, true)

	case "link":
		h.handleLink(ctx, msg, args)

	case "votes":
		h.handleVotes(ctx, msg)

	case "newvote":
		h.handleNewVote(ctx, msg, args)

	case "vote":
		h.handleVoteInfo(ctx, msg, args)

	case "draw":
		h.handleDraw(ctx, msg, args)

	case "result":
		h.handleResult(ctx, msg, args)

	case "history":
		h.handleHistory(ctx, msg, args)

	case "unlink":
		h.handleUnlink(ctx, msg)

	default:
		if cmd != "" {
			h.send(msg.Chat.ID, "Неизвестная команда. Используйте /help", false)
		}
	}
}

func (h *Handler) handleLink(ctx context.Context, msg *tgbotapi.Message, token string) {
	if token == "" {
		h.send(msg.Chat.ID,
			"Укажите токен: `/link ВАШ_ТОКЕН`\n\nПолучите токен в настройках приложения → Telegram бот",
			true)
		return
	}

	var telegramName string
	if msg.From != nil {
		telegramName = msg.From.UserName
	}

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.LinkTelegramAccount(gctx, &fatev1.LinkTelegramAccountRequest{
		LinkToken:    token,
		TelegramId:   msg.Chat.ID,
		TelegramName: telegramName,
	})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}

	if resp.Success {
		h.send(msg.Chat.ID, fmt.Sprintf("✅ Аккаунт привязан!\n\nДобро пожаловать, *%s*!", resp.DisplayName), true)
	} else {
		h.send(msg.Chat.ID, fmt.Sprintf("❌ %s", resp.Message), false)
	}
}

func (h *Handler) handleVotes(ctx context.Context, msg *tgbotapi.Message) {
	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.GetMyVotes(gctx, &fatev1.GetMyVotesRequest{TelegramId: msg.Chat.ID})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}

	if len(resp.Votes) == 0 {
		h.send(msg.Chat.ID, "У вас пока нет голосований.\n\nСоздайте первое на сайте!", false)
		return
	}

	var sb strings.Builder
	sb.WriteString("✦ *Ваши голосования:*\n\n")
	for _, v := range resp.Votes {
		statusEmoji := map[fatev1.VoteStatus]string{
			fatev1.VoteStatus_VOTE_STATUS_PENDING: "🔵",
			fatev1.VoteStatus_VOTE_STATUS_DRAWN:   "✅",
			fatev1.VoteStatus_VOTE_STATUS_CLOSED:  "🔒",
		}[v.Status]
		modeEmoji := "⚡"
		if v.Mode == fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION {
			modeEmoji = "🔄"
		}
		creatorTag := ""
		if v.IsCreator {
			creatorTag = " 👑"
		}
		sb.WriteString(fmt.Sprintf("%s %s *%s*%s\n", statusEmoji, modeEmoji, v.Title, creatorTag))
		sb.WriteString(fmt.Sprintf("  ID: `%s`\n", v.VoteId))
		sb.WriteString(fmt.Sprintf("  Участников: %d", v.ParticipantCount))
		if v.Mode == fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION {
			sb.WriteString(fmt.Sprintf(" | Раунд %d", v.CurrentRound))
		}
		sb.WriteString("\n\n")
	}

	h.send(msg.Chat.ID, sb.String(), true)
}

func (h *Handler) handleNewVote(ctx context.Context, msg *tgbotapi.Message, args string) {
	request, err := parseCreateVoteArgs(args)
	if err != nil {
		h.send(msg.Chat.ID, fmt.Sprintf("❌ %s\n\n%s", err.Error(), newVoteUsage()), true)
		return
	}

	request.TelegramId = msg.Chat.ID

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.CreateVote(gctx, request)
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}
	if !resp.Success {
		h.send(msg.Chat.ID, fmt.Sprintf("❌ %s", resp.Message), false)
		return
	}

	vote := resp.Vote
	h.send(msg.Chat.ID, fmt.Sprintf(
		"✅ *Голосование создано*\n\n*%s*\nID: `%s`\nУчастников: %d\nРежим: %s",
		vote.Title,
		vote.VoteId,
		len(vote.Participants),
		formatMode(vote.Mode),
	), true)
}

func (h *Handler) handleVoteInfo(ctx context.Context, msg *tgbotapi.Message, voteID string) {
	if voteID == "" {
		h.send(msg.Chat.ID, "Укажите ID голосования: `/vote <id>`\n\nПосмотреть ID: /votes", true)
		return
	}

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.GetVoteDetails(gctx, &fatev1.GetVoteDetailsRequest{
		VoteId:     voteID,
		TelegramId: msg.Chat.ID,
	})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}

	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("✦ *%s*\n", resp.Title))
	sb.WriteString(fmt.Sprintf("ID: `%s`\n", resp.VoteId))
	sb.WriteString(fmt.Sprintf("Статус: %s\n", formatStatus(resp.Status)))
	sb.WriteString(fmt.Sprintf("Режим: %s\n", formatMode(resp.Mode)))
	if resp.Description != "" {
		sb.WriteString(fmt.Sprintf("\n_%s_\n", resp.Description))
	}
	sb.WriteString(fmt.Sprintf("\nУчастники (%d):\n", len(resp.Participants)))
	for _, p := range resp.Participants {
		name := p.DisplayName
		if name == "" {
			name = p.Email
		}
		sb.WriteString(fmt.Sprintf("• %s (`%s`)\n", name, p.Email))
	}
	if resp.LastResult != nil {
		name := resp.LastResult.WinnerDisplayName
		if name == "" {
			name = resp.LastResult.WinnerEmail
		}
		sb.WriteString(fmt.Sprintf("\nПоследний победитель: *%s*, раунд %d", name, resp.LastResult.Round))
	}

	h.send(msg.Chat.ID, sb.String(), true)
}

func (h *Handler) handleDraw(ctx context.Context, msg *tgbotapi.Message, voteID string) {
	if voteID == "" {
		h.send(msg.Chat.ID, "Укажите ID голосования: `/draw <id>`\n\nПосмотреть ID: /votes", true)
		return
	}

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.DrawVote(gctx, &fatev1.DrawVoteRequest{
		VoteId:     voteID,
		TelegramId: msg.Chat.ID,
	})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}

	if !resp.Success {
		h.send(msg.Chat.ID, fmt.Sprintf("❌ %s", resp.Message), false)
		return
	}

	text := fmt.Sprintf("✨ *Рука Судьбы выбрала!*\n\n🏆 *Победитель раунда %d:*\n%s",
		resp.Round, resp.Message)
	if resp.NewRoundStarted {
		text += "\n\n🔄 Все участники победили — начинается новый раунд!"
	}
	h.send(msg.Chat.ID, text, true)
}

func (h *Handler) handleResult(ctx context.Context, msg *tgbotapi.Message, voteID string) {
	if voteID == "" {
		h.send(msg.Chat.ID, "Укажите ID голосования: `/result <id>`", true)
		return
	}

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.GetLastDrawResult(gctx, &fatev1.GetLastDrawResultRequest{
		VoteId:     voteID,
		TelegramId: msg.Chat.ID,
	})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}

	if !resp.HasResult {
		h.send(msg.Chat.ID, "Для этого голосования ещё не было жеребьёвки.", false)
		return
	}

	r := resp.Result
	name := r.WinnerDisplayName
	if name == "" {
		name = r.WinnerEmail
	}
	h.send(msg.Chat.ID,
		fmt.Sprintf("✦ *Последний результат*\n\n🏆 Победитель раунда *%d*:\n*%s*\n`%s`\n\n_%s_",
			r.Round, name, r.WinnerEmail, r.DrawnAt),
		true)
}

func (h *Handler) handleHistory(ctx context.Context, msg *tgbotapi.Message, voteID string) {
	if voteID == "" {
		h.send(msg.Chat.ID, "Укажите ID голосования: `/history <id>`", true)
		return
	}

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.GetVoteHistory(gctx, &fatev1.GetVoteHistoryRequest{
		VoteId:     voteID,
		TelegramId: msg.Chat.ID,
	})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}
	if len(resp.Results) == 0 {
		h.send(msg.Chat.ID, "Для этого голосования ещё нет истории результатов.", false)
		return
	}

	var sb strings.Builder
	sb.WriteString("✦ *История результатов*\n\n")
	for _, r := range resp.Results {
		name := r.WinnerDisplayName
		if name == "" {
			name = r.WinnerEmail
		}
		sb.WriteString(fmt.Sprintf("Раунд *%d*: %s (`%s`)\n_%s_\n\n", r.Round, name, r.WinnerEmail, r.DrawnAt))
	}
	h.send(msg.Chat.ID, sb.String(), true)
}

func (h *Handler) handleUnlink(ctx context.Context, msg *tgbotapi.Message) {
	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.UnlinkTelegramAccount(gctx, &fatev1.UnlinkTelegramAccountRequest{
		TelegramId: msg.Chat.ID,
	})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}
	if resp.Success {
		h.send(msg.Chat.ID, "✅ Telegram аккаунт отвязан.", false)
	} else {
		h.send(msg.Chat.ID, fmt.Sprintf("❌ %s", resp.Message), false)
	}
}

func (h *Handler) send(chatID int64, text string, markdown bool) {
	msg := tgbotapi.NewMessage(chatID, text)
	if markdown {
		msg.ParseMode = tgbotapi.ModeMarkdown
	}
	if _, err := h.bot.Send(msg); err != nil {
		h.log.Error("failed to send message", zap.Int64("chat_id", chatID), zap.Error(err))
	}
}

func (h *Handler) grpcErrMsg(err error) string {
	st, ok := status.FromError(err)
	if !ok {
		h.log.Error("non-gRPC error", zap.Error(err))
		return "❌ Ошибка сервера. Попробуйте позже."
	}
	switch st.Code() {
	case codes.NotFound:
		return "❌ Telegram аккаунт не привязан.\n\nПолучите токен в настройках приложения и выполните /link <токен>"
	case codes.InvalidArgument:
		return "❌ Некорректные данные команды."
	case codes.PermissionDenied:
		return "❌ У вас нет прав для этого действия."
	default:
		h.log.Error("gRPC error", zap.Error(err))
		return "❌ Ошибка сервера. Попробуйте позже."
	}
}

var emailSeparator = regexp.MustCompile(`[,\s]+`)

func parseCreateVoteArgs(args string) (*fatev1.CreateVoteRequest, error) {
	parts := strings.Split(args, "|")
	title := strings.TrimSpace(parts[0])
	if title == "" {
		return nil, fmt.Errorf("укажите название голосования")
	}

	mode := fatev1.VoteMode_VOTE_MODE_SIMPLE
	if len(parts) >= 3 {
		parsedMode, err := parseMode(parts[2])
		if err != nil {
			return nil, err
		}
		mode = parsedMode
	}

	return &fatev1.CreateVoteRequest{
		Title:             title,
		Mode:              mode,
		ParticipantEmails: parseEmails(participantPart(parts)),
	}, nil
}

func participantPart(parts []string) string {
	if len(parts) < 2 {
		return ""
	}
	return parts[1]
}

func parseEmails(raw string) []string {
	fields := emailSeparator.Split(raw, -1)
	emails := make([]string, 0, len(fields))
	seen := make(map[string]struct{}, len(fields))
	for _, field := range fields {
		email := strings.ToLower(strings.TrimSpace(field))
		if email == "" {
			continue
		}
		if _, ok := seen[email]; ok {
			continue
		}
		seen[email] = struct{}{}
		emails = append(emails, email)
	}
	return emails
}

func parseMode(raw string) (fatev1.VoteMode, error) {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "", "simple", "обычный":
		return fatev1.VoteMode_VOTE_MODE_SIMPLE, nil
	case "fair", "fair_rotation", "rotation", "rotate", "честный":
		return fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION, nil
	default:
		return fatev1.VoteMode_VOTE_MODE_UNSPECIFIED, fmt.Errorf("неизвестный режим голосования: %s", strings.TrimSpace(raw))
	}
}

func newVoteUsage() string {
	return "Формат: `/newvote Название | email1@example.com,email2@example.com | simple`\nРежимы: `simple` или `fair`."
}

func formatStatus(status fatev1.VoteStatus) string {
	switch status {
	case fatev1.VoteStatus_VOTE_STATUS_PENDING:
		return "ожидает жеребьёвки"
	case fatev1.VoteStatus_VOTE_STATUS_DRAWN:
		return "завершено"
	case fatev1.VoteStatus_VOTE_STATUS_CLOSED:
		return "закрыто"
	default:
		return "неизвестно"
	}
}

func formatMode(mode fatev1.VoteMode) string {
	if mode == fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION {
		return "честная ротация"
	}
	return "обычный"
}
