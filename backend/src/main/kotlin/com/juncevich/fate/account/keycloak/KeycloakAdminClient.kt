package com.juncevich.fate.account.keycloak

import com.juncevich.fate.account.domain.IdentityAdmin
import com.juncevich.fate.shared.UserId
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.service.annotation.DeleteExchange
import org.springframework.web.service.annotation.PostExchange
import java.util.UUID

/**
 * The calls this backend makes to Keycloak's Admin API, relative to the realm's admin URL. An HTTP
 * service of group [KEYCLOAK_ADMIN_GROUP] (`KeycloakAdminConfig`), sent with the access token of
 * the `fate-backend` service account (its role `manage-users` is granted in infra/keycloak/realm/fate.yaml).
 */
interface KeycloakAdminApi {
    @DeleteExchange("/users/{id}")
    fun deleteUser(
        @PathVariable id: UUID,
    )

    @PostExchange("/users/{id}/logout")
    fun logoutUser(
        @PathVariable id: UUID,
    )
}

/** [IdentityAdmin] on Keycloak: an account Keycloak doesn't hold counts as already done. */
@Component
class KeycloakAdminClient(
    private val api: KeycloakAdminApi,
) : IdentityAdmin {
    override fun deleteUser(userId: UserId) = ignoringNotFound { api.deleteUser(userId.value) }

    override fun logoutUser(userId: UserId) = ignoringNotFound { api.logoutUser(userId.value) }

    // An account that is not in Keycloak (e.g. one from before Keycloak) has nothing to delete there.
    private fun ignoringNotFound(call: () -> Unit) {
        try {
            call()
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode != HttpStatus.NOT_FOUND) throw ex
        }
    }
}
