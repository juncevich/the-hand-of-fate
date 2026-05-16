package com.juncevich.fate.auth.internal.web

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.internal.service.AuthResponse
import com.juncevich.fate.auth.internal.service.AuthService
import com.juncevich.fate.auth.internal.service.AuthTokens
import com.juncevich.fate.auth.internal.service.LoginRequest
import com.juncevich.fate.auth.internal.service.RefreshRequest
import com.juncevich.fate.auth.internal.service.RegisterRequest
import com.juncevich.fate.auth.internal.token.JwtProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Duration

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtProperties: JwtProperties,
    @param:Value("\${app.refresh-cookie-secure:false}") private val refreshCookieSecure: Boolean = false,
) {
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
    ): ResponseEntity<AuthResponse> {
        val tokens = authService.register(request)
        return ResponseEntity
            .status(201)
            .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken).toString())
            .body(tokens.response)
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<AuthResponse> {
        val tokens = authService.login(request.email, request.password)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken).toString())
            .body(tokens.response)
    }

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody(required = false) request: RefreshRequest?,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<AuthResponse> {
        val refreshToken =
            request.refreshTokenOrCookie(servletRequest)
                ?: throw IllegalArgumentException("Refresh token is required")
        val tokens = authService.refresh(refreshToken)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken).toString())
            .body(tokens.response)
    }

    @PostMapping("/logout")
    fun logout(
        @RequestBody(required = false) request: RefreshRequest?,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<Void> {
        val refreshToken = request.refreshTokenOrCookie(servletRequest)
        if (!refreshToken.isNullOrBlank()) {
            authService.logout(refreshToken)
        }
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
            .build()
    }

    @PostMapping("/logout-all")
    fun logoutAll(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<Void> {
        authService.logoutAll(user.id)
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
            .build()
    }

    private fun refreshCookie(value: String): ResponseCookie =
        ResponseCookie
            .from(REFRESH_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(refreshCookieSecure)
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(Duration.ofDays(jwtProperties.refreshTtlDays))
            .build()

    private fun clearRefreshCookie(): ResponseCookie =
        ResponseCookie
            .from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(refreshCookieSecure)
            .sameSite("Lax")
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO)
            .build()

    private fun RefreshRequest?.refreshTokenOrCookie(servletRequest: HttpServletRequest): String? =
        this?.refreshToken?.takeIf { it.isNotBlank() }
            ?: servletRequest.cookies
                ?.firstOrNull { it.name == REFRESH_COOKIE_NAME }
                ?.value
                ?.takeIf { it.isNotBlank() }

    private companion object {
        const val REFRESH_COOKIE_NAME = "fate_refresh_token"
    }
}
