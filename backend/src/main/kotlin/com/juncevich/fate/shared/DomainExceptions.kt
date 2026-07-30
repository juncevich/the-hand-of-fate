package com.juncevich.fate.shared

/** 400 — the request was malformed or failed a business validation rule. */
class BadRequestException(
    message: String,
) : RuntimeException(message)

/** 404 — the requested resource does not exist. */
class NotFoundException(
    message: String,
) : RuntimeException(message)

/** 409 — the resource is in a state that conflicts with the requested action. */
class ConflictException(
    message: String,
) : RuntimeException(message)
