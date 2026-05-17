package com.juncevich.fate.auth.internal.web

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.TelegramLinkService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant

data class LinkTokenResponse(
    val token: String,
    val expiresAt: Instant,
)

@RestController
@RequestMapping("/api/v1/telegram")
class TelegramController(
    private val telegramLinkService: TelegramLinkService,
) {
    @GetMapping("/link-token")
    fun getLinkToken(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): LinkTokenResponse {
        val result = telegramLinkService.generateLinkToken(user.id)
        return LinkTokenResponse(token = result.token, expiresAt = result.expiresAt)
    }

    @DeleteMapping("/unlink")
    fun unlink(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<Void> {
        telegramLinkService.unlinkByUserId(user.id)
        return ResponseEntity.noContent().build()
    }
}
