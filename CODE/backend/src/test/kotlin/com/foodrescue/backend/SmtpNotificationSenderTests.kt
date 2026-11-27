// AC-03,04: SMTP adapter uses configured sender and keeps private payload out of email.
package com.foodrescue.backend
import com.foodrescue.backend.notification.*
import com.foodrescue.backend.notification.port.*
import com.foodrescue.backend.auth.AuthProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.mockito.ArgumentCaptor
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import java.time.Instant
import java.util.UUID
class SmtpNotificationSenderTests {
    private fun command()=NotificationDispatchCommand(UUID.randomUUID(),UUID.randomUUID(),"customer@example.test","Customer",UUID.randomUUID(),
        NotificationType.PAYMENT,"PAYMENT_VERIFIED","Payment verified","{\"token\":\"secret-payload\"}","payment-key",Instant.parse("2026-11-24T00:00:00Z"))
    @Test fun `AC-03 SMTP uses configured sender and safe summary`() {
        val mail=mock(JavaMailSender::class.java);val properties=NotificationProperties();val auth=AuthProperties();auth.mailFrom="fallback@example.test"
        properties.mailFrom="notifications@example.test"
        val result=SmtpAndInAppNotificationSender(mail,properties,auth).send(command());assertEquals(NotificationDeliveryOutcome.DELIVERED,result.outcome)
        val capture=ArgumentCaptor.forClass(SimpleMailMessage::class.java);verify(mail).send(capture.capture())
        val message=capture.value;assertEquals("notifications@example.test",message.from);assertEquals(listOf("customer@example.test"),message.to!!.toList())
        assertFalse(message.text!!.contains("secret-payload"));assertTrue(message.text!!.contains("FoodRescue inbox"))
    }
    @Test fun `AC-03 in-app only skips SMTP and default mail from falls back to auth`() {
        val mail=mock(JavaMailSender::class.java);val properties=NotificationProperties();val auth=AuthProperties();auth.mailFrom="fallback@example.test"
        val sender=SmtpAndInAppNotificationSender(mail,properties,auth);sender.send(command())
        val capture=ArgumentCaptor.forClass(SimpleMailMessage::class.java);verify(mail).send(capture.capture());assertEquals(auth.mailFrom,capture.value.from)
        clearInvocations(mail);properties.emailEnabled=false;assertEquals(NotificationDeliveryOutcome.DELIVERED,sender.send(command()).outcome);verifyNoInteractions(mail)
    }
}
