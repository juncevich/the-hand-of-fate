package com.juncevich.fate.auth.internal.web

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.internal.service.AccountService
import com.juncevich.fate.auth.internal.service.AuthService
import com.juncevich.fate.auth.internal.service.AuthTokens
import com.juncevich.fate.auth.internal.service.LoginThrottledException
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
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
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
    private val accountService: AccountService,
    private val jwtProperties: JwtProperties,
    @param:Value("\${app.refresh-cookie-secure:false}") private val refreshCookieSecure: Boolean = false,
) {
    @PostMapping("/register")
    @Operation(
        operationId = "register",
        summary = "Create an account and mail a verification link",
        description = "No session is started: the account can sign in once the link from the email is followed."
    )
    @SecurityRequirements
    @ApiResponse(responseCode = "202", description = "Registered; a verification link was mailed")
    @ApiResponse(
        responseCode = "429",
        description = RATE_LIMITED,
        headers = [Header(name = "Retry-After", description = "Seconds to wait", schema = Schema(type = "integer"))],
        content = [Content(mediaType = OpenApiDocs.PROBLEM_MEDIA_TYPE, schema = Schema(ref = OpenApiDocs.PROBLEM_REF))]
    )
    fun register(
        @Valid @RequestBody request: RegisterRequest,
    ): ResponseEntity<RegistrationResponse> {
        val result = accountService.register(request.email, request.password, request.displayName)
        return ResponseEntity.accepted().body(RegistrationResponse(result.email))
    }

    @PostMapping("/verify-email")
    @Operation(operationId = "verifyEmail", summary = "Confirm the email address with the mailed token and sign in")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Verified and signed in; the refresh token is set as a cookie")
    fun verifyEmail(
        @Valid @RequestBody request: VerifyEmailRequest,
    ): ResponseEntity<AuthResponse> = session(accountService.verifyEmail(request.token))

    @PostMapping("/change-password")
    @Operation(
        operationId = "changePassword",
        summary = "Change the password",
        description =
            "Ends every other session; the caller gets a fresh one. " +
                "400 with code WRONG_PASSWORD on a wrong current password."
    )
    @ApiResponse(responseCode = "200", description = "Changed; the new refresh token is set as a cookie")
    fun changePassword(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: ChangePasswordRequest,
    ): ResponseEntity<AuthResponse> =
        session(accountService.changePassword(user.id, request.currentPassword, request.newPassword))

    @PostMapping("/login")
    @Operation(
        operationId = "login",
        summary = "Log in and start a session",
        description =
            "Answers 429 after too many requests from this client or failed attempts for this account, " +
                "and 403 with code EMAIL_NOT_VERIFIED for a correct password on an unconfirmed address."
    )
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
    ): ResponseEntity<AuthResponse> = session(authService.login(request.email, request.password))

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
        return session(authService.refresh(refreshToken))
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

    // Same 429 contract as RateLimitFilter, whose limit is per IP rather than per account.
    @ExceptionHandler(LoginThrottledException::class)
    fun handleLoginThrottled(ex: LoginThrottledException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
            .body(
                ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS).apply {
                    title = "Too many failed login attempts, please try again later"
                }
            )

    private fun session(tokens: AuthTokens): ResponseEntity<AuthResponse> =
        ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken).toString())
            .body(tokens.toResponse())

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
