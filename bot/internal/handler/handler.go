package handler

import (
	"context"
	"errors"
	"fmt"
	"html"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
	"unicode/utf16"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.uber.org/zap"
	"google.golang.org/genproto/googleapis/rpc/errdetails"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// Messages are sent with ParseMode HTML: every value that comes from users (titles, names,
// options, descriptions, backend messages) must go through esc(), static markup must not.
const helpText = `✦ <b>The Hand of Fate Bot</b>

Доступные команды:

/start — Приветствие
/link &lt;токен&gt; — Привязать Telegram к аккаунту
/newvote &lt;название&gt; | &lt;email1,email2&gt; | &lt;simple|fair&gt; | &lt;вариант1,вариант2&gt; — Создать голосование
/votes [страница] — Посмотреть свои голосования
/vote &lt;id&gt; — Информация о голосовании
/draw &lt;id&gt; — Выполнить жеребьёвку
/result &lt;id&gt; — Последний результат голосования
/history &lt;id&gt; [страница] — История результатов
/unlink — Отвязать аккаунт
/help — Эта справка

Бот работает только в личных сообщениях.`

const privateOnlyText = "🔒 Я работаю только в личных сообщениях — напишите мне напрямую."

const groupLinkText = "🔒 Привязка возможна только в личных сообщениях.\n\n" +
	"⚠️ Токен был отправлен в общий чат и мог быть скопирован. Сгенерируйте новый в настройках приложения " +
	"и отправьте его мне в личку."

// votesPageSize keeps a /votes page far below Telegram's message limit.
const votesPageSize = 10

type TelegramAPI interface {
	GetUpdatesChan(config tgbotapi.UpdateConfig) tgbotapi.UpdatesChannel
	Request(c tgbotapi.Chattable) (*tgbotapi.APIResponse, error)
	Send(c tgbotapi.Chattable) (tgbotapi.Message, error)
}

type Handler struct {
	bot    TelegramAPI
	client fatev1.FateServiceClient
	log    *zap.Logger
	sem    chan struct{}
	wg     sync.WaitGroup
}

// maxConcurrentMessages bounds how many Telegram messages are handled at once,
// so a burst/flood of updates can't spawn unbounded goroutines and gRPC calls.
const maxConcurrentMessages = 20

func New(bot TelegramAPI, client fatev1.FateServiceClient, log *zap.Logger) *Handler {
	return &Handler{bot: bot, client: client, log: log, sem: make(chan struct{}, maxConcurrentMessages)}
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
			h.wg.Wait()
			return
		case update := <-updates:
			if update.Message == nil {
				continue
			}
			msg := update.Message
			select {
			case h.sem <- struct{}{}:
				h.wg.Go(func() {
					defer func() { <-h.sem }()
					h.handleMessage(ctx, msg)
				})
			case <-ctx.Done():
				h.log.Warn("dropping message received during shutdown", zap.Int64("chat_id", msg.Chat.ID))
				h.wg.Wait()
				return
			}
		}
	}
}

