// AC-01..26: RecordingAuthMailSender responsibility.
package com.foodrescue.backend.support

import com.foodrescue.backend.auth.TokenPurpose
import com.foodrescue.backend.auth.mail.AuthMailSender
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.*
import java.util.concurrent.CopyOnWriteArrayList
class RecordingAuthMailSender: AuthMailSender {
    class Mail(val email: String,val purpose: TokenPurpose,val token: String)
    val messages=CopyOnWriteArrayList<Mail>()
    var fail=false
    override fun send(email: String,purpose: TokenPurpose,token: String) {
        if(fail) throw IllegalStateException("Simulated transport failure")
        messages.add(Mail(email,purpose,token))
    }
    fun token(email: String,purpose: TokenPurpose)=messages.last { it.email==email && it.purpose==purpose }.token
}
@TestConfiguration
class MailTestConfiguration {
    @Bean @Primary fun recordingMail()=RecordingAuthMailSender()
}
