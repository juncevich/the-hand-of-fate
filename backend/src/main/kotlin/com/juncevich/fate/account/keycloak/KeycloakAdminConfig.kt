package com.juncevich.fate.account.keycloak

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

/** HTTP service group of [KeycloakAdminApi]; base URL and timeouts: `spring.http.serviceclient.keycloak-admin`. */
const val KEYCLOAK_ADMIN_GROUP = "keycloak-admin"

/** Client registration of the `fate-backend` service account: `spring.security.oauth2.client.registration.fate-backend`. */
const val SERVICE_ACCOUNT_REGISTRATION = "fate-backend"

/**
 * Keycloak's Admin API as the `fate-backend` service account (client credentials). Spring Security
 * obtains the account's token, keeps it until shortly before it expires and drops it when Keycloak
 * answers 401/403, so the next call fetches a fresh one.
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = KEYCLOAK_ADMIN_GROUP, types = [KeycloakAdminApi::class])
class KeycloakAdminConfig {
    /** Works outside a web request too (`IdentityDeletionWorker` runs on the scheduler). */
    @Bean
    fun serviceAccountClientManager(
        registrations: ClientRegistrationRepository,
        authorizedClients: OAuth2AuthorizedClientService,
    ): OAuth2AuthorizedClientManager =
        AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients)

    @Bean
    fun keycloakAdminAuthentication(
        clientManager: OAuth2AuthorizedClientManager,
        authorizedClients: OAuth2AuthorizedClientService,
    ): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->
            val interceptor =
                OAuth2ClientHttpRequestInterceptor(clientManager).apply {
                    setClientRegistrationIdResolver { SERVICE_ACCOUNT_REGISTRATION }
                    // One token for the service, not one per signed-in user whose request makes the call.
                    setPrincipalResolver { SERVICE_ACCOUNT }
                    setAuthorizationFailureHandler(
                        OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClients)
                    )
                }
            groups
                .filterByName(KEYCLOAK_ADMIN_GROUP)
                .forEachClient { _, client -> client.requestInterceptor(interceptor) }
        }

    private companion object {
        val SERVICE_ACCOUNT: Authentication =
            AnonymousAuthenticationToken(
                SERVICE_ACCOUNT_REGISTRATION,
                SERVICE_ACCOUNT_REGISTRATION,
                AuthorityUtils.createAuthorityList("ROLE_SERVICE")
            )
    }
}
