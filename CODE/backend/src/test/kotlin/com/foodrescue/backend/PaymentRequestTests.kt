// AC-01..06 (T6.2): PaymentRequestTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.payment.gateway.FakePaymentGateway
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.payment.gateway.PaymentGatewayException.Kind
import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

class PaymentRequestTests : StoreTestFixtures() {
    @Autowired lateinit var gateway: PaymentGateway
    private val fake get() = gateway as FakePaymentGateway
    @BeforeEach fun resetFake() = fake.reset()

    private lateinit var customer: String
    private fun order(quantity: Int = 1): UUID {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test"); val store = store(owner, admin); openAllWeek(store, owner)
        val batch = openOffer(store, owner, boxes = 3); customer = user("customer@example.test")
        val r = placeOrder(customer, batch, quantity); assertEquals(201, r.statusCode(), r.body()); return UUID.fromString(json(r)["id"].asString())
    }
    private fun pay(id: UUID, token: String = customer) = call("/api/v1/orders/$id/payment", "POST", null, token)

    @Test fun `AC-01 AC-02 creates one link and reuses it`() {
        val id = order(2)
        val r = pay(id); assertEquals(201, r.statusCode(), r.body())
        val p = json(r)
        assertEquals("READY", p["status"].asString()); assertEquals(60000, p["amount"].asInt()); assertEquals("FAKE", p["provider"].asString())
        assertTrue(p["providerOrderCode"].asLong() >= 100000); assertEquals("FR" + p["providerOrderCode"].asLong(), p["transferDescription"].asString())
        assertTrue(p["checkoutUrl"].asString().startsWith("https://pay.payos.vn/web/")); assertTrue(p["qrCode"].asString().isNotEmpty())
        assertEquals(json(call("/api/v1/orders/$id", token = customer))["holdExpiresAt"].asString(), p["expiresAt"].asString())
        assertEquals("PENDING_PAYMENT", p["orderStatus"].asString())
        val again = pay(id); assertEquals(200, again.statusCode()); assertEquals(p["id"].asString(), json(again)["id"].asString())
        assertEquals(1, fake.created.get()); assertEquals(1, count("select count(*) from payment_request"))
        assertEquals(p["id"].asString(), json(call("/api/v1/orders/$id/payment", token = customer))["id"].asString())
    }

    @Test fun `AC-03 concurrent calls converge on one link`() {
        val id = order()
        assertTrue(concurrently({ pay(id) }, { pay(id) }, { pay(id) }).all { it in setOf(200, 201) })
        assertEquals(1, count("select count(*) from payment_request"))
        assertEquals(1, count("select count(distinct provider_request_id) from payment_request where status='READY'"))
    }

    @Test fun `AC-04 unclear failures look up before retrying, rejections start over`() {
        val id = order()
        fake.failNext = Kind.TIMEOUT
        val r = pay(id); assertEquals(503, r.statusCode()); assertEquals("PAYMENT_PROVIDER_UNAVAILABLE", json(r)["code"].asString())
        assertEquals("PENDING", jdbc.queryForObject("select status from payment_request", String::class.java))
        val code = jdbc.queryForObject("select provider_order_code from payment_request", Long::class.java)
        val ok = pay(id); assertEquals(201, ok.statusCode(), ok.body()); assertEquals(code, json(ok)["providerOrderCode"].asLong())
        assertEquals(1, count("select count(*) from payment_request"))
        // definitive rejection on a new order: FAILED, then a new code next time
        val id2 = UUID.fromString(json(placeOrder(customer, jdbc.queryForObject("select sale_batch_id from customer_order where id=?", UUID::class.java, id)!!))["id"].asString())
        fake.failNext = Kind.REJECTED
        val rej = pay(id2); assertEquals(502, rej.statusCode()); assertEquals("PAYMENT_PROVIDER_REJECTED", json(rej)["code"].asString())
        val failedCode = jdbc.queryForObject("select provider_order_code from payment_request where order_id=? and status='FAILED'", Long::class.java, id2)
        val retry = pay(id2); assertEquals(201, retry.statusCode()); assertNotEquals(failedCode, json(retry)["providerOrderCode"].asLong())
    }

    @Test fun `AC-05 only the customer's payable orders`() {
        val id = order()
        assertEquals(404, pay(id, user("other@example.test")).statusCode()); assertEquals(401, call("/api/v1/orders/$id/payment", "POST").statusCode())
        call("/api/v1/orders/$id/cancel", "POST", mapOf("reason" to "x"), customer)
        val r = pay(id); assertEquals(409, r.statusCode()); assertEquals("ORDER_NOT_PAYABLE", json(r)["code"].asString())
        assertEquals(0, fake.created.get())
    }

    @Test fun `AC-05 hold about to end`() {
        val id = order()
        // the hold ends in under 60 s: emulate by checking at a later instant via the expiry boundary
        jdbc.execute("ALTER TABLE customer_order DISABLE TRIGGER immutable_order_terms")
        try { jdbc.update("update customer_order set hold_expires_at = ? where id=?", java.sql.Timestamp.from(Instant.now().plusSeconds(30)), id) }
        finally { jdbc.execute("ALTER TABLE customer_order ENABLE TRIGGER immutable_order_terms") }
        assertEquals("ORDER_NOT_PAYABLE", json(pay(id))["code"].asString())
    }

    @Test fun `AC-06 current request`() {
        val id = order()
        assertEquals(404, call("/api/v1/orders/$id/payment", token = customer).statusCode())
        pay(id)
        assertEquals("READY", json(call("/api/v1/orders/$id/payment", token = customer))["status"].asString())
        assertEquals(404, call("/api/v1/orders/$id/payment", token = user("other@example.test")).statusCode())
    }
}
