package com.juncevich.fate.auth.internal.web

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.notification.EmailPreferences
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
) {
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
