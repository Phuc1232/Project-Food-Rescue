// AC-05 (T6.1): PaymentGatewayConfiguration responsibility.
package com.foodrescue.backend.payment.gateway

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

/** payment.gateway = payos (production, credentials required) or fake (default for local development and tests). */
@Configuration
class PaymentGatewayConfiguration {
    @Bean
    @ConditionalOnProperty(name = ["payment.gateway"], havingValue = "payos")
    fun payOsPaymentGateway(properties: PayOsProperties, mapper: ObjectMapper): PaymentGateway {
        require(properties.configured()) { "payment.gateway=payos needs PAYOS_CLIENT_ID, PAYOS_API_KEY and PAYOS_CHECKSUM_KEY" }
        return PayOsPaymentGateway(properties, mapper)
    }

    @Bean
    @ConditionalOnProperty(name = ["payment.gateway"], havingValue = "fake", matchIfMissing = true)
    fun fakePaymentGateway(mapper: ObjectMapper): PaymentGateway = FakePaymentGateway(mapper)
}
