package com.juncevich.fate.auth.internal.web

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.internal.service.AuthService
import com.juncevich.fate.auth.internal.token.JwtProperties
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.OpenApiDocs
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Auth", description = "Registration, login and refresh-token session")
@SecurityScheme(
    name = OpenApiDocs.REFRESH_COOKIE,
    type = SecuritySchemeType.APIKEY,
    `in` = SecuritySchemeIn.COOKIE,
    paramName = AuthController.REFRESH_COOKIE_NAME,
    description = "httpOnly refresh token set by login/register/refresh"
)
class AuthController(
    private val authService: AuthService,
    private val jwtProperties: JwtProperties,
    @param:Value("\${app.refresh-cookie-secure:false}") private val refreshCookieSecure: Boolean = false,
) {
    @PostMapping("/register")
    @Operation(operationId = "register", summary = "Create an account and start a session")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Registered; the refresh token is set as a cookie")
    @ApiResponse(
        responseCode = "429",
        description = RATE_LIMITED,
        headers = [Header(name = "Retry-After", description = "Seconds to wait", schema = Schema(type = "integer"))],
        content = [Content(mediaType = OpenApiDocs.PROBLEM_MEDIA_TYPE, schema = Schema(ref = OpenApiDocs.PROBLEM_REF))]
    )
    fun register(
        @Valid @RequestBody request: RegisterRequest,
    ): ResponseEntity<AuthResponse> {
        val tokens = authService.register(request.email, request.password, request.displayName)
        return ResponseEntity
            .status(201)
            .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken).toString())
            .body(tokens.toResponse())
    }

    @PostMapping("/login")
    @Operation(operationId = "login", summary = "Log in and start a session")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Logged in; the refresh token is set as a cookie")
    @ApiResponse(
        responseCode = "429",
        description = RATE_LIMITED,
        headers = [Header(name = "Retry-After", description = "Seconds to wait", schema = Schema(type = "integer"))],
        content = [Content(mediaType = OpenApiDocs.PROBLEM_MEDIA_TYPE, schema = Schema(ref = OpenApiDocs.PROBLEM_REF))]
    )
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<AuthResponse> {
        val tokens = authService.login(request.email, request.password)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken).toString())
            .body(tokens.toResponse())
    }

    @PostMapping("/refresh")
    @Operation(
        operationId = "refresh",
        summary = "Rotate the refresh token and issue a new access token",
        description = "The refresh token is read from the body if present, otherwise from the cookie."
    )
    @SecurityRequirement(name = OpenApiDocs.REFRESH_COOKIE)
    @ApiResponse(responseCode = "200", description = "Refreshed; the rotated refresh token is set as a cookie")
    @ApiResponse(
        responseCode = "429",
        description = RATE_LIMITED,
        headers = [Header(name = "Retry-After", description = "Seconds to wait", schema = Schema(type = "integer"))],
        content = [Content(mediaType = OpenApiDocs.PROBLEM_MEDIA_TYPE, schema = Schema(ref = OpenApiDocs.PROBLEM_REF))]
    )
    fun refresh(
        @RequestBody(required = false) request: RefreshRequest?,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<AuthResponse> {
        val refreshToken =
            request.refreshTokenOrCookie(servletRequest)
                ?: throw BadRequestException("Refresh token is required")
        val tokens = authService.refresh(refreshToken)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken).toString())
            .body(tokens.toResponse())
    }

    @PostMapping("/logout")
    @Operation(operationId = "logout", summary = "Revoke the current refresh token and clear the cookie")
    @SecurityRequirement(name = OpenApiDocs.REFRESH_COOKIE)
    @ApiResponse(responseCode = "204", description = "Logged out")
    fun logout(
        @RequestBody(required = false) request: RefreshRequest?,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<Unit> {
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
    @Operation(operationId = "logoutAll", summary = "Revoke every refresh token of the current user")
    @ApiResponse(responseCode = "204", description = "Logged out everywhere")
    fun logoutAll(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<Unit> {
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

    internal companion object {
        const val REFRESH_COOKIE_NAME = "fate_refresh_token"
        private const val RATE_LIMITED = "Too many attempts from this client"
    }
}
