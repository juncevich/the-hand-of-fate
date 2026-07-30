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
    fun `keys on the proxy-appended forwarded-for hop, not the spoofable client prefix`() {
        val filter = RateLimitFilter(RateLimitProperties(enabled = true, capacity = 1, windowSeconds = 60))

        // Attacker rotates the client-supplied prefix but the real hop (last entry,
        // appended by nginx) stays constant — the second request must be blocked.
        val first = MockHttpServletResponse()
        filter.doFilter(
            request("10.0.0.1").apply { addHeader("X-Forwarded-For", "1.1.1.1, 9.9.9.9") },
            first,
            MockFilterChain()
        )
        assertEquals(HttpStatus.OK.value(), first.status)

        val second = MockHttpServletResponse()
        filter.doFilter(
            request("10.0.0.1").apply { addHeader("X-Forwarded-For", "2.2.2.2, 9.9.9.9") },
            second,
            MockFilterChain()
        )
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), second.status)
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
