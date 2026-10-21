package com.juncevich.fate.auth.internal.token

import com.juncevich.fate.auth.internal.keycloak.KeycloakProperties
import com.juncevich.fate.auth.internal.service.KeycloakUserProvisioner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder

/** The API accepts only access tokens issued by Keycloak (docs/adr/0001). */
@Configuration
class JwtConfig {
    @Bean
    fun jwtDecoder(keycloak: KeycloakProperties): JwtDecoder = keycloakAccessTokenDecoder(keycloak)

    @Bean
    fun accessTokenAuthenticationConverter(
        provisioner: KeycloakUserProvisioner,
    ): Converter<Jwt, AbstractAuthenticationToken> = KeycloakAccessTokenConverter(provisioner)
}
