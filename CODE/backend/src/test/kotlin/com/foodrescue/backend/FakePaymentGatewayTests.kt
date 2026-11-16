// AC-05 (T6.1): FakePaymentGatewayTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.payment.gateway.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class FakePaymentGatewayTests {
    private val fake = FakePaymentGateway(JsonMapper.builder().build())

    @Test fun `fake behaves like the provider contract`() {
        val link = fake.createLink(PaymentLinkRequest(7, 30000, "FR7", "r", "c", null))
        assertEquals(PaymentLinkStatus.PENDING, fake.getLink(7).status)
        assertEquals(PaymentGatewayException.Kind.REJECTED, assertThrows(PaymentGatewayException::class.java) { fake.createLink(PaymentLinkRequest(7, 30000, "FR7", "r", "c", null)) }.kind)
        val body = fake.webhookFor(7, 30000, "FT1")
        val p = fake.verifyWebhook(body)
        assertEquals(7, p.orderCode); assertEquals("FT1", p.reference); assertEquals(link.paymentLinkId, p.paymentLinkId)
        assertEquals(PaymentLinkStatus.PAID, fake.getLink(7).status)
        assertThrows(InvalidSignatureException::class.java) { fake.verifyWebhook(body.replace("30000", "1")) }
        fake.failNext = PaymentGatewayException.Kind.TIMEOUT
        assertEquals(PaymentGatewayException.Kind.TIMEOUT, assertThrows(PaymentGatewayException::class.java) { fake.getLink(7) }.kind)
        assertEquals(PaymentLinkStatus.CANCELLED, fake.cancelLink(7, "x").status)
    }
}
