package com.juncevich.fate.notification.delivery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class MailTemplatesTest {
    private fun templates(language: String) = MailTemplates(NotificationMailProperties(locale = Locale.forLanguageTag(language)))

    @Test
    fun `emails are Russian by default`() {
        val mail = MailTemplates(NotificationMailProperties()).invitation("Обед", "Ева", "http://app/votes/1", "http://app/unsubscribe")

        assertEquals("✦ Вас пригласили в голосование: Обед", mail.subject)
        assertTrue("<html lang=\"ru\">" in mail.html)
        assertTrue("Ева приглашает вас участвовать в этом голосовании." in mail.html, mail.html)
        assertTrue(">Отписаться</a>" in mail.html)
    }

    @Test
    fun `English texts keep their apostrophes`() {
        val mail = templates("en").invitation("Lunch", "Eve", "http://app/votes/1", "http://app/unsubscribe")

        assertEquals("✦ You've been invited to a vote: Lunch", mail.subject)
        assertTrue("Eve has invited you to take part in this vote." in mail.html, mail.html)
    }

    @Test
    fun `a draw result names the winner, the round and the winner's address`() {
        val mail = templates("ru").drawResult("Обед", "Пицца", null, 1200, "http://app/votes/1", "http://app/unsubscribe")

        assertEquals("✦ Итог голосования «Обед»: Пицца", mail.subject)
        assertTrue("Победитель раунда 1200:" in mail.html, "no digit grouping in the round: ${mail.html}")
        assertTrue(">Пицца</p>" in mail.html)
        assertFalse("color:#a0a0b0\">\n" in mail.html.substringBefore("<a href"), "no empty address line without an address")

        val withAddress = templates("en").drawResult("Lunch", "Pat", "pat@test.com", 2, "http://app/votes/1", "http://app/unsubscribe")
        assertTrue(">pat@test.com</p>" in withAddress.html)
    }

    @Test
    fun `every value is html-escaped, links included`() {
        val mail =
            templates("en").invitation(
                "<a href=\"https://evil\">Win</a>",
                "<b>Eve</b>",
                "http://app/votes/1",
                "http://app/unsubscribe?email=a&sig=s"
            )

        assertTrue("&lt;a href=&quot;https://evil&quot;&gt;Win&lt;/a&gt;" in mail.html)
        assertTrue("&lt;b&gt;Eve&lt;/b&gt;" in mail.html)
        assertTrue("href=\"http://app/unsubscribe?email=a&amp;sig=s\"" in mail.html)
        // The subject is plain text, not HTML: nothing to escape there.
        assertEquals("✦ You've been invited to a vote: <a href=\"https://evil\">Win</a>", mail.subject)
    }
}
