package com.juncevich.fate.shared

/** Names shared by the OpenAPI annotations on controllers and the spec setup in `OpenApiConfig`. */
object OpenApiDocs {
    /** Security scheme `Authorization: Bearer <Keycloak access token>`; with [KEYCLOAK] the default for every operation. */
    const val BEARER = "bearerAuth"

    /** Security scheme signing in through Keycloak (Authorization Code + PKCE, client `fate-swagger` in dev). */
    const val KEYCLOAK = "keycloak"

    const val PROBLEM_SCHEMA = "ProblemDetail"
    const val PROBLEM_REF = "#/components/schemas/$PROBLEM_SCHEMA"
    const val PROBLEM_MEDIA_TYPE = "application/problem+json"
}
