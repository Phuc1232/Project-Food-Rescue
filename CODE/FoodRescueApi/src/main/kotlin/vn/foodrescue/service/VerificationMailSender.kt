package vn.foodrescue.service

import org.springframework.core.io.ClassPathResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import vn.foodrescue.config.MailDeliverySettings
import java.util.UUID

@Service
class VerificationMailSender(private val sender: JavaMailSender, private val settings: MailDeliverySettings) {
    private val template = ClassPathResource("templates/email/verification.html").inputStream
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun send(email: String, code: String, purpose: String) {
        require(code.matches(Regex("[0-9]{6}"))) { "Expected a six-digit verification code" }
        require(purpose in setOf("RESET_PASSWORD", "VERIFY_EMAIL")) { "Unsupported verification purpose" }
        val action = if (purpose == "RESET_PASSWORD") "đặt lại mật khẩu" else "xác minh email"
        val title = if (purpose == "RESET_PASSWORD") "Đặt lại mật khẩu" else "Xác minh email của bạn"
        val plain = "Mã $action của bạn là: $code\n\nNhập mã này trong ứng dụng FoodRescue để tiếp tục. " +
            "Mã có hiệu lực trong 10 phút và chỉ dùng một lần.\n\n" +
            "Không chia sẻ mã này với bất kỳ ai. FoodRescue không yêu cầu bạn cung cấp mã qua điện thoại hoặc tin nhắn.\n\n" +
            "Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email.\nFoodRescue"
        val html = template.replace("{{title}}", title).replace("{{action}}", action).replace("{{code}}", code)
        val message = sender.createMimeMessage()
        MimeMessageHelper(message, true, "UTF-8").apply {
            setFrom(settings.from)
            setTo(email)
            setSubject("FoodRescue — $title")
            setText(plain, html)
        }
        message.setHeader("Auto-Submitted", "auto-generated")
        message.setHeader("X-Auto-Response-Suppress", "All")
        message.setHeader("X-Entity-Ref-ID", UUID.randomUUID().toString())
        sender.send(message)
    }
}
