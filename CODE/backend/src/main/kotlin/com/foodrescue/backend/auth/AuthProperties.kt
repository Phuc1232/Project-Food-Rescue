// AC-06,13,20,24: AuthProperties responsibility.
package com.foodrescue.backend.auth

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
@Component
@ConfigurationProperties("auth")
class AuthProperties {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var environment: org.springframework.core.env.Environment
    var sessionTtl: Duration = Duration.ofDays(7)
    var resetTtl: Duration = Duration.ofMinutes(15)
    var verifyTtl: Duration = Duration.ofHours(24)
    var actionBaseUrl: String = "http://localhost:3000"
    @jakarta.annotation.PostConstruct
    fun validate() {
        if(environment.activeProfiles.contains("prod")) require(actionBaseUrl.startsWith("https://")) { "HTTPS action base URL required in production" }
        require(!sessionTtl.isNegative && !sessionTtl.isZero && !resetTtl.isNegative && !resetTtl.isZero && !verifyTtl.isNegative && !verifyTtl.isZero)
    }
    var mailFrom: String = "noreply@foodrescue.local"
}
