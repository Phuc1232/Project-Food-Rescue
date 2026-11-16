// AC-01..06 (T6.5): ManualRefundTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.payment.gateway.FakePaymentGateway
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

class ManualRefundTests : StoreTestFixtures() {
    @Autowired lateinit var gateway: PaymentGateway
    private val fake get() = gateway as FakePaymentGateway
    @BeforeEach fun resetFake() = fake.reset()
    private lateinit var customer: String; private lateinit var admin: String; private lateinit var batch: UUID; private lateinit var store: String
    private val base = "/api/v1/admin/refund-requests"

    private fun setup() {
        val owner = user("owner@example.test"); admin = makeAdmin("admin@example.test"); store = store(owner, admin); openAllWeek(store, owner)
        batch = openOffer(store, owner, boxes = 6); customer = user("customer@example.test")
    }
    /** A cancelled order paid anyway -> obligation (LATE/CANCELLED); returns (orderId, obligationId). */
    private fun obligation(q: Int = 2): Pair<UUID, String> {
        val id = UUID.fromString(json(placeOrder(customer, batch, q))["id"].asString())
        val p = json(call("/api/v1/orders/$id/payment", "POST", null, customer))
        call("/api/v1/orders/$id/cancel", "POST", mapOf("reason" to "x"), customer)
        call("/api/v1/payments/webhooks/payos", "POST", fake.webhookFor(p["providerOrderCode"].asLong(), p["amount"].asLong()))
        return id to jdbc.queryForObject("select id from refund_request where order_id=?", UUID::class.java, id)!!.toString()
    }
    /** A paid (confirmed) order that then owes a refund through the shared obligation service (as T7.3 will). */
    private fun appliedObligation(): Pair<UUID, String> {
        val id = UUID.fromString(json(placeOrder(customer, batch, 1))["id"].asString())
        val p = json(call("/api/v1/orders/$id/payment", "POST", null, customer))
        call("/api/v1/payments/webhooks/payos", "POST", fake.webhookFor(p["providerOrderCode"].asLong(), p["amount"].asLong()))
        val pay = jdbc.queryForObject("select id from payment where order_id=?", UUID::class.java, id)!!
        val rid = UUID.randomUUID()
        jdbc.update("""insert into refund_request(id,created_at,version,updated_at,order_id,reason,requested_amount,status,payment_id,reason_code)
            values (?,now(),0,now(),?,'STORE_FAULT: test',30000,'PENDING',?,'STORE_FAULT')""", rid, id, pay)
        return id to rid.toString()
    }
    private fun approve(id: String, amount: Any, token: String = admin) = call("$base/$id/approve", "POST", mapOf("amount" to amount, "note" to "Duyệt"), token)
    private fun confirm(id: String, bank: String = "VCB" + UUID.randomUUID().toString().take(8), token: String = admin, file: String = fileId(admin, "bienlai.pdf")) =
        call("$base/$id/confirm", "POST", mapOf("bankTransactionId" to bank, "transferredAt" to Instant.now().toString(), "evidenceFileId" to file, "note" to "Đã chuyển"), token)

    @Test fun `AC-01 approval amounts are bounded`() {
        setup(); val (_, id) = obligation(2)
        assertEquals(403, approve(id, 60000, customer).statusCode())
        assertField(approve(id, 70000), "amount"); assertField(approve(id, 0), "amount"); assertField(approve(id, 1000.5), "amount")
        val r = approve(id, 40000); assertEquals(200, r.statusCode(), r.body())
        assertEquals("APPROVED", json(r)["status"].asString()); assertEquals(40000, json(r)["approvedAmount"].asInt())
        assertEquals("customer@example.test", json(r)["customerEmail"].asString()); assertEquals(60000, json(r)["refundableFromPayment"].asInt())
        assertEquals(1, json(call("$base?status=APPROVED", token = admin))["totalElements"].asInt())
    }

