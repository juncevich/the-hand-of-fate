package com.juncevich.fate.account.web

import com.juncevich.fate.account.AuthenticatedUser
import com.juncevich.fate.account.TelegramLinkService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant

data class LinkTokenResponse(
    val token: String,
    val expiresAt: Instant,
)

@RestController
@RequestMapping("/api/v1/telegram")
@Tag(name = "Telegram", description = "Linking the account to the Telegram bot")
class TelegramController(
    private val telegramLinkService: TelegramLinkService,
) {
    @GetMapping("/link-token")
    @Operation(operationId = "getTelegramLinkToken", summary = "Issue a short-lived token for the bot's /link command")
    fun getLinkToken(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): LinkTokenResponse {
        val result = telegramLinkService.generateLinkToken(user.id)
        return LinkTokenResponse(token = result.token, expiresAt = result.expiresAt)
    }

    @DeleteMapping("/unlink")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "unlinkTelegram", summary = "Unlink the Telegram account")
    fun unlink(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) {
        telegramLinkService.unlinkByUserId(user.id)
    }
}
