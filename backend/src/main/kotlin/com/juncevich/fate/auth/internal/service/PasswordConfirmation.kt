package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.auth.User
import com.juncevich.fate.shared.BadRequestException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/** Re-authentication with the current password before a sensitive change of a signed-in account. */
@Component
class PasswordConfirmation(
    private val passwordEncoder: PasswordEncoder,
    private val loginThrottle: LoginThrottle,
) {
    fun require(
        user: User,
        password: String,
    ) {
        // Guessing the password with a stolen access token counts like failed logins.
        loginThrottle.ensureAllowed(user.email)
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            loginThrottle.recordFailure(user.email)
            // Not 401: the caller is authenticated, and the SPA treats 401 as an expired session.
            throw BadRequestException("Current password is incorrect", code = "WRONG_PASSWORD")
        }
    }
}
