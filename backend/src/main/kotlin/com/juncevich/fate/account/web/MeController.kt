package com.juncevich.fate.account.web

import com.juncevich.fate.account.AuthenticatedUser
import com.juncevich.fate.account.UserQueryService
import com.juncevich.fate.account.service.avatarUrl
import com.juncevich.fate.notification.EmailPreferences
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.NotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Settings of the signed-in account. */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Users")
class MeController(
    private val emailPreferences: EmailPreferences,
    private val users: UserQueryService,
) {
    @GetMapping
    @Operation(
        operationId = "getProfile",
        summary = "The signed-in account's profile",
        description = "What the app shows of the account; read after signing in through Keycloak."
    )
    fun getProfile(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ProfileResponse {
        val account = users.findById(user.id) ?: throw NotFoundException("User not found", ErrorCode.USER_NOT_FOUND)
        return ProfileResponse(
            userId = account.id.toString(),
            email = account.email,
            displayName = account.displayName,
            avatarUrl = account.avatarUrl()
        )
    }

    @GetMapping("/email-preferences")
    @Operation(operationId = "getEmailPreferences", summary = "Whether notification emails reach the account's address")
    fun getEmailPreferences(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) = EmailPreferencesDto(emailPreferences.receivesNotifications(user.email))

    @PutMapping("/email-preferences")
    @Operation(
        operationId = "updateEmailPreferences",
        summary = "Turn notification emails on or off",
        description = "Also undoes an unsubscribe done through the link in an email."
    )
    fun updateEmailPreferences(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestBody request: EmailPreferencesDto,
    ): EmailPreferencesDto {
        emailPreferences.setReceivesNotifications(user.email, request.notificationsEnabled)
        return EmailPreferencesDto(emailPreferences.receivesNotifications(user.email))
    }
}
