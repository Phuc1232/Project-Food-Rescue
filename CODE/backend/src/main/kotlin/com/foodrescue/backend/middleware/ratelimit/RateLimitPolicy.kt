// AC-15,25,26: RateLimitPolicy responsibility.
package com.foodrescue.backend.middleware.ratelimit

enum class RateLimitPolicy(val quota: Int) {
    REGISTER_IP(10), LOGIN_IP(100), LOGIN_IDENTIFIER(30), FAILED_IP(50), FAILED_IDENTIFIER(10),
    FORGOT_IP(10), FORGOT_IDENTIFIER(3), RESEND_IP(10), RESEND_IDENTIFIER(3),
    ACTION_IP(20), REFRESH_IP(120), REFRESH_SESSION(60)
}
