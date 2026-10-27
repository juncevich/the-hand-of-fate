package com.juncevich.fate.account.keycloak

import com.juncevich.fate.account.AuthenticatedUser
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * A request authenticated by a Keycloak access token: the principal is the app's
 * [AuthenticatedUser] (what controllers get with `@AuthenticationPrincipal`), the credentials the
 * verified [token]. Says what it is, unlike a username/password token it would otherwise pose as.
 */
class KeycloakAuthentication(
    private val user: AuthenticatedUser,
    val token: Jwt,
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_USER"))) {
    init {
        // Built only from a token the resource server has already verified.
        isAuthenticated = true
    }

    override fun getPrincipal(): AuthenticatedUser = user

    override fun getCredentials(): Jwt = token

    override fun getName(): String = user.id.toString()
}
