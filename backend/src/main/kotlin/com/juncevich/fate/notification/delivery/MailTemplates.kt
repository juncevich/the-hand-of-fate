package com.juncevich.fate.notification.delivery

import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateExceptionHandler
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.stereotype.Component
import java.io.StringWriter

/** A notification email ready to send. */
data class RenderedMail(
    val subject: String,
    val html: String,
)

/**
 * Renders the notification emails: texts from `mail/messages*.properties` in
 * [NotificationMailProperties.locale], laid out by `mail/templates/notification.ftlh`, which
 * HTML-escapes every value — user-supplied text (a vote title, a name) can't inject markup.
 */
@Component
class MailTemplates(
    properties: NotificationMailProperties,
) {
    private val locale = properties.locale

    private val messages =
        ResourceBundleMessageSource().apply {
            setBasename("mail/messages")
            setDefaultEncoding(Charsets.UTF_8.name())
            setFallbackToSystemLocale(false)
            // Also for texts without arguments, so a literal apostrophe is always written ''.
            setAlwaysUseMessageFormat(true)
        }

    // Thread-safe once built: the outbox worker renders on many virtual threads at once.
    private val template: Template =
        Configuration(Configuration.VERSION_2_3_34)
            .apply {
                setClassForTemplateLoading(MailTemplates::class.java, "/mail/templates")
                defaultEncoding = Charsets.UTF_8.name()
                templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
                logTemplateExceptions = false
            }.getTemplate("notification.ftlh")

    fun invitation(
        voteTitle: String,
        creatorName: String,
        voteUrl: String,
        unsubscribePage: String,
    ) = RenderedMail(
        subject = text("invitation.subject", voteTitle),
        html =
            render(
                unsubscribePage,
                "heading" to text("invitation.heading"),
                "title" to voteTitle,
                "lead" to text("invitation.lead", creatorName),
                "button" to text("invitation.button"),
                "link" to voteUrl
            )
    )

    @Suppress("LongParameterList") // one parameter per part of the email
    fun drawResult(
        voteTitle: String,
        winnerName: String,
        winnerEmail: String?,
        round: Int,
        voteUrl: String,
        unsubscribePage: String,
    ) = RenderedMail(
        subject = text("draw.subject", voteTitle, winnerName),
        html =
            render(
                unsubscribePage,
                "heading" to text("draw.heading"),
                "title" to text("draw.title", voteTitle),
                "lead" to text("draw.lead", round),
                "winner" to winnerName,
                "winnerEmail" to winnerEmail.orEmpty(),
                "button" to text("draw.button"),
                "link" to voteUrl
            )
    )

    private fun render(
        unsubscribePage: String,
        vararg parts: Pair<String, String>,
    ): String {
        val model =
            mapOf("winner" to "", "winnerEmail" to "") +
                parts +
                mapOf(
                    "lang" to locale.language,
                    "footer" to text("footer"),
                    "unsubscribe" to text("unsubscribe"),
                    "unsubscribeLink" to unsubscribePage
                )
        return StringWriter().also { template.process(model, it) }.toString()
    }

    private fun text(
        key: String,
        vararg args: Any,
    ): String = messages.getMessage(key, args, locale)
}
