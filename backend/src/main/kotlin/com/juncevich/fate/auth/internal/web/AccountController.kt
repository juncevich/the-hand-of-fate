package com.juncevich.fate.auth.internal.web

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.internal.service.AccountDeletionService
import com.juncevich.fate.auth.internal.service.SessionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** What the app does with the signed-in account itself; signing in, passwords and sessions live in Keycloak. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Sessions and deletion of the signed-in account")
class AccountController(
    private val sessionService: SessionService,
    private val accountDeletionService: AccountDeletionService,
) {
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "logoutAll",
        summary = "End every session of the current user",
        description = "Keycloak issues no new tokens to them; access tokens already issued live until they expire."
    )
    fun logoutAll(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) {
        sessionService.logoutEverywhere(user.id)
    }

    @PostMapping("/delete-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteAccount",
        summary = "Delete the account and everything it created",
        description =
            "Needs a sign-in within the last minutes (403 with code REAUTHENTICATION_REQUIRED otherwise). " +
                "Deletes the votes the user created; in other votes the user stops being a participant " +
                "and past wins are anonymized. The Keycloak user is deleted too."
    )
    fun deleteAccount(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) {
        accountDeletionService.deleteAccount(user)
    }
}
