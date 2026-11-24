// AC-01..15 (T7.3): real HTTP and PostgreSQL cancellation scenarios.
package com.foodrescue.backend

import com.foodrescue.backend.payment.gateway.FakePaymentGateway
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import java.time.Instant
import java.util.UUID

@Import(PickupClockConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderCancellationTests : StoreTestFixtures() {
    @Autowired lateinit var clock: PickupTestClock
    @Autowired lateinit var gateway: PaymentGateway
    private lateinit var owner: String
    private lateinit var customer: String
    private lateinit var admin: String
    private lateinit var storeId: String
    private lateinit var orderId: UUID
    private lateinit var batch: UUID
    private lateinit var startsAt: Instant
    private val path get() = "/api/v1/orders/$orderId"
    @BeforeEach fun setup() {
        clock.time = fixtureNow(); (gateway as FakePaymentGateway).reset()
        owner = user("owner@example.test"); admin = makeAdmin("admin@example.test")
        storeId = store(owner, admin); openAllWeek(storeId, owner); batch = openOffer(storeId, owner, boxes = 8)
        customer = user("customer@example.test")
        orderId = UUID.fromString(json(placeOrder(customer, batch, 2))["id"].asString())
        startsAt = jdbc.queryForObject("select pickup_starts_at from customer_order where id=?", java.sql.Timestamp::class.java, orderId)!!.toInstant()
    }
    private fun pay() {
        val code = json(call("$path/payment", "POST", token = customer))["providerOrderCode"].asLong()
        val r = call("/api/v1/payments/webhooks/payos", "POST", (gateway as FakePaymentGateway).webhookFor(code, 60000, UUID.randomUUID().toString()))
        assertEquals(200, r.statusCode(), r.body())
    }
    private fun atPickup() {
        clock.time = startsAt
        owner = login("owner@example.test")["accessToken"].asString()
        customer = login("customer@example.test")["accessToken"].asString()
        admin = login("admin@example.test")["accessToken"].asString()
    }
    private fun keyed(url: String, body: Any? = null, token: String? = customer, key: String = "cancel-key") =
        callWithHeader(url, "POST", body, token, "Idempotency-Key", key)
    private fun cancel(key: String = "cancel-key", reason: String = "Changed plans") = keyed("$path/cancel", mapOf("reason" to reason), key = key)
    private fun submit(key: String = "request-key", files: List<String> = emptyList()) = keyed("$path/cancellation-requests", mapOf("reason" to "Please review", "attachmentIds" to files), key = key)
    private fun storeCancel(key: String = "store-key", unsafe: Boolean = true, token: String? = owner) = keyed(
        "/api/v1/stores/$storeId/orders/$orderId/cancel", mapOf("reason" to "Cannot supply", "boxesUnsafe" to unsafe), token, key)
    private fun decide(id: String, decision: String, amount: Any? = null, fault: Boolean = false, key: String = "decision-key", token: String? = admin) = keyed(
        "/api/v1/admin/cancellation-requests/$id/decision", mapOf("decision" to decision, "reason" to "Reviewed evidence", "refundAmount" to amount,
            "storeFault" to fault, "releaseBoxesToAvailable" to true), token, key)
    private fun requestId() = json(submit())["id"].asString()
    private fun status() = jdbc.queryForObject("select status from customer_order where id=?", String::class.java, orderId)
    private fun err(code: String, r: java.net.http.HttpResponse<String>) { assertEquals(409, r.statusCode(), r.body()); assertEquals(code, json(r)["code"].asString()) }
    private fun session() = keyed("/api/v1/stores/$storeId/orders/$orderId/pickup-sessions/start", token = owner, key = "pickup-key")

    @Test fun `AC-01,02 preview follows boundary and is read only`() {
        pay(); val version = jdbc.queryForObject("select version from customer_order where id=?", Long::class.java, orderId)
        val r = call("$path/cancellation-preview", token = customer); assertEquals(200, r.statusCode(), r.body())
        assertTrue(json(r)["cancellableImmediately"].asBoolean()); assertEquals(60000, json(r)["estimatedRefundAmount"].asInt())
        assertFalse(json(r)["policy"]["cancellationTerms"].asString().isBlank())
        assertEquals(version, jdbc.queryForObject("select version from customer_order where id=?", Long::class.java, orderId))
        assertEquals(0, count("select count(*) from order_cancellation_request"))
        atPickup(); val later = json(call("$path/cancellation-preview", token = customer))
        assertTrue(later["requiresReview"].asBoolean()); assertTrue(later["estimatedRefundAmount"].isNull)
        err("CANCELLATION_REQUIRES_REVIEW", cancel())
    }
    @Test fun `AC-03 unpaid cancellation remains compatible and key replays without a refund`() {
        assertEquals(200, cancel().statusCode()); assertEquals(200, cancel().statusCode())
        assertEquals("CANCELLED", status()); assertEquals(0, count("select count(*) from refund_request"))
        assertEquals(2, count("select count(*) from box_allocation where order_id=? and status='RELEASED'", orderId))
        assertEquals(1, count("select count(*) from order_cancellation_request"))
        err("IDEMPOTENCY_KEY_REUSED", cancel(reason = "Different reason"))
    }
    @Test fun `AC-04,12,13 paid immediate cancellation returns all verified money once`() {
        pay(); assertEquals(listOf(200,200), concurrently({ cancel() }, { cancel() }))
        assertEquals("CANCELLED", status()); assertEquals(1, count("select count(*) from refund_request where requested_amount=60000 and status='PENDING'"))
        assertEquals(2, count("select count(*) from box_allocation where order_id=? and status='RELEASED'", orderId))
        assertEquals(8, count("select count(*) from physical_box where status='AVAILABLE'"))
        val r = call("$path/cancellation", token = customer); assertEquals(200, r.statusCode(), r.body())
        assertEquals("CANCELLED", json(r)["orderStatus"].asString()); assertEquals(1, json(r)["refunds"].size())
        assertEquals("APPROVED", json(r)["requests"][0]["status"].asString())
    }
    @Test fun `AC-05,06 request replays and withdraw leaves order and money unchanged`() {
        pay(); err("IMMEDIATE_CANCELLATION_AVAILABLE", submit()); atPickup()
        val r = submit(); assertEquals(201, r.statusCode(), r.body()); val id = json(r)["id"].asString()
        assertEquals(200, submit().statusCode()); err("CANCELLATION_ALREADY_REQUESTED", submit("other"))
        val withdrawn = call("$path/cancellation-requests/$id/withdraw", "POST", token = customer)
        assertEquals(200, withdrawn.statusCode()); assertEquals("WITHDRAWN", json(withdrawn)["status"].asString())
        assertEquals("CONFIRMED", status()); assertEquals(0, count("select count(*) from refund_request"))
    }
    @Test fun `AC-07,14 request info supplement and evidence have scoped access`() {
        pay(); atPickup(); val id = requestId()
        assertEquals(200, decide(id,"REQUEST_INFO").statusCode())
        val image = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")
        val f = upload(image, customer, "evidence.png", "image/png"); assertEquals(201, f.statusCode(), f.body()); val fileId = json(f)["id"].asString()
        val r = call("$path/cancellation-requests/$id/supplement", "POST", mapOf("reason" to "Additional evidence", "attachmentIds" to listOf(fileId)), customer)
        assertEquals(200,r.statusCode(),r.body()); assertEquals("SUBMITTED",json(r)["status"].asString())
        for(t in listOf(customer,owner,admin)) assertEquals(200,download("/api/v1/files/$fileId/content",t).statusCode())
        val stranger = user("stranger@example.test"); assertEquals(404,download("/api/v1/files/$fileId/content",stranger).statusCode())
        assertEquals(200, decide(id,"REQUEST_INFO",key="info-again").statusCode())
        val reuse = call("$path/cancellation-requests/$id/supplement", "POST", mapOf("reason" to "Reuse", "attachmentIds" to listOf(fileId)), customer)
        assertEquals(400,reuse.statusCode())
    }
    @Test fun `AC-08 store cancellation after incident refunds all and preserves blocked boxes`() {
        pay(); atPickup(); assertEquals(201,session().statusCode())
        val incident = call("/api/v1/stores/$storeId/orders/$orderId/pickup-incidents","POST",mapOf("reason" to "Unsafe food","customerArrived" to true),owner)
        assertEquals(201,incident.statusCode()); assertTrue(json(call("$path/cancellation-preview",token=customer))["requiresReview"].asBoolean())
        val r = storeCancel(); assertEquals(200,r.statusCode(),r.body()); assertTrue(json(r)["storeFault"].asBoolean())
        assertEquals(200,storeCancel().statusCode()); assertEquals("CANCELLED",status())
        assertEquals(2,count("select count(*) from physical_box where status='BLOCKED'"))
        assertEquals(1,count("select count(*) from refund_request where reason_code='STORE_FAULT' and requested_amount=60000"))
    }
    @Test fun `AC-08 store can close customer request and return only safe boxes`() {
        pay(); atPickup(); val id = requestId()
        assertEquals(200,storeCancel(unsafe=false).statusCode()); assertEquals(200,storeCancel(unsafe=false).statusCode())
        assertEquals(1,count("select count(*) from refund_request")); assertEquals(8,count("select count(*) from physical_box where status='AVAILABLE'"))
        assertEquals(1,count("select count(*) from order_cancellation_request where id=? and status='APPROVED'",UUID.fromString(id)))
    }
    @Test fun `AC-09 approve partial zero and store fault validates money`() {
        pay(); atPickup(); val id = requestId()
        for(amount in listOf(-1,60001,0.5)) assertEquals(400,decide(id,"APPROVE",amount).statusCode())
        assertEquals(400,decide(id,"APPROVE",30000,true).statusCode())
        val r=decide(id,"APPROVE",30000);assertEquals(200,r.statusCode(),r.body())
        assertEquals("CANCELLED",status()); assertEquals(1,count("select count(*) from refund_request where requested_amount=30000"))
        assertEquals(200,decide(id,"APPROVE",30000).statusCode()); err("IDEMPOTENCY_KEY_REUSED",decide(id,"APPROVE",20000))
    }
    @Test fun `AC-09 approval of zero never creates a refund`() {
        pay();atPickup();val id=requestId();assertEquals(200,decide(id,"APPROVE",0).statusCode())
        assertEquals("CANCELLED",status());assertEquals(0,count("select count(*) from refund_request"))
    }
    @Test fun `AC-09 reject and store fault decisions`() {
        pay();atPickup();val rejected=requestId();assertEquals(200,decide(rejected,"REJECT").statusCode())
        assertEquals("CONFIRMED",status());assertEquals(0,count("select count(*) from refund_request"))
        val id=json(submit("second"))["id"].asString();assertEquals(200,decide(id,"APPROVE",60000,true).statusCode())
        assertEquals(2,count("select count(*) from physical_box where status='BLOCKED'"))
    }
    @Test fun `AC-10 completed and active handover block all cancellation writers`() {
        pay();atPickup();val request=requestId();val s=session();assertEquals(201,s.statusCode());val id=json(s)["id"].asString()
        err("HANDOVER_IN_PROGRESS",cancel());err("HANDOVER_IN_PROGRESS",storeCancel());err("HANDOVER_IN_PROGRESS",decide(request,"APPROVE",60000))
        assertEquals(200,keyed("/api/v1/stores/$storeId/pickup-sessions/$id/complete",token=owner,key="pickup-key").statusCode())
        err("ORDER_ALREADY_COMPLETED",cancel());err("ORDER_ALREADY_COMPLETED",storeCancel());err("ORDER_ALREADY_COMPLETED",decide(request,"APPROVE",60000))
        assertEquals(0,count("select count(*) from refund_request"))
    }
    @Test fun `AC-11 store cancellation racing handover has one winner`() {
        pay();atPickup();val result=concurrently({storeCancel()},{session()})
        assertTrue(result==listOf(200,409)||result==listOf(201,409),result.toString())
        if(status()=="CANCELLED") {assertEquals(1,count("select count(*) from refund_request"));assertEquals(0,count("select count(*) from pickup_session"))}
        else {assertEquals("HANDOVER_IN_PROGRESS",status());assertEquals(0,count("select count(*) from refund_request"))}
    }
    @Test fun `AC-12 refund failure never restores cancelled order`() {
        pay();cancel();val refund=jdbc.queryForObject("select id from refund_request",UUID::class.java)!!.toString()
        assertEquals(200,call("/api/v1/admin/refund-requests/$refund/approve","POST",mapOf("amount" to 60000,"note" to "Approve"),admin).statusCode())
        assertEquals(200,call("/api/v1/admin/refund-requests/$refund/fail","POST",mapOf("note" to "Bank unavailable"),admin).statusCode())
        assertEquals("CANCELLED",status());assertEquals("APPROVED",json(call("$path/cancellation",token=customer))["refunds"][0]["status"].asString())
    }
    @Test fun `AC-14 customer owner and admin routes enforce scope`() {
        pay();atPickup();val id=requestId();val other=user("other@example.test")
        assertEquals(401,call("$path/cancellation-preview").statusCode());assertEquals(404,call("$path/cancellation",token=other).statusCode())
        val staff=staffWith(storeId,owner,"staff@example.test",listOf("PICKUP_HANDLE"));assertEquals(403,storeCancel(token=staff).statusCode())
        assertEquals(403,storeCancel(token=other).statusCode());assertEquals(401,storeCancel(token=null).statusCode())
        for(t in listOf(customer,owner,staff)) {
            assertEquals(403,call("/api/v1/admin/cancellation-requests",token=t).statusCode());assertEquals(403,decide(id,"REJECT",token=t).statusCode())
        }
        assertEquals(200,call("/api/v1/admin/cancellation-requests?status=SUBMITTED",token=admin).statusCode())
        assertEquals(400,call("/api/v1/admin/cancellation-requests?size=101",token=admin).statusCode())
    }
    @Test fun `AC-04 cancellation refund failure rolls back order boxes and request`() {
        pay();jdbc.execute("alter table refund_request add constraint test_reject_cancellation_refund check (reason_code <> 'CUSTOMER_CANCELLED_BEFORE_PICKUP')")
        try {assertEquals(409,cancel().statusCode());assertEquals("CONFIRMED",status());assertEquals(0,count("select count(*) from order_cancellation_request"))
            assertEquals(2,count("select count(*) from box_allocation where order_id=? and status='CONFIRMED'",orderId))
        } finally {jdbc.execute("alter table refund_request drop constraint test_reject_cancellation_refund")}
    }
    @Test fun `AC-11 cancellation racing completion cannot refund delivered boxes`() {
        pay();atPickup();val r=session();assertEquals(201,r.statusCode());val id=json(r)["id"].asString()
        assertEquals(listOf(200,409),concurrently({storeCancel()},{keyed("/api/v1/stores/$storeId/pickup-sessions/$id/complete",token=owner,key="pickup-key")}))
        assertEquals("COMPLETED",status());assertEquals(0,count("select count(*) from refund_request"))
        assertEquals(2,count("select count(*) from box_allocation where order_id=? and status='DELIVERED'",orderId))
    }
    @Test fun `AC-04,12 existing refund obligation prevents double reserving verified money`() {
        pay()
        jdbc.update("""insert into refund_request(id,created_at,updated_at,version,order_id,payment_id,reason,reason_code,requested_amount,status)
            select ?,now(),now(),0,order_id,id,'Existing obligation','TEST',received_amount,'PENDING' from payment where order_id=?""",UUID.randomUUID(),orderId)
        err("REFUND_AMOUNT_CONFLICT",cancel());assertEquals("CONFIRMED",status())
        assertEquals(1,count("select count(*) from refund_request"));assertEquals(0,count("select count(*) from order_cancellation_request"))
    }
    @Test fun `AC-04 existing unsafe box stays blocked after customer cancellation`() {
        pay();jdbc.update("update physical_box set status='BLOCKED' where id=(select box_id from box_allocation where order_id=? limit 1)",orderId)
        assertEquals(200,cancel().statusCode());assertEquals(1,count("select count(*) from physical_box where status='BLOCKED'"))
        assertEquals(2,count("select count(*) from box_allocation where order_id=? and status='RELEASED'",orderId))
    }
    @Test fun `AC-12 concurrent admin decisions replay one refund and reject different payloads`() {
        pay();atPickup();val id=requestId()
        assertEquals(listOf(200,200),concurrently({decide(id,"APPROVE",60000)},{decide(id,"APPROVE",60000)}))
        assertEquals(1,count("select count(*) from refund_request"));assertEquals("CANCELLED",status())
        err("IDEMPOTENCY_KEY_REUSED",decide(id,"REJECT"))
    }
    @Test fun `AC-14 evidence ownership and input validation preserve the open request`() {
        pay();atPickup();val id=requestId();assertEquals(200,decide(id,"REQUEST_INFO").statusCode())
        assertEquals(400,call("$path/cancellation-requests/$id/supplement","POST",mapOf("reason" to "Unknown file","attachmentIds" to listOf(UUID.randomUUID())),customer).statusCode())
        assertEquals(1,count("select count(*) from order_cancellation_request where status='NEEDS_INFO'"))
        assertEquals(400,cancel("invalid key").statusCode())
        assertEquals(400,keyed("/api/v1/admin/cancellation-requests/$id/decision",mapOf("decision" to "REJECT","reason" to " "),admin).statusCode())
        val wrong=keyed("/api/v1/stores/$storeId/orders/$orderId/cancel",mapOf("reason" to "Wrong box","boxesUnsafe" to false,"unsafeBoxIds" to listOf(UUID.randomUUID())),owner)
        err("INVALID_UNSAFE_BOX",wrong);assertEquals("CONFIRMED",status())
    }

}
