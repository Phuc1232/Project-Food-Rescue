// AC-01..05 (T6.4): PaymentReconciliationTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.order.service.OrderExpiryService
import com.foodrescue.backend.payment.gateway.FakePaymentGateway
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

class PaymentReconciliationTests : StoreTestFixtures() {
    @Autowired lateinit var gateway: PaymentGateway
    @Autowired lateinit var expiry: OrderExpiryService
    private val fake get() = gateway as FakePaymentGateway
    @BeforeEach fun resetFake() = fake.reset()
    private lateinit var customer: String; private lateinit var admin: String; private lateinit var batch: UUID

    private fun setup() {
        val owner = user("owner@example.test"); admin = makeAdmin("admin@example.test"); val store = store(owner, admin); openAllWeek(store, owner)
        batch = openOffer(store, owner, boxes = 6); customer = user("customer@example.test")
    }
    private fun payable(q: Int = 1): Triple<UUID, Long, Long> {
        val id = UUID.fromString(json(placeOrder(customer, batch, q))["id"].asString())
        val p = json(call("/api/v1/orders/$id/payment", "POST", null, customer)); return Triple(id, p["providerOrderCode"].asLong(), p["amount"].asLong())
    }
    private fun hook(b: String) = json(call("/api/v1/payments/webhooks/payos", "POST", b))["outcome"].asString()
    private fun status(id: UUID) = jdbc.queryForObject("select status from customer_order where id=?", String::class.java, id)!!
    private fun reconcile() = json(call("/api/v1/admin/payment-reconciliation/run", "POST", null, admin))
    /** BR-T64-01: every payment in exactly one bucket and the buckets add up. */
    private fun assertBucketsAddUp() {
        val all = json(call("/api/v1/admin/payments?size=100", token = admin))["content"]
        val total = (0 until all.size()).sumOf { all[it]["amount"].asLong() }
        val byBucket = listOf("APPLIED", "REFUND_PENDING", "REFUNDED", "UNASSIGNED", "RECEIVED").sumOf { b ->
            json(call("/api/v1/admin/payments?size=100&bucket=$b", token = admin))["content"].let { n -> (0 until n.size()).sumOf { n[it]["amount"].asLong() } } }
        assertEquals(total, byBucket); assertEquals(0, (0 until all.size()).count { all[it]["bucket"].asString() == "RECEIVED" })
    }

    @Test fun `AC-01 money that cannot be used becomes one refund obligation`() {
        setup()
        val (expired, eCode, eTotal) = payable(); expiry.expireDue(Instant.now().plusSeconds(11 * 60))
        assertEquals("REQUIRES_REVIEW", hook(fake.webhookFor(eCode, eTotal, "LATE1")))
        val (cancelled, cCode, cTotal) = payable(); call("/api/v1/orders/$cancelled/cancel", "POST", mapOf("reason" to "x"), customer)
        assertEquals("REQUIRES_REVIEW", hook(fake.webhookFor(cCode, cTotal, "CANC1")))
        val (paid, pCode, pTotal) = payable()
        assertEquals("CONFIRMED", hook(fake.webhookFor(pCode, pTotal, "OK1"))); assertEquals("REQUIRES_REVIEW", hook(fake.webhookFor(pCode, pTotal, "OK2")))
        val (short, sCode, sTotal) = payable(); assertEquals("REQUIRES_REVIEW", hook(fake.webhookFor(sCode, sTotal + 5000, "OVER1")))
        assertEquals(mapOf(expired to "LATE_PAYMENT", cancelled to "ORDER_CANCELLED", paid to "ALREADY_PAID", short to "AMOUNT_MISMATCH"),
            listOf(expired, cancelled, paid, short).associateWith { jdbc.queryForObject("select reason_code from refund_request where order_id=?", String::class.java, it) })
        assertEquals(4, count("select count(*) from refund_request where status='PENDING' and payment_id is not null and requested_amount > 0"))
        assertEquals("PENDING_PAYMENT", status(short)); assertEquals("EXPIRED", status(expired))
        assertEquals(cTotal.toInt(), json(call("/api/v1/orders/$cancelled/money", token = customer))["refundInProgress"].asInt())
        hook(fake.webhookFor(eCode, eTotal, "LATE1"))       // duplicate: no second obligation
        assertEquals(4, count("select count(*) from refund_request"))
        assertBucketsAddUp()
    }

