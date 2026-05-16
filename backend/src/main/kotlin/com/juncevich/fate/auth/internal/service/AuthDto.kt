package com.juncevich.fate.auth.internal.service

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

data class RefreshRequest(
    val refreshToken: String? = null,
)

data class AuthResponse(
    val accessToken: String,
    val userId: String,
    val email: String,
    val displayName: String,
)

data class AuthTokens(
    val response: AuthResponse,
    val refreshToken: String,
)
