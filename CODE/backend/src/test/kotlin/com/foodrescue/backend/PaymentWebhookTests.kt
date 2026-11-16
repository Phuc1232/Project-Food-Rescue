// AC-01..06 (T6.3): PaymentWebhookTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.order.service.OrderExpiryService
import com.foodrescue.backend.payment.gateway.FakePaymentGateway
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PaymentWebhookTests : StoreTestFixtures() {
    @Autowired lateinit var gateway: PaymentGateway
    @Autowired lateinit var expiry: OrderExpiryService
    private val fake get() = gateway as FakePaymentGateway
    @BeforeEach fun resetFake() = fake.reset()
    private lateinit var customer: String; private lateinit var owner: String; private lateinit var store: String; private lateinit var batch: UUID

    private fun setup() {
        owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test"); store = store(owner, admin); openAllWeek(store, owner)
        batch = openOffer(store, owner, boxes = 4); customer = user("customer@example.test")
    }
    /** Places an order and its payment link; returns (orderId, providerOrderCode, total). */
    private fun payable(quantity: Int = 2): Triple<UUID, Long, Long> {
        val o = json(placeOrder(customer, batch, quantity)); val id = UUID.fromString(o["id"].asString())
        val p = json(call("/api/v1/orders/$id/payment", "POST", null, customer))
        return Triple(id, p["providerOrderCode"].asLong(), p["amount"].asLong())
    }
    private fun hook(body: String): HttpResponse<String> = call("/api/v1/payments/webhooks/payos", "POST", body)
    private fun status(id: UUID) = jdbc.queryForObject("select status from customer_order where id=?", String::class.java, id)!!

    @Test fun `AC-01 full valid payment confirms the order with event and ledger`() {
        setup(); val (id, code, total) = payable(2)
        val r = hook(fake.webhookFor(code, total, "FT001")); assertEquals(200, r.statusCode(), r.body()); assertEquals("CONFIRMED", json(r)["outcome"].asString())
        assertEquals("CONFIRMED", status(id)); assertEquals("PAYMENT_VERIFIED", jdbc.queryForObject("select status_reason from customer_order where id=?", String::class.java, id))
        assertEquals(1, count("select count(*) from payment where status='VERIFIED' and verified_at is not null and order_id=?", id))
        assertEquals(2, count("select count(*) from box_allocation where status='CONFIRMED' and confirmed_at is not null and order_id=?", id))
        assertEquals(2, count("select count(*) from physical_box where status='ALLOCATED'"))
        assertEquals(1, count("select count(*) from financial_event where type='PAYMENT_RECEIVED' and order_id=?", id))
        assertEquals(total.toInt(), count("select amount::int from ledger_entry where type='PAYMENT_RECEIPT' and store_id=?", UUID.fromString(store)))
        assertEquals("CONFIRMED", json(call("/api/v1/orders/$id", token = customer))["status"].asString())
        assertEquals("CONFIRMED", json(call("/api/v1/orders/$id/payment", token = customer))["orderStatus"].asString())
        assertEquals(total.toInt(), json(call("/api/v1/orders/$id/money", token = customer))["verifiedPaid"].asInt())
        assertEquals(0, expiry.expireDue(Instant.now().plusSeconds(3600)))   // confirmed orders never expire
    }

    @Test fun `AC-02 bad signatures change nothing`() {
        setup(); val (id, code, total) = payable()
        val good = fake.webhookFor(code, total, "FT002")
        for (bad in listOf(good.replace("\"amount\":$total", "\"amount\":1"), good.replace(Regex("\"signature\":\"[0-9a-f]+\""), "\"signature\":\"${"0".repeat(64)}\""),
            good.replace(Regex(",\"signature\":\"[0-9a-f]+\""), ""), "not json", "")) {
            val r = hook(bad); assertEquals(400, r.statusCode(), bad.take(40)); assertEquals("INVALID_SIGNATURE", json(r)["code"].asString())
        }
        assertEquals(0, count("select count(*) from payment")); assertEquals("PENDING_PAYMENT", status(id))
    }

    @Test fun `AC-03 duplicates are recorded once`() {
        setup(); val (id, code, total) = payable()
        val body = fake.webhookFor(code, total, "FT003")
        assertEquals("CONFIRMED", json(hook(body))["outcome"].asString()); assertEquals("DUPLICATE", json(hook(body))["outcome"].asString())
        val (id2, code2, total2) = payable(1); val body2 = fake.webhookFor(code2, total2, "FT004")
        assertTrue(concurrently({ hook(body2) }, { hook(body2) }, { hook(body2) }).all { it == 200 })
        for (o in listOf(id, id2)) {
            assertEquals(1, count("select count(*) from payment where order_id=?", o)); assertEquals(1, count("select count(*) from financial_event where order_id=?", o))
            assertEquals("CONFIRMED", status(o))
        }
        assertEquals(2, count("select count(*) from ledger_entry"))
    }

    @Test fun `AC-04 webhook racing the expiry job gives one consistent outcome`() {
        setup(); val (id, code, total) = payable(2)
        // the job runs "after" the hold; the webhook arrives within it (server time now)
        val pool = Executors.newFixedThreadPool(2); val start = CountDownLatch(1)
        try {
            val job = pool.submit(Callable { start.await(); expiry.expireDue(Instant.now().plusSeconds(11 * 60)) })
            val web = pool.submit(Callable { start.await(); json(hook(fake.webhookFor(code, total, "FT005")))["outcome"].asString() })
            start.countDown(); job.get(30, TimeUnit.SECONDS); val outcome = web.get(30, TimeUnit.SECONDS)
            when (status(id)) {
                "CONFIRMED" -> { assertEquals("CONFIRMED", outcome); assertEquals(2, count("select count(*) from physical_box where status='ALLOCATED'")) }
                "EXPIRED" -> { assertEquals("REQUIRES_REVIEW", outcome); assertEquals(0, count("select count(*) from physical_box where status in ('ALLOCATED','RESERVED')"))
                    assertEquals(0, count("select count(*) from financial_event")) }
                else -> fail("unexpected ${status(id)}")
            }
        } finally { pool.shutdownNow() }
        assertEquals(1, count("select count(*) from payment"))
    }

    @Test fun `AC-05 late, cancelled or wrong amounts go to review`() {
        setup()
        val (late, lateCode, lateTotal) = payable(1)
        jdbc.execute("ALTER TABLE customer_order DISABLE TRIGGER immutable_order_terms")
        try { jdbc.update("update customer_order set hold_expires_at = accepted_at + interval '1 millisecond' where id=?", late) }
        finally { jdbc.execute("ALTER TABLE customer_order ENABLE TRIGGER immutable_order_terms") }
        assertEquals("REQUIRES_REVIEW", json(hook(fake.webhookFor(lateCode, lateTotal)))["outcome"].asString())
        assertEquals("EXPIRED", status(late)); assertEquals(0, count("select count(*) from box_allocation where order_id=? and status='HELD'", late))
        val (cancelled, cCode, cTotal) = payable(1)
        call("/api/v1/orders/$cancelled/cancel", "POST", mapOf("reason" to "x"), customer)
        assertEquals("REQUIRES_REVIEW", json(hook(fake.webhookFor(cCode, cTotal)))["outcome"].asString()); assertEquals("CANCELLED", status(cancelled))
        val (short, sCode, sTotal) = payable(1)
        assertEquals("REQUIRES_REVIEW", json(hook(fake.webhookFor(sCode, sTotal - 1000)))["outcome"].asString()); assertEquals("PENDING_PAYMENT", status(short))
        assertEquals(3, count("select count(*) from payment where status='REQUIRES_REVIEW'"))
        assertEquals(0, count("select count(*) from financial_event")); assertEquals(0, count("select count(*) from ledger_entry"))
    }

    @Test fun `AC-06 unknown codes and failed transfers`() {
        setup()
        assertEquals("REQUIRES_REVIEW", json(hook(fake.webhookFor(999999, 10000)))["outcome"].asString())
        assertEquals(1, count("select count(*) from payment where order_id is null and status='REQUIRES_REVIEW'"))
        val (id, code, total) = payable(1)
        assertEquals("IGNORED", json(hook(fake.webhookFor(code, total, code = "01")))["outcome"].asString())
        assertEquals("PENDING_PAYMENT", status(id)); assertEquals(1, count("select count(*) from payment"))
    }
}