    @Test fun `AC-02 admins assign unmatched money`() {
        setup(); val (id, _, _) = payable()
        assertEquals("REQUIRES_REVIEW", hook(fake.webhookFor(424242, 30000, "LOST1")))
        val pid = json(call("/api/v1/admin/payments?bucket=UNASSIGNED", token = admin))["content"][0]["id"].asString()
        val code = jdbc.queryForObject("select order_code from customer_order where id=?", String::class.java, id)!!
        assertEquals(403, call("/api/v1/admin/payments/$pid/assign", "POST", mapOf("orderCode" to code, "note" to "x"), customer).statusCode())
        assertEquals(404, call("/api/v1/admin/payments/$pid/assign", "POST", mapOf("orderCode" to "FR-000000-XXXXXX", "note" to "x"), admin).statusCode())
        val r = call("/api/v1/admin/payments/$pid/assign", "POST", mapOf("orderCode" to code, "note" to "Khách gửi biên lai"), admin)
        assertEquals(200, r.statusCode(), r.body()); assertEquals("REFUND_PENDING", json(r)["bucket"].asString()); assertEquals("ASSIGNED_BY_ADMIN", json(r)["refundReason"].asString())
        assertEquals("PENDING_PAYMENT", status(id))
        assertEquals(409, call("/api/v1/admin/payments/$pid/assign", "POST", mapOf("orderCode" to code, "note" to "x"), admin).statusCode())
        assertBucketsAddUp()
    }

    @Test fun `AC-03 reconciliation releases failed links, flags missing webhooks and is idempotent`() {
        setup()
        val (failed, fCode, _) = payable(2); fake.cancelLink(fCode, "customer cancelled at the bank page")
        val (missing, mCode, mTotal) = payable(); fake.webhookFor(mCode, mTotal)     // provider marks PAID but the webhook never reaches us
        val (ended, _, _) = payable(); call("/api/v1/orders/$ended/cancel", "POST", mapOf("reason" to "x"), customer)
        val (paid, pCode, pTotal) = payable(); hook(fake.webhookFor(pCode, pTotal))
        val report = reconcile()
        assertEquals(1, report["ordersReleased"].asInt()); assertEquals(1, report["missingWebhook"].asInt())
        assertEquals("EXPIRED", status(failed)); assertEquals(0, count("select count(*) from box_allocation where order_id=? and status='HELD'", failed))
        assertTrue(jdbc.queryForObject("select reconcile_note from payment_request where order_id=?", String::class.java, missing)!!.startsWith("MISSING_WEBHOOK"))
        assertEquals("PENDING_PAYMENT", status(missing))
        assertEquals("EXPIRED", jdbc.queryForObject("select status from payment_request where order_id=?", String::class.java, ended))
        assertEquals("CONFIRMED", status(paid))
        val payments = count("select count(*) from payment"); val obligations = count("select count(*) from refund_request")
        val again = reconcile(); assertEquals(0, again["ordersReleased"].asInt()); assertEquals(0, again["missingWebhook"].asInt())
        assertEquals(payments, count("select count(*) from payment")); assertEquals(obligations, count("select count(*) from refund_request"))
        // the late webhook for the flagged order still confirms it normally
        assertEquals("CONFIRMED", hook(fake.webhookFor(mCode, 0L.let { mTotal }, "LATE-WEBHOOK")))
        assertEquals(403, call("/api/v1/admin/payment-reconciliation/run", "POST", null, customer).statusCode())
    }

    @Test fun `AC-05 admin lookup by order and transaction`() {
        setup(); val (id, code, total) = payable(2); hook(fake.webhookFor(code, total, "LOOKUP1")); hook(fake.webhookFor(code, total, "LOOKUP2"))
        val orderCode = jdbc.queryForObject("select order_code from customer_order where id=?", String::class.java, id)!!
        assertEquals(2, json(call("/api/v1/admin/payments?orderCode=$orderCode", token = admin))["totalElements"].asInt())
        val one = json(call("/api/v1/admin/payments?reference=LOOKUP2", token = admin))["content"][0]
        assertEquals("REFUND_PENDING", one["bucket"].asString()); assertEquals(code, one["providerOrderCode"].asLong())
        assertEquals("APPLIED", json(call("/api/v1/admin/payments/${json(call("/api/v1/admin/payments?reference=LOOKUP1", token = admin))["content"][0]["id"].asString()}", token = admin))["bucket"].asString())
        val m = json(call("/api/v1/admin/orders/$id/money", token = admin))
        assertEquals(2 * total, m["received"].asLong()); assertEquals(total, m["applied"].asLong()); assertEquals(total, m["refundPending"].asLong())
        assertEquals(403, call("/api/v1/admin/payments", token = customer).statusCode()); assertEquals(400, call("/api/v1/admin/payments?bucket=X", token = admin).statusCode())
        assertBucketsAddUp()
    }
}
