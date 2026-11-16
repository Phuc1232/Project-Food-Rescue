// AC-13,17,24: SmtpAuthMailSender responsibility.
package com.foodrescue.backend.auth.mail

import com.foodrescue.backend.auth.*
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
@Component
class SmtpAuthMailSender(private val sender: JavaMailSender,private val p: AuthProperties): AuthMailSender {
    override fun send(email: String,purpose: TokenPurpose,token: String) {
        val message=SimpleMailMessage();
        message.setFrom(p.mailFrom);
        message.setTo(email)
        message.subject=when(purpose) { TokenPurpose.VERIFY_EMAIL -> "Verify your FoodRescue email"
            TokenPurpose.RESET_PASSWORD -> "Reset your FoodRescue password"
            TokenPurpose.SET_PASSWORD -> "Set up your FoodRescue staff account" }
        val path=if(purpose==TokenPurpose.VERIFY_EMAIL) "verify-email" else "reset-password"
        message.text=p.actionBaseUrl.trimEnd('/')+"/"+path+"#token="+token
        sender.send(message)
    }
}
