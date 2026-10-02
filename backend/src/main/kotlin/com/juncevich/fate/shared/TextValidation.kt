package com.juncevich.fate.shared

fun requireValidTitle(value: String): String {
    val title = value.trim()
    if (title.isBlank() || title.length > 255) {
        throw BadRequestException("Title must contain between 1 and 255 characters")
    }
    return title
}
