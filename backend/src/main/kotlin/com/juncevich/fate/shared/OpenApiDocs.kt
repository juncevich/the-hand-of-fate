package com.juncevich.fate.shared

/** Names shared by the OpenAPI annotations on controllers and the spec setup in `OpenApiConfig`. */
object OpenApiDocs {
    /** Security scheme `Authorization: Bearer <access token>`; the default for every operation. */
    const val BEARER = "bearerAuth"

    /** Security scheme for the httpOnly refresh-token cookie, accepted by `/auth/refresh` and `/auth/logout`. */
    const val REFRESH_COOKIE = "refreshCookie"

    const val PROBLEM_SCHEMA = "ProblemDetail"
    const val PROBLEM_REF = "#/components/schemas/$PROBLEM_SCHEMA"
    const val PROBLEM_MEDIA_TYPE = "application/problem+json"
}
