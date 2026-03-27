package com.juncevich.fate.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.username:noreply@handoffate.app}") private val from: String,
) {

    fun sendVoteInvitation(
        to: String,
        voteTitle: String,
        creatorName: String,
        voteUrl: String,
    ) {
        send(
            to = to,
            subject = "✦ You've been invited to a vote: $voteTitle",
            html = invitationHtml(voteTitle, creatorName, voteUrl),
        )
    }

    fun sendDrawResult(
        to: String,
        voteTitle: String,
        winnerName: String,
        winnerEmail: String,
        round: Int,
        voteUrl: String,
    ) {
        send(
            to = to,
            subject = "✦ Vote result: $voteTitle — Winner: $winnerName",
            html = drawResultHtml(voteTitle, winnerName, winnerEmail, round, voteUrl),
        )
    }

    private fun send(to: String, subject: String, html: String) {
        val message = mailSender.createMimeMessage()
        MimeMessageHelper(message, true, "UTF-8").apply {
            setFrom(from)
            setTo(to)
            setSubject(subject)
            setText(html, true)
        }
        mailSender.send(message)
    }

    private fun invitationHtml(voteTitle: String, creatorName: String, voteUrl: String) = """
        <!DOCTYPE html>
        <html><body style="font-family:sans-serif;background:#0d0d1a;color:#f0f0f0;padding:24px">
        <h2 style="color:#f59e0b">✦ The Hand of Fate</h2>
        <p><strong>$creatorName</strong> has invited you to participate in:</p>
        <h3 style="color:#fff">$voteTitle</h3>
        <a href="$voteUrl" style="display:inline-block;background:#f59e0b;color:#000;padding:12px 24px;
            border-radius:8px;text-decoration:none;font-weight:bold;margin-top:16px">
          View Vote
        </a>
        </body></html>
    """.trimIndent()

    private fun drawResultHtml(
        voteTitle: String,
        winnerName: String,
        winnerEmail: String,
        round: Int,
        voteUrl: String,
    ) = """
        <!DOCTYPE html>
        <html><body style="font-family:sans-serif;background:#0d0d1a;color:#f0f0f0;padding:24px">
        <h2 style="color:#f59e0b">✦ The Hand of Fate has spoken!</h2>
        <h3 style="color:#fff">Vote: $voteTitle</h3>
        <p style="font-size:18px">The winner of round <strong>$round</strong> is:</p>
        <p style="font-size:24px;color:#f59e0b;font-weight:bold">$winnerName</p>
        <p style="color:#a0a0b0">$winnerEmail</p>
        <a href="$voteUrl" style="display:inline-block;background:#f59e0b;color:#000;padding:12px 24px;
            border-radius:8px;text-decoration:none;font-weight:bold;margin-top:16px">
          View Results
        </a>
        </body></html>
    """.trimIndent()
}
