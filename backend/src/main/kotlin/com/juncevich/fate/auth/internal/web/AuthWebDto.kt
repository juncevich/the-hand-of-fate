package com.juncevich.fate.auth.internal.web

import com.juncevich.fate.auth.internal.service.AuthTokens
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email @field:NotBlank val email: String,
    @field:Size(min = 8, max = 100) val password: String,
    @field:NotBlank @field:Size(max = 100) val displayName: String,
)

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class RegistrationResponse(
    /** Where the verification link was sent. */
    val email: String,
)

data class VerifyEmailRequest(
    @field:NotBlank val token: String,
)

data class EmailRequest(
    @field:Email @field:NotBlank val email: String,
)

data class ResetPasswordRequest(
    @field:NotBlank val token: String,
    @field:Size(min = 8, max = 100) val newPassword: String,
)

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:Size(min = 8, max = 100) val newPassword: String,
)

data class DeleteAccountRequest(
    /** Confirms the deletion; without it a sign-in within the last few minutes is required. */
    val password: String? = null,
)

data class EmailPreferencesDto(
    /** Whether invitations and draw results are emailed to the account's address. */
    val notificationsEnabled: Boolean,
)

data class RefreshRequest(
    val refreshToken: String? = null,
)

data class AuthResponse(
    val accessToken: String,
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
)

// The refresh token is deliberately left out: it only travels in the httpOnly cookie.
fun AuthTokens.toResponse() =
    AuthResponse(
        accessToken = accessToken,
        userId = userId.toString(),
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl
    )