func (h *Handler) handleMessage(ctx context.Context, msg *tgbotapi.Message) {
	cmd := msg.Command()
	args := strings.TrimSpace(msg.CommandArguments())

	h.log.Info("message received",
		zap.Int64("chat_id", msg.Chat.ID),
		zap.String("chat_type", chatType(msg)),
		zap.String("command", cmd),
	)

	// Plain text (no command) is ignored below, so it gets no span or metrics either.
	var tel *commandTelemetry
	if cmd != "" {
		ctx, tel = startCommand(ctx, cmd)
	}
	defer func() {
		r := recover()
		if r != nil {
			h.log.Error("panic in handler", zap.Any("recover", r))
		}
		if tel != nil {
			tel.finish(ctx, r != nil)
		}
	}()

	if cmd != "" && !isPrivate(msg) {
		h.rejectNonPrivate(msg, cmd)
		return
	}

	switch cmd {
	case "start", "help":
		h.send(msg.Chat.ID, helpText, true)

	case "link":
		h.handleLink(ctx, msg, args)

	case "votes":
		h.handleVotes(ctx, msg, args)

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

// isPrivate reports whether msg comes from a one-to-one chat with a known sender. Commands act
// on behalf of msg.From: in a group the chat ID is shared by every member, so it must never be
// used as the account identity, and a link token posted there is visible to everyone.
func isPrivate(msg *tgbotapi.Message) bool {
	return msg.From != nil && msg.Chat != nil && msg.Chat.IsPrivate()
}

func chatType(msg *tgbotapi.Message) string {
	if msg.Chat == nil {
		return ""
	}
	return msg.Chat.Type
}

func (h *Handler) rejectNonPrivate(msg *tgbotapi.Message, cmd string) {
	if msg.Chat == nil {
		return
	}
	if cmd != "link" {
		h.send(msg.Chat.ID, privateOnlyText, false)
		return
	}
	// Best effort: works only where the bot may delete messages (group admin).
	if _, err := h.bot.Request(tgbotapi.NewDeleteMessage(msg.Chat.ID, msg.MessageID)); err != nil {
		h.log.Warn("could not delete link token posted in a group", zap.Int64("chat_id", msg.Chat.ID), zap.Error(err))
	}
	h.send(msg.Chat.ID, groupLinkText, false)
}

func (h *Handler) handleLink(ctx context.Context, msg *tgbotapi.Message, token string) {
	if token == "" {
		h.send(msg.Chat.ID,
			"Укажите токен: <code>/link ВАШ_ТОКЕН</code>\n\nПолучите токен в настройках приложения → Telegram бот",
			true)
		return
	}

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.LinkTelegramAccount(gctx, &fatev1.LinkTelegramAccountRequest{
		LinkToken:    token,
		TelegramId:   msg.From.ID,
		TelegramName: msg.From.UserName,
	})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}

	if resp.Success {
		h.send(msg.Chat.ID, fmt.Sprintf("✅ Аккаунт привязан!\n\nДобро пожаловать, <b>%s</b>!", esc(resp.DisplayName)), true)
	} else {
		h.send(msg.Chat.ID, fmt.Sprintf("❌ %s", resp.Message), false)
	}
}

var voteStatusEmoji = map[fatev1.VoteStatus]string{
	fatev1.VoteStatus_VOTE_STATUS_PENDING: "🔵",
	fatev1.VoteStatus_VOTE_STATUS_DRAWN:   "✅",
	fatev1.VoteStatus_VOTE_STATUS_CLOSED:  "🔒",
}

func (h *Handler) handleVotes(ctx context.Context, msg *tgbotapi.Message, args string) {
	page := 1
	if args != "" {
		var ok bool
		if page, ok = parsePage(args); !ok {
			h.send(msg.Chat.ID, "Используйте /votes [страница]", false)
			return
		}
	}

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.GetMyVotes(gctx, &fatev1.GetMyVotesRequest{TelegramId: msg.From.ID})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}

	if len(resp.Votes) == 0 {
		h.send(msg.Chat.ID, "У вас пока нет голосований.\n\nСоздайте первое на сайте!", false)
		return
	}

	// The backend returns every vote at once; paging here keeps each reply readable and
	// well under Telegram's per-message limit.
	totalPages := (len(resp.Votes) + votesPageSize - 1) / votesPageSize
	if page > totalPages {
		h.send(msg.Chat.ID, fmt.Sprintf("Страницы %d нет, всего страниц: %d", page, totalPages), false)
		return
	}
	from := (page - 1) * votesPageSize
	to := min(from+votesPageSize, len(resp.Votes))

	var sb strings.Builder
	sb.WriteString("✦ <b>Ваши голосования:</b>\n\n")
	for _, v := range resp.Votes[from:to] {
		statusEmoji := voteStatusEmoji[v.Status]
		modeEmoji := "⚡"
		if v.Mode == fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION {
			modeEmoji = "🔄"
		}
		creatorTag := ""
		if v.IsCreator {
			creatorTag = " 👑"
		}
		sb.WriteString(fmt.Sprintf("%s %s <b>%s</b>%s\n", statusEmoji, modeEmoji, esc(v.Title), creatorTag))
		sb.WriteString(fmt.Sprintf("  ID: <code>%s</code>\n", esc(v.VoteId)))
		sb.WriteString(fmt.Sprintf("  Участников: %d", v.ParticipantCount))
		if v.Mode == fatev1.VoteMode_VOTE_MODE_FAIR_ROTATION {
			sb.WriteString(fmt.Sprintf(" | Раунд %d", v.CurrentRound))
		}
		sb.WriteString("\n\n")
	}
	if totalPages > 1 {
		sb.WriteString(fmt.Sprintf("Страница %d из %d\n", page, totalPages))
		if page < totalPages {
			sb.WriteString(fmt.Sprintf("Следующая: <code>/votes %d</code>", page+1))
		}
	}

	h.send(msg.Chat.ID, sb.String(), true)
}

// parsePage accepts a 1-based page number that fits the proto's int32 page field.
func parsePage(raw string) (int, bool) {
	page, err := strconv.Atoi(raw)
	if err != nil || page < 1 || page > 2147483647 {
		return 0, false
	}
	return page, true
}

