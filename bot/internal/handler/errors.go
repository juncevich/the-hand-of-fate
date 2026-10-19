package handler

import (
	fatev1 "github.com/juncevich/the-hand-of-fate/bot/gen/fate/v1"
	"go.uber.org/zap"
	"google.golang.org/genproto/googleapis/rpc/errdetails"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// Backend error contract (see proto/fate/v1/fate.proto): expected failures carry an ErrorCode —
// in the error_code of mutating responses, or as google.rpc.ErrorInfo{domain: "fate.v1",
// reason: <code without the ERROR_CODE_ prefix>} on status errors. The backend's message is an
// English fallback; users read the Russian text below.
const errorDomain = "fate.v1"

const telegramNotLinkedText = "Telegram аккаунт не привязан.\n\n" +
	"Получите токен в настройках приложения и выполните /link <токен> в личных сообщениях"

// errorTexts must cover every fatev1.ErrorCode (TestEveryErrorCodeHasAText).
var errorTexts = map[fatev1.ErrorCode]string{
	fatev1.ErrorCode_ERROR_CODE_VALIDATION_FAILED:       "Некорректные данные команды.",
	fatev1.ErrorCode_ERROR_CODE_REQUEST_INVALID:         "Некорректный запрос.",
	fatev1.ErrorCode_ERROR_CODE_PAYLOAD_TOO_LARGE:       "Слишком большой запрос.",
	fatev1.ErrorCode_ERROR_CODE_UNAUTHORIZED:            "Нет доступа.",
	fatev1.ErrorCode_ERROR_CODE_RATE_LIMITED:            "Слишком много запросов, попробуйте позже.",
	fatev1.ErrorCode_ERROR_CODE_CONCURRENT_MODIFICATION: "Голосование изменилось одновременно с вами, повторите команду.",
	fatev1.ErrorCode_ERROR_CODE_ALREADY_EXISTS:          "Такая запись уже существует.",
	fatev1.ErrorCode_ERROR_CODE_INTERNAL_ERROR:          "Ошибка сервера. Попробуйте позже.",

	fatev1.ErrorCode_ERROR_CODE_INVALID_EMAIL:            "Некорректный email.",
	fatev1.ErrorCode_ERROR_CODE_INVALID_CREDENTIALS:      "Неверный email или пароль.",
	fatev1.ErrorCode_ERROR_CODE_EMAIL_ALREADY_REGISTERED: "Этот email уже зарегистрирован.",
	fatev1.ErrorCode_ERROR_CODE_EMAIL_NOT_VERIFIED:       "Сначала подтвердите адрес почты.",
	fatev1.ErrorCode_ERROR_CODE_WRONG_PASSWORD:           "Неверный пароль.",
	fatev1.ErrorCode_ERROR_CODE_LOGIN_THROTTLED:          "Слишком много неудачных попыток, попробуйте позже.",
	fatev1.ErrorCode_ERROR_CODE_INVALID_TOKEN:            "Ссылка недействительна или уже использована.",
	fatev1.ErrorCode_ERROR_CODE_TOKEN_EXPIRED:            "Срок действия ссылки истёк.",
	fatev1.ErrorCode_ERROR_CODE_REFRESH_TOKEN_REQUIRED:   "Сессия истекла.",
	fatev1.ErrorCode_ERROR_CODE_USER_NOT_FOUND:           "Пользователь не найден.",
	fatev1.ErrorCode_ERROR_CODE_EMAIL_LIMIT_REACHED:      "Достигнут суточный лимит писем. Попробуйте позже или пригласите меньше участников.",

	fatev1.ErrorCode_ERROR_CODE_AVATAR_INVALID:      "Нужна картинка PNG, JPEG или GIF.",
	fatev1.ErrorCode_ERROR_CODE_AVATAR_NOT_FOUND:    "Аватар не найден.",
	fatev1.ErrorCode_ERROR_CODE_STORAGE_UNAVAILABLE: "Загрузка файлов сейчас недоступна.",

	fatev1.ErrorCode_ERROR_CODE_TELEGRAM_NOT_LINKED:         telegramNotLinkedText,
	fatev1.ErrorCode_ERROR_CODE_TELEGRAM_ALREADY_LINKED:     "Этот Telegram уже привязан к другому аккаунту.",
	fatev1.ErrorCode_ERROR_CODE_TELEGRAM_PRIVATE_CHAT_ONLY:  "Привязка возможна только в личных сообщениях с ботом.",
	fatev1.ErrorCode_ERROR_CODE_TELEGRAM_LINK_TOKEN_INVALID: "Токен привязки недействителен. Получите новый в настройках приложения.",
	fatev1.ErrorCode_ERROR_CODE_TELEGRAM_LINK_TOKEN_EXPIRED: "Срок действия токена истёк. Получите новый в настройках приложения.",

	fatev1.ErrorCode_ERROR_CODE_VOTE_NOT_FOUND:        "Голосование не найдено.",
	fatev1.ErrorCode_ERROR_CODE_VOTE_ACCESS_DENIED:    "У вас нет доступа к этому голосованию.",
	fatev1.ErrorCode_ERROR_CODE_NOT_VOTE_CREATOR:      "Это может сделать только создатель голосования.",
	fatev1.ErrorCode_ERROR_CODE_VOTE_NOT_PENDING:      "Голосование уже разыграно или закрыто.",
	fatev1.ErrorCode_ERROR_CODE_VOTE_NOT_DRAWN:        "Переоткрыть можно только разыгранное голосование.",
	fatev1.ErrorCode_ERROR_CODE_VOTE_ALREADY_CLOSED:   "Голосование уже закрыто.",
	fatev1.ErrorCode_ERROR_CODE_TITLE_INVALID:         "Название должно быть от 1 до 255 символов.",
	fatev1.ErrorCode_ERROR_CODE_DESCRIPTION_TOO_LONG:  "Описание не длиннее 2000 символов.",
	fatev1.ErrorCode_ERROR_CODE_TOO_MANY_PARTICIPANTS: "Не больше 100 участников, включая вас.",
	fatev1.ErrorCode_ERROR_CODE_TOO_MANY_OPTIONS:      "Не больше 100 вариантов.",
	fatev1.ErrorCode_ERROR_CODE_PARTICIPANT_EXISTS:    "Этот участник уже добавлен.",
	fatev1.ErrorCode_ERROR_CODE_OPTIONS_NOT_ALLOWED:   "В голосовании по участникам не может быть вариантов.",
	fatev1.ErrorCode_ERROR_CODE_OPTION_ORDER_INVALID:  "Список вариантов изменился.",
	fatev1.ErrorCode_ERROR_CODE_NO_OPTIONS:            "Добавьте хотя бы один вариант.",
	fatev1.ErrorCode_ERROR_CODE_NO_PARTICIPANTS:       "В голосовании нет участников.",
	fatev1.ErrorCode_ERROR_CODE_PAGE_INVALID:          "Некорректный номер страницы.",
	fatev1.ErrorCode_ERROR_CODE_VOTE_ID_INVALID:       "Некорректный ID голосования. Посмотреть ID: /votes",
}

// failureText is the reply to an unsuccessful mutating RPC: the translation of its code, or the
// backend's message when the code is unknown (an older bot talking to a newer backend).
func failureText(code fatev1.ErrorCode, fallback string) string {
	if text, ok := errorTexts[code]; ok {
		return "❌ " + text
	}
	return "❌ " + fallback
}

// errorCodeOf extracts the ErrorCode carried by a status error's ErrorInfo.
func errorCodeOf(st *status.Status) (fatev1.ErrorCode, bool) {
	for _, d := range st.Details() {
		if info, ok := d.(*errdetails.ErrorInfo); ok && info.GetDomain() == errorDomain {
			value, known := fatev1.ErrorCode_value["ERROR_CODE_"+info.GetReason()]
			return fatev1.ErrorCode(value), known
		}
	}
	return fatev1.ErrorCode_ERROR_CODE_UNSPECIFIED, false
}

func (h *Handler) grpcErrMsg(err error) string {
	st, ok := status.FromError(err)
	if !ok {
		h.log.Error("non-gRPC error", zap.Error(err))
		return "❌ Ошибка сервера. Попробуйте позже."
	}
	if code, known := errorCodeOf(st); known {
		if text, ok := errorTexts[code]; ok {
			return "❌ " + text
		}
	}
	// No (known) code: an unexpected failure, or a backend older than the codes.
	switch st.Code() {
	case codes.NotFound:
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
