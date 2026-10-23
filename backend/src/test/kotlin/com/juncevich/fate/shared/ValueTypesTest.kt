package com.juncevich.fate.shared

import com.juncevich.fate.vote.VoteId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class ValueTypesTest {
    @Test
    fun `an email is canonical, so equal addresses are equal values`() {
        assertEquals(Email.of(" Ann@Test.com "), Email.of("ann@test.com"))
        assertEquals("ann@test.com", Email.of(" Ann@Test.com ").toString())
    }

    @Test
    fun `a user-supplied email is validated, a trusted one only canonicalized`() {
        assertEquals(ErrorCode.INVALID_EMAIL, assertThrows<BadRequestException> { Email.of("not-an-address") }.code)
        assertEquals("legacy-without-domain", Email.trusted(" Legacy-Without-Domain ").value)
    }

    @Test
    fun `ids print as their uuid`() {
        val uuid = UUID.randomUUID()
        assertEquals(uuid.toString(), UserId(uuid).toString())
        assertEquals(uuid.toString(), VoteId(uuid).toString())
        assertNotEquals(VoteId.new(), VoteId.new())
    }
}
