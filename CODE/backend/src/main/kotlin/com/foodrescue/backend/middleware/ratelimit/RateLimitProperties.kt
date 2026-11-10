// AC-15,25,26: RateLimitProperties responsibility.
package com.foodrescue.backend.middleware.ratelimit

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
@Component
class RateLimitProperties(@Value("\${auth.rate-limit-key-secret}") val secret: String) {
    init { require(secret.toByteArray().size >= 32) { "Rate limit secret must contain at least 32 bytes" } }
}
