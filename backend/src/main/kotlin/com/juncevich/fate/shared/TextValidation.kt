package com.juncevich.fate.shared

private const val MAX_TITLE_LENGTH = 255

/**
 * A vote or option title, trimmed: 1–[MAX_TITLE_LENGTH] characters on one line. Control characters
 * (a line break pasted into the bot's `/newvote`, a tab, …) are refused: a title is shown on one line
 * everywhere — lists, email subjects, Telegram messages.
 */
fun requireValidTitle(value: String): String {
    val title = value.trim()
    if (title.isBlank() || title.length > MAX_TITLE_LENGTH || title.any(Char::isISOControl)) {
        throw BadRequestException(
            "Title must be one line of 1 to $MAX_TITLE_LENGTH characters without control characters",
            ErrorCode.TITLE_INVALID
        )
    }
    return title
}
