package vn.foodrescue.auth

import jakarta.mail.Multipart
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.boot.autoconfigure.mail.MailProperties
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender
import vn.foodrescue.config.MailDeliveryConfig
import vn.foodrescue.config.MailDeliverySettings
import vn.foodrescue.service.VerificationMailSender
import java.util.Properties

class MailDeliveryTest {
    private fun smtp() = MailProperties().apply {
        host = "smtp.gmail.com"; port = 587
        username = "foodrescue.qa@gmail.com"; password = "test-only-not-a-real-secret"
        properties.putAll(mapOf("mail.smtp.auth" to "true", "mail.smtp.starttls.enable" to "true",
            "mail.smtp.starttls.required" to "true", "mail.smtp.ssl.checkserveridentity" to "true"))
    }

    @Test fun `real SMTP requires credentials TLS identity and real sender`() {
        MailDeliveryConfig.validate(smtp(), "smtp", "FoodRescue <foodrescue.qa@gmail.com>")
        val invalid = listOf<MailProperties.() -> Unit>(
            { password = "" }, { username = "" }, { host = "mailpit" },
            { properties["mail.smtp.auth"] = "false" },
            { properties["mail.smtp.starttls.required"] = "false" },
            { properties["mail.smtp.ssl.checkserveridentity"] = "false" },
            { properties["mail.debug"] = "true" },
        )
        invalid.forEach { edit -> assertThrows(IllegalArgumentException::class.java) {
            MailDeliveryConfig.validate(smtp().apply(edit), "smtp", "foodrescue.qa@gmail.com")
        } }
        listOf("other@gmail.com", "noreply@foodrescue.test", "bad\r\nBcc: other@gmail.com").forEach { from ->
            assertThrows(IllegalArgumentException::class.java) { MailDeliveryConfig.validate(smtp(), "smtp", from) }
        }
    }

    @Test fun `sandbox cannot silently send to external SMTP`() {
        MailDeliveryConfig.validate(MailProperties().apply { host = "mailpit" }, "sandbox", "noreply@foodrescue.test")
        assertThrows(IllegalArgumentException::class.java) { MailDeliveryConfig.validate(smtp(), "sandbox", "foodrescue.qa@gmail.com") }
        assertThrows(IllegalArgumentException::class.java) { MailDeliveryConfig.validate(smtp(), "typo", "foodrescue.qa@gmail.com") }
    }

    @Test fun `implicit TLS and Resend are supported without disabling certificate checks`() {
        val mail = smtp().apply {
            host = "smtp.resend.com"; username = "resend"; port = 465
            properties["mail.smtp.starttls.enable"] = "false"
            properties["mail.smtp.ssl.enable"] = "true"
        }
        MailDeliveryConfig.validate(mail, "smtp", "FoodRescue <account@foodrescue.vn>")
    }

    @Test fun `email contains UTF8 plain and HTML alternatives but no OTP in subject`() {
        for (purpose in listOf("VERIFY_EMAIL", "RESET_PASSWORD")) {
            val sender = mock(JavaMailSender::class.java)
            val message = MimeMessage(Session.getInstance(Properties()))
            `when`(sender.createMimeMessage()).thenReturn(message)
            VerificationMailSender(sender, MailDeliverySettings("sandbox", "FoodRescue <noreply@foodrescue.test>"))
                .send("customer@example.test", "012345", purpose)
            message.saveChanges()
            verify(sender).send(message)
            assertEquals("customer@example.test", message.allRecipients.single().toString())
            assertFalse(message.subject.contains("012345"))
            assertEquals("auto-generated", message.getHeader("Auto-Submitted").single())
            val alternatives = mutableListOf<String>()
            fun visit(value: Any) {
                when (value) {
                    is Multipart -> (0 until value.count).forEach { visit(value.getBodyPart(it).content) }
                    is String -> alternatives.add(value)
                }
            }
            visit(message.content)
            assertEquals(2, alternatives.size)
            assertTrue(alternatives.all { it.contains("012345") && it.contains("10 phút") })
            assertTrue(alternatives.any { it.contains("<html lang=\"vi\">") })
            assertTrue(alternatives.none { it.contains("{{") || it.contains("<script") })
        }
    }

    @Test fun `delivery failure propagates so account transaction rolls back`() {
        val sender = mock(JavaMailSender::class.java)
        val message = MimeMessage(Session.getInstance(Properties()))
        `when`(sender.createMimeMessage()).thenReturn(message)
        doThrow(MailSendException("test delivery failure")).`when`(sender).send(message)
        assertThrows(MailSendException::class.java) {
            VerificationMailSender(sender, MailDeliverySettings("sandbox", "noreply@foodrescue.test"))
                .send("customer@example.test", "012345", "VERIFY_EMAIL")
        }
    }
}
