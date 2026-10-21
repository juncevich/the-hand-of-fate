package com.juncevich.fate.auth.internal.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val accessTokenAuthenticationConverter: Converter<Jwt, AbstractAuthenticationToken>,
    @param:Value("\${app.frontend-url}") private val frontendUrl: String,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/*/avatar")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/notifications/unsubscribe",
                        "/api/v1/notifications/unsubscribe/one-click"
                    ).permitAll()
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        "/v3/api-docs",
                        "/v3/api-docs.yaml",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    ).permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { rs ->
                rs.bearerTokenResolver(bearerTokenResolver())
                rs.jwt { it.jwtAuthenticationConverter(accessTokenAuthenticationConverter) }
            }.exceptionHandling {
                // Return 401 for unauthenticated requests to protected endpoints
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }.build()

    /**
     * The public unsubscribe endpoints ignore a bearer token, so a stale one can never turn them
     * into 401s; everywhere else an invalid token is rejected.
     */
    private fun bearerTokenResolver(): BearerTokenResolver {
        val delegate = DefaultBearerTokenResolver()
        return BearerTokenResolver { request ->
            if (UNSUBSCRIBE_PATHS.any { request.requestURI == it }) null else delegate.resolve(request)
        }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = listOf(frontendUrl)
                allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                allowedHeaders = listOf("*")
                maxAge = 3600L
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    private companion object {
        val UNSUBSCRIBE_PATHS =
            listOf(
                "/api/v1/notifications/unsubscribe",
                "/api/v1/notifications/unsubscribe/one-click"
            )
    }
}
