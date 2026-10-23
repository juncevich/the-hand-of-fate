package com.juncevich.fate.shared

import java.util.UUID

/** Identifies an account (the Keycloak subject); typed so it can't be confused with other ids. */
@JvmInline
value class UserId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}