func (h *Handler) handleNewVote(ctx context.Context, msg *tgbotapi.Message, args string) {
	request, err := parseCreateVoteArgs(args)
	if err != nil {
		h.send(msg.Chat.ID, fmt.Sprintf("❌ %s\n\n%s", esc(err.Error()), newVoteUsage()), true)
		return
	}

	request.TelegramId = msg.From.ID

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
	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("✅ <b>Голосование создано</b>\n\n<b>%s</b>\nID: <code>%s</code>\nУчастников: %d\nРежим: %s",
		esc(vote.Title),
		esc(vote.VoteId),
		len(vote.Participants),
		formatMode(vote.Mode),
	))
	if len(vote.Options) > 0 {
		sb.WriteString(fmt.Sprintf("\nВариантов: %d", len(vote.Options)))
	}
	h.send(msg.Chat.ID, sb.String(), true)
}

func (h *Handler) handleVoteInfo(ctx context.Context, msg *tgbotapi.Message, voteID string) {
	if voteID == "" {
		h.send(msg.Chat.ID, "Укажите ID голосования: <code>/vote &lt;id&gt;</code>\n\nПосмотреть ID: /votes", true)
		return
	}

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.GetVoteDetails(gctx, &fatev1.GetVoteDetailsRequest{
		VoteId:     voteID,
		TelegramId: msg.From.ID,
	})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}

	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("✦ <b>%s</b>\n", esc(resp.Title)))
	sb.WriteString(fmt.Sprintf("ID: <code>%s</code>\n", esc(resp.VoteId)))
	sb.WriteString(fmt.Sprintf("Статус: %s\n", formatStatus(resp.Status)))
	sb.WriteString(fmt.Sprintf("Режим: %s\n", formatMode(resp.Mode)))
	if resp.Description != "" {
		// Not wrapped in <i>: a multi-line description would span lines, and send() may split there.
		sb.WriteString(fmt.Sprintf("\n%s\n", esc(resp.Description)))
	}

	if len(resp.Options) > 0 {
		sb.WriteString(fmt.Sprintf("\nВарианты (%d):\n", len(resp.Options)))
		for _, o := range resp.Options {
			sb.WriteString(fmt.Sprintf("• %s\n", esc(o.Title)))
		}
	}

	sb.WriteString(fmt.Sprintf("\nУчастники (%d):\n", len(resp.Participants)))
	for _, p := range resp.Participants {
		name := p.DisplayName
		if name == "" {
			name = p.Email
		}
		sb.WriteString(fmt.Sprintf("• %s (<code>%s</code>)\n", esc(name), esc(p.Email)))
	}
	if resp.LastResult != nil {
		label := drawResultLabel(resp.LastResult)
		sb.WriteString(fmt.Sprintf("\nПоследний победитель: <b>%s</b>, раунд %d", esc(label), resp.LastResult.Round))
	}

	h.send(msg.Chat.ID, sb.String(), true)
}

func (h *Handler) handleDraw(ctx context.Context, msg *tgbotapi.Message, voteID string) {
	if voteID == "" {
		h.send(msg.Chat.ID, "Укажите ID голосования: <code>/draw &lt;id&gt;</code>\n\nПосмотреть ID: /votes", true)
		return
	}

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.DrawVote(gctx, &fatev1.DrawVoteRequest{
		VoteId:     voteID,
		TelegramId: msg.From.ID,
	})
	if err != nil {
		h.send(msg.Chat.ID, h.grpcErrMsg(err), false)
		return
	}

	if !resp.Success {
		h.send(msg.Chat.ID, fmt.Sprintf("❌ %s", resp.Message), false)
		return
	}

	text := fmt.Sprintf("✨ <b>Рука Судьбы выбрала!</b>\n\n🏆 <b>Победитель раунда %d:</b>\n%s",
		resp.Round, esc(resp.Message))
	if resp.NewRoundStarted {
		text += "\n\n🔄 Все варианты/участники выиграли — начинается новый раунд!"
	}
	h.send(msg.Chat.ID, text, true)
}

