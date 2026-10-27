import type { components } from '@/api/generated/schema'

export type ErrorCode = components['schemas']['ErrorCode']

/**
 * What the user reads for each backend error code. Typed as a full Record over the generated
 * ErrorCode union, so a code added on the backend fails `npm run build` until it is translated here.
 */
export const ERROR_MESSAGES: Record<ErrorCode, string> = {
  VALIDATION_FAILED: 'Проверьте введённые данные',
  REQUEST_INVALID: 'Некорректный запрос',
  PAYLOAD_TOO_LARGE: 'Файл слишком большой',
  UNAUTHORIZED: 'Сессия истекла, войдите снова',
  RATE_LIMITED: 'Слишком много запросов, попробуйте немного позже',
  CONCURRENT_MODIFICATION: 'Данные изменились одновременно с вами, обновите страницу',
  ALREADY_EXISTS: 'Такая запись уже существует',
  INTERNAL_ERROR: 'Ошибка сервера, попробуйте позже',

  INVALID_EMAIL: 'Некорректный email',
  INVALID_CREDENTIALS: 'Неверный email или пароль',
  EMAIL_ALREADY_REGISTERED: 'Этот email уже зарегистрирован',
  EMAIL_NOT_VERIFIED: 'Сначала подтвердите адрес почты',
  WRONG_PASSWORD: 'Неверный пароль',
  LOGIN_THROTTLED: 'Слишком много неудачных попыток входа, попробуйте позже',
  INVALID_TOKEN: 'Ссылка недействительна или уже использована',
  TOKEN_EXPIRED: 'Срок действия ссылки истёк',
  REFRESH_TOKEN_REQUIRED: 'Сессия истекла, войдите снова',
  USER_NOT_FOUND: 'Пользователь не найден',
  EMAIL_LIMIT_REACHED: 'Достигнут суточный лимит писем. Попробуйте позже или пригласите меньше участников',
  REAUTHENTICATION_REQUIRED: 'Для подтверждения войдите ещё раз',

  AVATAR_INVALID: 'Нужна картинка PNG, JPEG или GIF до 4096×4096',
  AVATAR_NOT_FOUND: 'Аватар не найден',
  STORAGE_UNAVAILABLE: 'Загрузка файлов сейчас недоступна',

  TELEGRAM_NOT_LINKED: 'Telegram не привязан',
  TELEGRAM_ALREADY_LINKED: 'Этот Telegram уже привязан к другому аккаунту',
  TELEGRAM_PRIVATE_CHAT_ONLY: 'Привязка возможна только в личном чате с ботом',
  TELEGRAM_LINK_TOKEN_INVALID: 'Код привязки недействителен',
  TELEGRAM_LINK_TOKEN_EXPIRED: 'Срок действия кода привязки истёк, получите новый',

  VOTE_NOT_FOUND: 'Голосование не найдено',
  VOTE_ACCESS_DENIED: 'У вас нет доступа к этому голосованию',
  NOT_VOTE_CREATOR: 'Это может сделать только создатель голосования',
  VOTE_NOT_PENDING: 'Голосование уже разыграно или закрыто',
  VOTE_NOT_DRAWN: 'Переоткрыть можно только разыгранное голосование',
  VOTE_ALREADY_CLOSED: 'Голосование уже закрыто',
  TITLE_INVALID: 'Название — одна строка от 1 до 255 символов',
  DESCRIPTION_TOO_LONG: 'Описание не длиннее 2000 символов',
  TOO_MANY_PARTICIPANTS: 'Не больше 100 участников, включая вас',
  TOO_MANY_OPTIONS: 'Не больше 100 вариантов',
  PARTICIPANT_EXISTS: 'Этот участник уже добавлен',
  OPTIONS_NOT_ALLOWED: 'В голосовании по участникам не может быть вариантов',
  OPTION_ORDER_INVALID: 'Список вариантов изменился, обновите страницу',
  NO_OPTIONS: 'Добавьте хотя бы один вариант',
  NO_PARTICIPANTS: 'В голосовании нет участников',
  PAGE_INVALID: 'Некорректная страница',
  VOTE_ID_INVALID: 'Некорректный идентификатор голосования',
}

const CONSTRAINT_MESSAGES: Record<string, string> = {
  Email: 'Некорректный email',
  NotBlank: 'Обязательное поле',
  NotNull: 'Обязательное поле',
}

const SIZE_MESSAGES: Record<string, string> = {
  password: 'От 8 до 100 символов',
  newPassword: 'От 8 до 100 символов',
  displayName: 'Не длиннее 100 символов',
  title: 'Не длиннее 255 символов',
  description: 'Не длиннее 2000 символов',
}

/** Russian text for a field error, from the constraint the backend reports in `fieldErrors`. */
export function fieldErrorMessage(field: string, constraint: string): string {
  if (constraint === 'Size') return SIZE_MESSAGES[field] ?? 'Недопустимая длина'
  return CONSTRAINT_MESSAGES[constraint] ?? 'Некорректное значение'
}
