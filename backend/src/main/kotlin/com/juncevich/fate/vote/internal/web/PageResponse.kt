package com.juncevich.fate.vote.internal.web

import org.springframework.data.domain.Page

/**
 * Stable JSON shape for paginated responses. Serializing Spring Data's `PageImpl` directly is
 * unsupported (its structure is not a stable contract), so controllers map to this instead.
 */
data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
)

fun <T : Any> Page<T>.toResponse() = PageResponse(content, totalElements, totalPages, number, size)
