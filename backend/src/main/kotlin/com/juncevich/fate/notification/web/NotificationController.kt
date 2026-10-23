package com.juncevich.fate.notification.web

import com.juncevich.fate.notification.EmailPreferences
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class UnsubscribeRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val signature: String,
)

/** Opt-out links of notification emails; they work without an account or a session. */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Opting out of notification emails")
class NotificationController(
    private val preferences: EmailPreferences,
) {
    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "unsubscribe",
        summary = "Stop notification emails to an address, from the link in such an email",
        description = "400 with code INVALID_TOKEN when the signature does not match the address."
    )
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Unsubscribed")
    fun unsubscribe(
        @Valid @RequestBody request: UnsubscribeRequest,
    ) {
        preferences.unsubscribe(request.email, request.signature)
    }

    /** RFC 8058: mail clients POST `List-Unsubscribe=One-Click` (form-encoded) to the header's URL. */
    @PostMapping("/unsubscribe/one-click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "unsubscribeOneClick",
        summary = "One-click unsubscribe (RFC 8058) used by mail clients via the List-Unsubscribe header"
    )
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Unsubscribed")
    fun unsubscribeOneClick(
        @RequestParam email: String,
        @RequestParam sig: String,
    ) {
        preferences.unsubscribe(email, sig)
    }
}
