package com.juncevich.fate.vote.domain

/** One page of a port's listing: zero-based [number], at most [size] entries. */
data class PageQuery(
    val number: Int,
    val size: Int,
) {
    init {
        require(number >= 0) { "page number must not be negative" }
        require(size > 0) { "page size must be positive" }
    }
}

/** The entries of [query]'s page and how many there are in all. */
data class PageResult<T>(
    val content: List<T>,
    val query: PageQuery,
    val totalElements: Long,
) {
    fun <R> map(transform: (T) -> R): PageResult<R> = PageResult(content.map(transform), query, totalElements)
}
