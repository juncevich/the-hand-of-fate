package handler

import (
	"context"
	"fmt"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.uber.org/zap"
)

const helpText = `✦ *The Hand of Fate Bot*

Доступные команды:

/start — Приветствие
/link <токен> — Привязать Telegram к аккаунту
/votes — Посмотреть свои голосования
/draw <id> — Выполнить жеребьёвку
/result <id> — Последний результат голосования
/unlink — Отвязать аккаунт
/help — Эта справка`

type Handler struct {
	bot    *tgbotapi.BotAPI
	client fatev1.FateServiceClient
	log    *zap.Logger
}

func New(bot *tgbotapi.BotAPI, client fatev1.FateServiceClient, log *zap.Logger) *Handler {
	return &Handler{bot: bot, client: client, log: log}
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

	case "draw":
		h.handleDraw(ctx, msg, args)

	case "result":
		h.handleResult(ctx, msg, args)

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

	resp, err := h.client.LinkTelegramAccount(ctx, &fatev1.LinkTelegramAccountRequest{
		LinkToken:    token,
		TelegramId:   msg.Chat.ID,
		TelegramName: msg.From.UserName,
	})
	if err != nil {
		h.send(msg.Chat.ID, "❌ Ошибка связи с сервером. Попробуйте позже.", false)
		h.log.Error("LinkTelegramAccount error", zap.Error(err))
		return
	}

	if resp.Success {
		h.send(msg.Chat.ID, fmt.Sprintf("✅ Аккаунт привязан!\n\nДобро пожаловать, *%s*!", resp.DisplayName), true)
	} else {
		h.send(msg.Chat.ID, fmt.Sprintf("❌ %s", resp.Message), false)
	}
}

func (h *Handler) handleVotes(ctx context.Context, msg *tgbotapi.Message) {
	resp, err := h.client.GetMyVotes(ctx, &fatev1.GetMyVotesRequest{TelegramId: msg.Chat.ID})
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

func (h *Handler) handleDraw(ctx context.Context, msg *tgbotapi.Message, voteID string) {
	if voteID == "" {
		h.send(msg.Chat.ID, "Укажите ID голосования: `/draw <id>`\n\nПосмотреть ID: /votes", true)
		return
	}

	resp, err := h.client.DrawVote(ctx, &fatev1.DrawVoteRequest{
		VoteId:    voteID,
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

	resp, err := h.client.GetLastDrawResult(ctx, &fatev1.GetLastDrawResultRequest{VoteId: voteID})
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

func (h *Handler) handleUnlink(ctx context.Context, msg *tgbotapi.Message) {
	resp, err := h.client.UnlinkTelegramAccount(ctx, &fatev1.UnlinkTelegramAccountRequest{
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
	if strings.Contains(err.Error(), "NOT_FOUND") {
		return "❌ Telegram аккаунт не привязан.\n\nПолучите токен в настройках приложения и выполните /link <токен>"
	}
	if strings.Contains(err.Error(), "PERMISSION_DENIED") {
		return "❌ У вас нет прав для этого действия."
	}
	h.log.Error("gRPC error", zap.Error(err))
	return "❌ Ошибка сервера. Попробуйте позже."
}