    @Test fun `AC-02 confirmed refunds are recorded once and only revenue is reversed in the ledger`() {
        setup(); val (late, lateId) = obligation(2)
        approve(lateId, 60000)
        val r = confirm(lateId, "VCB0001"); assertEquals(200, r.statusCode(), r.body())
        assertEquals("FULFILLED", json(r)["status"].asString()); assertEquals(60000, json(r)["refundedAmount"].asInt())
        assertEquals("SUCCEEDED", json(r)["attempts"][0]["status"].asString()); assertEquals("VCB0001", json(r)["attempts"][0]["bankTransactionId"].asString())
        assertEquals(1, count("select count(*) from financial_event where type='REFUND_SUCCEEDED' and order_id=?", late))
        assertEquals(0, count("select count(*) from ledger_entry where type='CUSTOMER_REFUND'"))      // late money was never store revenue
        val (paid, paidId) = appliedObligation(); approve(paidId, 20000)                                // partial (UC-72 A3)
        assertEquals(200, confirm(paidId).statusCode())
        assertEquals(-20000, count("select amount::int from ledger_entry where type='CUSTOMER_REFUND' and store_id=?", UUID.fromString(store)))
        val m = json(call("/api/v1/admin/orders/$paid/money", token = admin)); assertEquals(20000, m["refunded"].asInt()); assertEquals(0, m["refundPending"].asInt())
        assertTrue(count("select coalesce(sum(amount),0)::int from refund where status='SUCCEEDED'") <= count("select sum(received_amount)::int from payment"))
        val c = json(call("/api/v1/orders/$late/refunds", token = customer))[0]
        assertEquals("FULFILLED", c["status"].asString()); assertTrue(c["customerEmail"].isNull)
    }

    @Test fun `AC-03 concurrent confirmations and reused bank ids`() {
        setup(); val (_, id) = obligation(1); approve(id, 30000)
        val f1 = fileId(admin); val f2 = fileId(admin)
        assertEquals(listOf(200, 409), concurrently({ confirm(id, "BANK-RACE-1", file = f1) }, { confirm(id, "BANK-RACE-2", file = f2) }))
        assertEquals(1, count("select count(*) from refund where status='SUCCEEDED'")); assertEquals(1, count("select count(*) from financial_event where type='REFUND_SUCCEEDED'"))
        val used = jdbc.queryForObject("select provider_refund_id from refund where status='SUCCEEDED'", String::class.java)!!
        val (_, other) = obligation(1); approve(other, 30000)
        val r = confirm(other, used); assertEquals(409, r.statusCode())
        assertEquals("APPROVED", jdbc.queryForObject("select status from refund_request where id=?", String::class.java, UUID.fromString(other)))
        assertEquals(409, confirm(id).statusCode())        // already fulfilled (UC-72 A1)
    }

    @Test fun `AC-04 failures keep the obligation open`() {
        setup(); val (order, id) = obligation(1); approve(id, 30000)
        val f = call("$base/$id/fail", "POST", mapOf("note" to "Sai số tài khoản"), admin); assertEquals(200, f.statusCode(), f.body())
        assertEquals("APPROVED", json(f)["status"].asString()); assertEquals("FAILED", json(f)["attempts"][0]["status"].asString()); assertEquals(0, json(f)["refundedAmount"].asInt())
        assertEquals(30000, json(call("/api/v1/orders/$order/money", token = customer))["refundInProgress"].asInt())
        assertEquals(0, json(call("/api/v1/orders/$order/money", token = customer))["refunded"].asInt())
        assertEquals(200, confirm(id).statusCode()); assertEquals(30000, json(call("/api/v1/orders/$order/money", token = customer))["refunded"].asInt())
    }

    @Test fun `AC-05 wrong state, foreign evidence, unknown obligation`() {
        setup(); val (_, id) = obligation(1)
        assertEquals(409, confirm(id).statusCode())                        // not approved yet
        approve(id, 30000)
        assertField(confirm(id, file = fileId(customer)), "evidenceFileId")
        assertField(call("$base/$id/confirm", "POST", mapOf("bankTransactionId" to "x", "transferredAt" to Instant.now().toString(), "evidenceFileId" to fileId(admin)), admin), "bankTransactionId")
        assertEquals(404, approve(UUID.randomUUID().toString(), 1000).statusCode())
    }

    @Test fun `AC-06 customers follow their refunds and read the evidence`() {
        setup(); val (order, id) = obligation(1); approve(id, 30000); val file = fileId(admin, "bienlai.pdf"); confirm(id, file = file)
        val list = json(call("/api/v1/orders/$order/refunds", token = customer)); assertEquals(1, list.size())
        assertEquals(file, list[0]["attempts"][0]["evidenceFileIds"][0].asString())
        assertEquals(200, download("/api/v1/files/$file/content", customer).statusCode())
        val stranger = user("stranger@example.test")
        assertEquals(404, download("/api/v1/files/$file/content", stranger).statusCode()); assertEquals(404, call("/api/v1/orders/$order/refunds", token = stranger).statusCode())
    }
}
