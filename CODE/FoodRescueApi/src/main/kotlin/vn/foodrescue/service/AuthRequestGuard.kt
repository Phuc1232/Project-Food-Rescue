package vn.foodrescue.service

import org.springframework.stereotype.Service
import vn.foodrescue.repository.AuthRepository
import java.util.Locale

/** Rate-limit before opening the business transaction, avoiding nested connection-pool waits. */
@Service
class AuthRequestGuard(private val limiter: AuthRateLimiter, private val repository: AuthRepository) {
    fun mail(email: String, ip: String) {
        limiter.check("mail-ip", ip, 30)
        limiter.check("mail-email", normalized(email), 5)
    }
    fun login(email: String, ip: String) {
        limiter.check("login-ip", ip, 60)
        limiter.check("login-email", normalized(email), 10)
    }
    fun challenge(id: String, ip: String, resend: Boolean) {
        limiter.check(if (resend) "challenge-ip" else "verify-ip", ip, 60)
        repository.challenge(id)?.let {
            limiter.check(if (resend) "mail-email" else "verify-email", it.email, if (resend) 5 else 15)
        }
    }
    fun refresh(ip: String) = limiter.check("refresh-ip", ip, 120)
    fun reset(ip: String) = limiter.check("reset-ip", ip, 30)
    private fun normalized(value: String) = value.trim().lowercase(Locale.ROOT)
}
