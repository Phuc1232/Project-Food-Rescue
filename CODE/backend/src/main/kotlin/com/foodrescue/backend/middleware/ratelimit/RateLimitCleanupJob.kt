// AC-15,25,26: RateLimitCleanupJob responsibility.
package com.foodrescue.backend.middleware.ratelimit

import org.springframework.scheduling.annotation.*
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
@Configuration
@EnableScheduling
class RateLimitCleanupJob(private val store: RateLimitStore) {
    @Scheduled(fixedDelay=60000) fun cleanup() { store.cleanup() }
}
