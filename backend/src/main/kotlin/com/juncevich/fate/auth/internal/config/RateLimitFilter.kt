package com.juncevich.fate.auth.internal.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory fixed-window rate limiter for the unauthenticated auth endpoints.
 * Self-contained (no external dependency); adequate for a single-instance
 * deployment. For a horizontally-scaled setup this should move to a shared
 * store (e.g. Redis).
 */
@Component
class RateLimitFilter(
    private val properties: RateLimitProperties,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val counters = ConcurrentHashMap<String, Window>()

    public override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!properties.enabled) return true
        val path = request.requestURI
        return PROTECTED_PATHS.none { path.startsWith(it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val key = clientIp(request)
        if (isAllowed(key)) {
            filterChain.doFilter(request, response)
        } else {
            log.warn("Rate limit exceeded for {} on {}", key, request.requestURI)
            rejectTooManyRequests(response)
        }
    }

    private fun isAllowed(key: String): Boolean {
        val nowWindow = System.currentTimeMillis() / (properties.windowSeconds * 1000)
        pruneIfNeeded(nowWindow)
        val window = counters.computeIfAbsent(key) { Window(AtomicLong(nowWindow), AtomicInteger(0)) }
        synchronized(window) {
            if (window.windowIndex.get() != nowWindow) {
                window.windowIndex.set(nowWindow)
                window.count.set(0)
            }
            return window.count.incrementAndGet() <= properties.capacity
        }
    }

    /** Keep the map from growing unbounded under a distributed IP flood. */
    private fun pruneIfNeeded(nowWindow: Long) {
        if (counters.size <= MAX_TRACKED_KEYS) return
        counters.entries.removeIf { it.value.windowIndex.get() < nowWindow }
    }

    private fun rejectTooManyRequests(response: HttpServletResponse) {
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", properties.windowSeconds.toString())
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.write(
            """{"title":"Too many requests, please try again later","status":429}"""
        )
    }

    private fun clientIp(request: HttpServletRequest): String {
        // Use the LAST X-Forwarded-For entry — the hop appended by our own reverse
        // proxy (nginx `$proxy_add_x_forwarded_for`). Earlier entries are supplied
        // by the client and are trivially spoofable, so keying on them would let an
        // attacker bypass the limit by rotating a fake header value per request.
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.substringAfterLast(',').trim()
        }
        return request.remoteAddr ?: "unknown"
    }

    private class Window(
        val windowIndex: AtomicLong,
        val count: AtomicInteger,
    )

    private companion object {
        val PROTECTED_PATHS =
            listOf(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/refresh"
            )
        const val MAX_TRACKED_KEYS = 10_000
    }
}
