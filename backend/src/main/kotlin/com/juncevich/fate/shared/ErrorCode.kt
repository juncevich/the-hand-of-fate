package com.juncevich.fate.shared

/**
 * Stable machine-readable reason of every client-facing error: the `code` of REST problems and
 * the `ErrorInfo.reason` / `error_code` of gRPC answers. Clients show their own wording per code
 * (the frontend's and the bot's dictionaries must cover every value), so the English `title`
 * is only a fallback. The proto `ErrorCode` enum mirrors this one (`ERROR_CODE_` + name) —
 * `ErrorMappingContractTest` keeps them in sync. Never rename or reuse a value: one that goes out of
 * use is deprecated here and in the proto, never removed.
 */
private const val LEGACY_LOGIN = "Answered only by the app's own login, replaced by Keycloak"

enum class ErrorCode {
    // ── Generic ──────────────────────────────────────────────────────────────
    VALIDATION_FAILED,
    REQUEST_INVALID,
    PAYLOAD_TOO_LARGE,
    UNAUTHORIZED,
    RATE_LIMITED,
    CONCURRENT_MODIFICATION,
    ALREADY_EXISTS,
    INTERNAL_ERROR,

    // ── Accounts ─────────────────────────────────────────────────────────────
    INVALID_EMAIL,

    @Deprecated(LEGACY_LOGIN)
    INVALID_CREDENTIALS,

    @Deprecated(LEGACY_LOGIN)
    EMAIL_ALREADY_REGISTERED,

    @Deprecated(LEGACY_LOGIN)
    EMAIL_NOT_VERIFIED,

    @Deprecated(LEGACY_LOGIN)
    WRONG_PASSWORD,

    @Deprecated(LEGACY_LOGIN)
    LOGIN_THROTTLED,
    INVALID_TOKEN,

    @Deprecated(LEGACY_LOGIN)
    TOKEN_EXPIRED,

    @Deprecated(LEGACY_LOGIN)
    REFRESH_TOKEN_REQUIRED,
    USER_NOT_FOUND,
    EMAIL_LIMIT_REACHED,

    // ── Avatars ──────────────────────────────────────────────────────────────
    AVATAR_INVALID,
    AVATAR_NOT_FOUND,
    STORAGE_UNAVAILABLE,

    // ── Telegram ─────────────────────────────────────────────────────────────
    TELEGRAM_NOT_LINKED,
    TELEGRAM_ALREADY_LINKED,
    TELEGRAM_PRIVATE_CHAT_ONLY,
    TELEGRAM_LINK_TOKEN_INVALID,
    TELEGRAM_LINK_TOKEN_EXPIRED,

    // ── Votes ────────────────────────────────────────────────────────────────
    VOTE_NOT_FOUND,
    VOTE_ACCESS_DENIED,
    NOT_VOTE_CREATOR,
    VOTE_NOT_PENDING,
    VOTE_NOT_DRAWN,
    VOTE_ALREADY_CLOSED,
    TITLE_INVALID,
    DESCRIPTION_TOO_LONG,
    TOO_MANY_PARTICIPANTS,
    TOO_MANY_OPTIONS,
    PARTICIPANT_EXISTS,
    OPTIONS_NOT_ALLOWED,
    OPTION_ORDER_INVALID,
    NO_OPTIONS,
    NO_PARTICIPANTS,
    PAGE_INVALID,
    VOTE_ID_INVALID,

    // ── Accounts (added later; the proto numbering follows declaration order) ───
    REAUTHENTICATION_REQUIRED,
}
