package vn.foodrescue.config

import jakarta.mail.internet.InternetAddress
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.mail.MailProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

data class MailDeliverySettings(val mode: String, val from: String)

@Configuration
class MailDeliveryConfig {
    @Bean
    fun mailDeliverySettings(
        mail: MailProperties,
        @Value("\${foodrescue.mail.mode}") mode: String,
        @Value("\${foodrescue.mail.from}") from: String,
    ): MailDeliverySettings {
        validate(mail, mode, from)
        return MailDeliverySettings(mode, from)
    }

    companion object {
        fun validate(mail: MailProperties, mode: String, from: String) {
            require(mode in setOf("sandbox", "smtp")) { "MAIL_MODE must be sandbox or smtp" }
            val address = runCatching {
                require('\r' !in from && '\n' !in from)
                InternetAddress(from, true).also { it.validate() }.address
            }.getOrNull()
            require(!address.isNullOrBlank() && '@' in address) { "MAIL_FROM must be a valid sender address" }
            val host = mail.host.orEmpty().lowercase()
            val localHosts = setOf("localhost", "127.0.0.1", "::1", "mailpit")
            require(mail.properties["mail.debug"]?.toBoolean() != true) { "SMTP debug logging must remain disabled" }
            if (mode == "sandbox") {
                require(host in localHosts) { "External mail servers require MAIL_MODE=smtp" }
                return
            }
            require(host.isNotBlank() && host !in localHosts) { "MAIL_MODE=smtp requires an external MAIL_HOST" }
            require(!mail.username.isNullOrBlank() && !mail.password.isNullOrBlank()) { "Set MAIL_USERNAME and MAIL_PASSWORD" }
            require(mail.properties["mail.smtp.auth"].toBoolean()) { "Real email requires MAIL_AUTH=true" }
            val tls = mail.properties["mail.smtp.starttls.enable"].toBoolean() &&
                mail.properties["mail.smtp.starttls.required"].toBoolean()
            val ssl = mail.properties["mail.smtp.ssl.enable"].toBoolean()
            require(tls.xor(ssl)) { "Enable either required STARTTLS or implicit TLS for real email" }
            require(mail.properties["mail.smtp.ssl.checkserveridentity"].toBoolean()) { "SMTP server identity verification must be enabled" }
            val domain = address.substringAfterLast('@').lowercase()
            require('.' in domain && !domain.endsWith(".test") && !domain.endsWith(".invalid") &&
                !domain.endsWith(".localhost") && domain !in setOf("example.com", "example.org", "example.net")) {
                "MAIL_FROM must use your real sender address"
            }
            if (host == "smtp.gmail.com") {
                require(address.equals(mail.username, ignoreCase = true)) { "For Gmail, MAIL_FROM must match MAIL_USERNAME" }
            }
        }
    }
}
