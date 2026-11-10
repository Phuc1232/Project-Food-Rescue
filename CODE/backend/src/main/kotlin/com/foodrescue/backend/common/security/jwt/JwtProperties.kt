// AC-07,08,11,20,21: JwtProperties responsibility.
package com.foodrescue.backend.common.security.jwt

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
@Component
@ConfigurationProperties("auth.jwt")
class JwtProperties {
    var privateKeyLocation: String = ""
    var publicKeyLocation: String = ""
    var keyId: String = ""
    var issuer: String = "foodrescue"
    var audience: String = "foodrescue-api"
    var accessTtl: Duration = Duration.ofMinutes(15)
}
