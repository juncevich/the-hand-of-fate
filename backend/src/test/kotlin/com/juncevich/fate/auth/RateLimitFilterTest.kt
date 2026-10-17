package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.config.RateLimitFilter
import com.juncevich.fate.auth.internal.config.RateLimitProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RateLimitFilterTest {
    private fun request(
        ip: String,
        uri: String = "/api/v1/auth/login",
    ) = MockHttpServletRequest("POST", uri).apply { remoteAddr = ip }

    @Test
    fun `blocks requests beyond capacity for the same ip`() {
        val filter = RateLimitFilter(RateLimitProperties(enabled = true, capacity = 2, windowSeconds = 60))

        repeat(2) {
            val response = MockHttpServletResponse()
            filter.doFilter(request("1.1.1.1"), response, MockFilterChain())
            assertEquals(HttpStatus.OK.value(), response.status)
        }

        val blocked = MockHttpServletResponse()
        filter.doFilter(request("1.1.1.1"), blocked, MockFilterChain())
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.status)
        assertEquals("60", blocked.getHeader("Retry-After"))
    }

    @Test
    fun `tracks ips independently`() {
        val filter = RateLimitFilter(RateLimitProperties(enabled = true, capacity = 1, windowSeconds = 60))

        val first = MockHttpServletResponse()
        filter.doFilter(request("1.1.1.1"), first, MockFilterChain())
        assertEquals(HttpStatus.OK.value(), first.status)

        val other = MockHttpServletResponse()
        filter.doFilter(request("2.2.2.2"), other, MockFilterChain())
        assertEquals(HttpStatus.OK.value(), other.status)
    }

    @Test
    fun `keys on the container-resolved address and ignores a client-supplied forwarded-for`() {
        val filter = RateLimitFilter(RateLimitProperties(enabled = true, capacity = 1, windowSeconds = 60))

        // Tomcat's RemoteIpValve has already applied X-Forwarded-For from trusted proxies to
        // remoteAddr; a client reaching the backend directly must not dodge the limit by
        // rotating the header itself.
        val first = MockHttpServletResponse()
        filter.doFilter(
            request("9.9.9.9").apply { addHeader("X-Forwarded-For", "1.1.1.1") },
            first,
            MockFilterChain()
        )
        assertEquals(HttpStatus.OK.value(), first.status)

        val second = MockHttpServletResponse()
        filter.doFilter(
            request("9.9.9.9").apply { addHeader("X-Forwarded-For", "2.2.2.2") },
            second,
            MockFilterChain()
        )
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), second.status)
    }

    @Test
    fun `resets the budget when a new window starts`() {
        val filter = RateLimitFilter(RateLimitProperties(enabled = true, capacity = 1, windowSeconds = 1))
        // Start at the beginning of a fresh one-second window so both requests share it
        val start = System.currentTimeMillis() / 1000
        while (System.currentTimeMillis() / 1000 == start) Thread.onSpinWait()

        val first = MockHttpServletResponse()
        filter.doFilter(request("1.1.1.1"), first, MockFilterChain())
        val blocked = MockHttpServletResponse()
        filter.doFilter(request("1.1.1.1"), blocked, MockFilterChain())
        assertEquals(HttpStatus.OK.value(), first.status)
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.status)

        val window = System.currentTimeMillis() / 1000
        while (System.currentTimeMillis() / 1000 == window) Thread.onSpinWait()

        val next = MockHttpServletResponse()
        filter.doFilter(request("1.1.1.1"), next, MockFilterChain())
        assertEquals(HttpStatus.OK.value(), next.status)
    }

    @Test
    fun `ignores non-auth paths`() {
        val filter = RateLimitFilter(RateLimitProperties(enabled = true, capacity = 1, windowSeconds = 60))
        assertTrue(filter.shouldNotFilter(request("1.1.1.1", uri = "/api/v1/votes")))
        assertFalse(filter.shouldNotFilter(request("1.1.1.1", uri = "/api/v1/auth/login")))
    }

    @Test
    fun `disabled filter skips all paths`() {
        val filter = RateLimitFilter(RateLimitProperties(enabled = false))
        assertTrue(filter.shouldNotFilter(request("1.1.1.1", uri = "/api/v1/auth/login")))
    }
}
