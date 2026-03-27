package com.juncevich.fate.web.auth

import com.juncevich.fate.security.AuthenticatedUser
import com.juncevich.fate.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.status(201).body(authService.register(request))

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.login(request.email, request.password))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(authService.refresh(request.refreshToken))

    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: RefreshRequest): ResponseEntity<Void> {
        authService.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/logout-all")
    fun logoutAll(@AuthenticationPrincipal user: AuthenticatedUser): ResponseEntity<Void> {
        authService.logoutAll(user.id)
        return ResponseEntity.noContent().build()
    }
}
