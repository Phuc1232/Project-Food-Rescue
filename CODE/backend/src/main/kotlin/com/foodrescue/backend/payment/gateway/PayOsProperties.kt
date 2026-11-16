// AC-04,05 (T6.1): PayOsProperties responsibility.
package com.foodrescue.backend.payment.gateway

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/** payos.* from environment (.env, never committed). toString never prints secrets. */
@Component
@ConfigurationProperties("payos")
class PayOsProperties {
    var clientId: String = ""
    var apiKey: String = ""
    var checksumKey: String = ""
    var baseUrl: String = "https://api-merchant.payos.vn"
    var returnUrl: String = "http://localhost:3000/payment/return"
    var cancelUrl: String = "http://localhost:3000/payment/cancel"
    var connectTimeout: Duration = Duration.ofSeconds(5)
    var readTimeout: Duration = Duration.ofSeconds(10)

    fun configured() = clientId.isNotBlank() && apiKey.isNotBlank() && checksumKey.isNotBlank()
    override fun toString() = "PayOsProperties(baseUrl=$baseUrl, configured=${configured()})"
}
