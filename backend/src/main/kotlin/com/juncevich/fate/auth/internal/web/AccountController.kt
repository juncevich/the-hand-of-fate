package com.juncevich.fate.auth.internal.web

import com.juncevich.fate.auth.internal.service.AccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Mailed-link flows that need no session. Each answers the same whether or not the account exists. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AccountController(
    private val accountService: AccountService,
) {
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        operationId = "resendVerification",
        summary = "Mail a new verification link",
        description = "Sent only to a registered, unconfirmed address, at most once a minute."
    )
    @SecurityRequirements
    @ApiResponse(responseCode = "202", description = "Accepted")
    fun resendVerification(
        @Valid @RequestBody request: EmailRequest,
    ) {
        accountService.resendVerification(request.email)
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        operationId = "forgotPassword",
        summary = "Mail a password reset link",
        description = "Sent only to a registered address, at most once a minute; the link is valid for 30 minutes."
    )
    @SecurityRequirements
    @ApiResponse(responseCode = "202", description = "Accepted")
    fun forgotPassword(
        @Valid @RequestBody request: EmailRequest,
    ) {
        accountService.requestPasswordReset(request.email)
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "resetPassword",
        summary = "Set a new password with the mailed token",
        description = "Ends every session. 400 with code INVALID_TOKEN or TOKEN_EXPIRED for an unusable link."
    )
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Password changed")
    fun resetPassword(
        @Valid @RequestBody request: ResetPasswordRequest,
    ) {
        accountService.resetPassword(request.token, request.newPassword)
    }
}
