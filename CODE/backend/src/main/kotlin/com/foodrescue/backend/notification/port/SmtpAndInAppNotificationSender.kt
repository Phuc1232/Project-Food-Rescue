// AC-01..08 (T8.2): SmtpAndInAppNotificationSender responsibility.
package com.foodrescue.backend.notification.port
import com.foodrescue.backend.notification.NotificationProperties
import com.foodrescue.backend.auth.AuthProperties
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
@Component
class SmtpAndInAppNotificationSender(private val mail: JavaMailSender,private val properties: NotificationProperties,
    private val auth: AuthProperties) : NotificationSender {
    override fun send(command: NotificationDispatchCommand): NotificationDeliveryResult {
        if(properties.emailEnabled) {
            val title=(command.title ?: "New notification").replace('\r',' ').replace('\n',' ')
            mail.send(SimpleMailMessage().apply {
                from=properties.mailFrom.ifBlank { auth.mailFrom };setTo(command.recipientEmail)
                subject="[FoodRescue] $title"
                text=buildString {
                    appendLine(title)
                    command.orderId?.let { appendLine("Order: $it") }
                    appendLine("Open your FoodRescue inbox to view details.")
                }
            })
        }
        return NotificationDeliveryResult(NotificationDeliveryOutcome.DELIVERED)
    }
}