func (h *Handler) handleResult(ctx context.Context, msg *tgbotapi.Message, voteID string) {
	if voteID == "" {
		h.send(msg.Chat.ID, "Укажите ID голосования: <code>/result &lt;id&gt;</code>", true)
		return
	}

	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.GetLastDrawResult(gctx, &fatev1.GetLastDrawResultRequest{
		VoteId:     voteID,
		TelegramId: msg.From.ID,
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
	label := drawResultLabel(r)
	text := fmt.Sprintf("✦ <b>Последний результат</b>\n\n🏆 Победитель раунда <b>%d</b>:\n<b>%s</b>\n\n<i>%s</i>",
		r.Round, esc(label), esc(r.DrawnAt))
	if r.WinnerEmail != "" && r.WinnerOptionTitle == "" {
		text = fmt.Sprintf("✦ <b>Последний результат</b>\n\n🏆 Победитель раунда <b>%d</b>:\n<b>%s</b>\n<code>%s</code>\n\n<i>%s</i>",
			r.Round, esc(label), esc(r.WinnerEmail), esc(r.DrawnAt))
	}
	h.send(msg.Chat.ID, text, true)
}

func (h *Handler) handleHistory(ctx context.Context, msg *tgbotapi.Message, voteID string) {
	if voteID == "" {
		h.send(msg.Chat.ID, "Укажите ID голосования: <code>/history &lt;id&gt; [страница]</code>", true)
		return
	}

	args := strings.Fields(voteID)
	page := 1
	if len(args) == 0 || len(args) > 2 {
		h.send(msg.Chat.ID, "Используйте /history <id> [страница]", false)
		return
	}
	if len(args) == 2 {
		var ok bool
		if page, ok = parsePage(args[1]); !ok {
			h.send(msg.Chat.ID, "Номер страницы должен быть положительным числом", false)
			return
		}
	}
	voteID = args[0]
	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.GetVoteHistory(gctx, &fatev1.GetVoteHistoryRequest{
		VoteId:     voteID,
		Page:       int32(page - 1),
		PageSize:   20,
		TelegramId: msg.From.ID,
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
	sb.WriteString("✦ <b>История результатов</b>\n\n")
	for _, r := range resp.Results {
		label := esc(drawResultLabel(r))
		if r.WinnerEmail != "" && r.WinnerOptionTitle == "" {
			sb.WriteString(fmt.Sprintf("Раунд <b>%d</b>: %s (<code>%s</code>)\n<i>%s</i>\n\n",
				r.Round, label, esc(r.WinnerEmail), esc(r.DrawnAt)))
		} else {
			sb.WriteString(fmt.Sprintf("Раунд <b>%d</b>: %s\n<i>%s</i>\n\n", r.Round, label, esc(r.DrawnAt)))
		}
	}
	if resp.TotalPages > 1 {
		sb.WriteString(fmt.Sprintf("Страница %d из %d\n", page, resp.TotalPages))
		if int32(page) < resp.TotalPages {
			sb.WriteString(fmt.Sprintf("Следующая: <code>/history %s %d</code>", esc(voteID), page+1))
		}
	}
	h.send(msg.Chat.ID, sb.String(), true)
}

func (h *Handler) handleUnlink(ctx context.Context, msg *tgbotapi.Message) {
	gctx, cancel := grpcCtx(ctx)
	defer cancel()

	resp, err := h.client.UnlinkTelegramAccount(gctx, &fatev1.UnlinkTelegramAccountRequest{
		TelegramId: msg.From.ID,
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

// maxMessageLength is Telegram's limit on a message's text, in UTF-16 code units.
const maxMessageLength = 4096

// send delivers text, split into several messages when it exceeds Telegram's limit. With
// formatted set it is parsed as HTML; should Telegram still reject the markup, the same text
// is resent without formatting so the user always gets a reply.
func (h *Handler) send(chatID int64, text string, formatted bool) {
	for _, chunk := range splitMessage(text, maxMessageLength) {
		h.sendChunk(chatID, chunk, formatted)
	}
}

func (h *Handler) sendChunk(chatID int64, text string, formatted bool) {
	msg := tgbotapi.NewMessage(chatID, text)
	if formatted {
		msg.ParseMode = tgbotapi.ModeHTML
	}
	_, err := h.bot.Send(msg)
	var apiErr *tgbotapi.Error
	if err != nil && formatted && errors.As(err, &apiErr) && apiErr.Code == 400 {
		h.log.Warn("Telegram rejected formatted message, resending as plain text",
			zap.Int64("chat_id", chatID), zap.Error(err))
		_, err = h.bot.Send(tgbotapi.NewMessage(chatID, plainText(text)))
	}
	if err != nil {
		h.log.Error("failed to send message", zap.Int64("chat_id", chatID), zap.Error(err))
	}
}

// esc escapes a user-supplied value for Telegram's HTML parse mode.
func esc(s string) string {
	return html.EscapeString(s)
}

var htmlTag = regexp.MustCompile(`<[^>]*>`)

// plainText turns the bot's HTML markup back into the text a user would read.
func plainText(s string) string {
	return html.UnescapeString(htmlTag.ReplaceAllString(s, ""))
}

func utf16Len(s string) int {
	return len(utf16.Encode([]rune(s)))
}

// splitMessage cuts text into chunks of at most limit UTF-16 units. It breaks between lines,
// where the bot's markup is always balanced, and only hard-splits a line that alone is too long.
func splitMessage(text string, limit int) []string {
	if utf16Len(text) <= limit {
		return []string{text}
	}
	var chunks []string
	var cur strings.Builder
	curLen := 0
	flush := func() {
		if chunk := strings.TrimRight(cur.String(), "\n"); chunk != "" {
			chunks = append(chunks, chunk)
		}
		cur.Reset()
		curLen = 0
	}
	for _, line := range strings.SplitAfter(text, "\n") {
		lineLen := utf16Len(line)
		if curLen+lineLen > limit {
			flush()
		}
		for lineLen > limit {
			head, tail := cutUTF16(line, limit)
			chunks = append(chunks, head)
			line, lineLen = tail, utf16Len(tail)
		}
		cur.WriteString(line)
		curLen += lineLen
	}
	flush()
	return chunks
}

// cutUTF16 splits s after at most limit UTF-16 units without breaking a rune.
func cutUTF16(s string, limit int) (string, string) {
	n := 0
	for i, r := range s {
		w := utf16.RuneLen(r)
		if n+w > limit {
			return s[:i], s[i:]
		}
		n += w
	}
	return s, ""
}

func (h *Handler) grpcErrMsg(err error) string {
	st, ok := status.FromError(err)
	if !ok {
		h.log.Error("non-gRPC error", zap.Error(err))
		return "❌ Ошибка сервера. Попробуйте позже."
	}
	switch st.Code() {
	case codes.NotFound:
		if isTelegramNotLinked(st) {
			return "❌ Telegram аккаунт не привязан.\n\nПолучите токен в настройках приложения и выполните /link <токен> в личных сообщениях"
		}
		return "❌ Голосование не найдено."
	case codes.InvalidArgument:
		return "❌ Некорректные данные команды."
	case codes.PermissionDenied:
		return "❌ У вас нет прав для этого действия."
	case codes.FailedPrecondition:
		return "❌ Действие недоступно в текущем состоянии голосования."
	case codes.ResourceExhausted:
		return "❌ Достигнут лимит. Попробуйте позже."
	default:
		h.log.Error("gRPC error", zap.Error(err))
		return "❌ Ошибка сервера. Попробуйте позже."
	}
}

// Backend error contract (see proto/fate/v1/fate.proto): an unlinked Telegram account is
// NOT_FOUND plus an ErrorInfo detail, which distinguishes it from a missing vote.
const (
	errorDomain             = "fate.v1"
	reasonTelegramNotLinked = "TELEGRAM_NOT_LINKED"
)

func isTelegramNotLinked(st *status.Status) bool {
	for _, d := range st.Details() {
		if info, ok := d.(*errdetails.ErrorInfo); ok &&
			info.GetDomain() == errorDomain && info.GetReason() == reasonTelegramNotLinked {
			return true
		}
	}
	return false
}

// Emails can't contain spaces, so commas and whitespace both separate them;
// option titles can ("Pizza Hut"), so only commas separate options.
var (
	emailSeparator  = regexp.MustCompile(`[,\s]+`)
	optionSeparator = regexp.MustCompile(`,`)
)

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

	var options []string
	if len(parts) >= 4 {
		options = parseItems(parts[3])
	}

	return &fatev1.CreateVoteRequest{
		Title:             title,
		Mode:              mode,
		ParticipantEmails: parseEmails(participantPart(parts)),
		Options:           options,
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

func parseItems(raw string) []string {
	fields := optionSeparator.Split(raw, -1)
	items := make([]string, 0, len(fields))
	seen := make(map[string]struct{}, len(fields))
	for _, field := range fields {
		item := strings.TrimSpace(field)
		if item == "" {
			continue
		}
		if _, ok := seen[item]; ok {
			continue
		}
		seen[item] = struct{}{}
		items = append(items, item)
	}
	return items
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
	return "Формат: <code>/newvote Название | email1,email2 | simple | вариант1,вариант2</code>\n" +
		"Режимы: <code>simple</code> или <code>fair</code>.\nВарианты — необязательно."
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

func drawResultLabel(r *fatev1.DrawResultInfo) string {
	if r.WinnerOptionTitle != "" {
		return r.WinnerOptionTitle
	}
	if r.WinnerDisplayName != "" {
		return r.WinnerDisplayName
	}
	return r.WinnerEmail
}
