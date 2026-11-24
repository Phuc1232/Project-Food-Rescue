// AC-01..12 (T7.5): HTTP/PostgreSQL safety intervention, retries and handover races.
package com.foodrescue.backend
import com.foodrescue.backend.support.StoreTestFixtures
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.payment.gateway.FakePaymentGateway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import java.time.Instant
import java.util.UUID

@Import(PickupClockConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BoxUnfitViolationSuspendTests : StoreTestFixtures() {
    @Autowired lateinit var clock: PickupTestClock
    @Autowired lateinit var gateway: PaymentGateway
    private lateinit var owner: String
    private lateinit var admin: String
    private lateinit var customer: String
    private lateinit var storeId: String
    private lateinit var batch: UUID
    private lateinit var order: UUID
    private val base get()="/api/v1/stores/$storeId"
    @BeforeEach fun setup() {
        clock.time=fixtureNow();(gateway as FakePaymentGateway).reset()
        owner=user("owner@example.test");admin=makeAdmin("admin@example.test");storeId=store(owner,admin);openAllWeek(storeId,owner)
        batch=openOffer(storeId,owner,boxes=10);customer=user("customer@example.test");newOrder()
    }
    private fun newOrder() {val r=placeOrder(customer,batch,2);assertEquals(201,r.statusCode(),r.body());order=UUID.fromString(json(r)["id"].asString())}
    private fun pay(): Long {
        val code=json(call("/api/v1/orders/$order/payment","POST",token=customer))["providerOrderCode"].asLong()
        assertEquals(200,hook(code).statusCode());return code
    }
    private fun hook(code: Long)=call("/api/v1/payments/webhooks/payos","POST",(gateway as FakePaymentGateway).webhookFor(code,60000,UUID.randomUUID().toString()))
    private fun tick() {
        clock.time=jdbc.queryForObject("select pickup_starts_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant()
        owner=login("owner@example.test")["accessToken"].asString();customer=login("customer@example.test")["accessToken"].asString();admin=login("admin@example.test")["accessToken"].asString()
    }
    private fun boxId()=jdbc.queryForObject("select box_id from box_allocation where order_id=? order by box_id limit 1",UUID::class.java,order)!!.toString()
    private fun state()=jdbc.queryForObject("select status from customer_order where id=?",String::class.java,order)
    private fun unfit(box: String=boxId(), key: String="unfit-001", reason: String="Unsafe food", files: List<String> = emptyList(), token: String?=owner, asAdmin: Boolean=false)=callWithHeader(
        if(asAdmin) "/api/v1/admin/boxes/$box/block" else "$base/boxes/$box/unfit","POST",mapOf("reason" to reason,"notes" to "Packaging damaged","attachmentIds" to files),token,"Idempotency-Key",key)
    private fun suspend(handling: String?=null)=call("/api/v1/admin/stores/$storeId/status","POST",mapOf("action" to "SUSPEND","reason" to "Safety intervention","existingOrderHandling" to handling),admin)
    private fun start(): String {val r=callWithHeader("$base/orders/$order/pickup-sessions/start","POST",null,owner,"Idempotency-Key","handover-$order");assertEquals(201,r.statusCode(),r.body());return json(r)["id"].asString()}
    private fun complete(id: String)=callWithHeader("$base/pickup-sessions/$id/complete","POST",null,owner,"Idempotency-Key","handover-$order")
    private fun err(code: String,r: java.net.http.HttpResponse<String>){assertEquals(409,r.statusCode(),r.body());assertEquals(code,json(r)["code"].asString())}
    private fun evidence(): String {
        val png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")
        val r=upload(png,owner,"proof.png","image/png");assertEquals(201,r.statusCode(),r.body());return json(r)["id"].asString()
    }
    @Test fun `AC-01 unallocated box is blocked with immutable inspection evidence and reduced availability`() {
        val file=evidence();val box=jdbc.queryForObject("select id from physical_box where status='AVAILABLE' limit 1",UUID::class.java)!!.toString()
        val r=unfit(box,files=listOf(file));assertEquals(200,r.statusCode(),r.body());assertEquals("BOX_BLOCKED",json(r)["outcome"].asString())
        assertEquals("BLOCKED",json(r)["box"]["box"]["status"].asString());assertEquals(0,json(r)["cancelledOrderIds"].size())
        assertEquals(1,count("select count(*) from box_inspection where box_id=? and result='FAILED'",UUID.fromString(box)))
        assertEquals(1,count("select count(*) from attachment where box_id=?",UUID.fromString(box)))
        assertEquals(7,json(call("$base/sale-batches/$batch/box-counts",token=owner))["available"].asInt())
        err("BOX_ALREADY_BLOCKED",unfit(box,key="other"))
    }
    @Test fun `AC-02 unpaid order is cancelled safe siblings released and late payment refunded`() {
        val code=json(call("/api/v1/orders/$order/payment","POST",token=customer))["providerOrderCode"].asLong()
        val r=unfit();assertEquals(200,r.statusCode(),r.body());assertEquals("CANCELLED",state())
        assertEquals(1,json(r)["releasedSafeBoxIds"].size());assertEquals(0,count("select count(*) from refund_request"))
        assertEquals(1,count("select count(*) from physical_box where status='BLOCKED'"))
        assertEquals(200,hook(code).statusCode());assertEquals("CANCELLED",state())
        assertEquals(1,count("select count(*) from refund_request where requested_amount=60000"))
    }
    @Test fun `AC-03 confirmed and pending no-show orders refund all verified money`() {
        for(status in listOf("CONFIRMED","PENDING_NO_SHOW")) {
            if(status!="CONFIRMED")newOrder();pay();jdbc.update("update customer_order set status=? where id=?",status,order)
            val r=unfit(key="unfit-$order");assertEquals(200,r.statusCode(),r.body());assertEquals("CANCELLED",state())
            assertEquals(1,count("select count(*) from refund_request where order_id=? and requested_amount=60000 and reason_code='STORE_FAULT'",order))
            assertEquals(1,count("select count(*) from customer_order where id=? and pickup_code_hash is null and pickup_code_encrypted is null and pickup_code_key_id is null",order))
            assertEquals(1,count("select count(*) from order_cancellation_request where order_id=? and store_fault=true and status='APPROVED'",order))
        }
    }
    @Test fun `AC-03 in-handover cancellation closes session and customer cancellation request`() {
        pay();tick()
        assertEquals(201,call("/api/v1/orders/$order/cancellation-requests","POST",mapOf("reason" to "Please review"),customer).statusCode())
        val session=start();assertEquals(200,unfit().statusCode());assertEquals("CANCELLED",state())
        assertEquals(1,count("select count(*) from pickup_session where status='CANCELLED'"));assertEquals(0,count("select count(*) from order_cancellation_request where status in ('SUBMITTED','NEEDS_INFO')"))
        err("INVALID_ORDER_STATUS",complete(session))
    }
    @Test fun `AC-04 delivered box opens a safety complaint and preserves delivery and money`() {
        val file=evidence();pay();tick();assertEquals(200,complete(start()).statusCode())
        val r=unfit(files=listOf(file));assertEquals(200,r.statusCode(),r.body());assertEquals("POST_DELIVERY_INCIDENT",json(r)["outcome"].asString())
        assertEquals("COMPLETED",state());assertEquals("DELIVERED",json(r)["box"]["box"]["status"].asString())
        assertFalse(json(r)["complaintId"].isNull);assertEquals(0,count("select count(*) from refund_request"))
        assertEquals(1,count("select count(*) from complaint where source_box_id=? and status='OPEN' and type='FOOD_SAFETY'",UUID.fromString(boxId())))
        assertEquals(1,count("select count(*) from attachment where complaint_id is not null"))
        val replay=unfit(files=listOf(file));assertEquals(200,replay.statusCode(),replay.body());assertEquals(json(r),json(replay))
        err("POST_DELIVERY_INCIDENT_ALREADY_OPEN",unfit(key="other"))
        for(t in listOf(owner,customer,admin))assertEquals(200,download("/api/v1/files/$file/content",t).statusCode())
    }
    @Test fun `AC-05 concurrent unfit and handover completion keep one consistent outcome`() {
        repeat(3) { round ->
        if(round > 0) {clearDatabase();setup()}
        pay();tick();val session=start();val result=concurrently({unfit()},{complete(session)})
        assertTrue(result==listOf(200,200)||result==listOf(200,409),result.toString())
        if(state()=="CANCELLED") {assertEquals(1,count("select count(*) from refund_request"));assertEquals(0,count("select count(*) from complaint"));assertEquals(1,count("select count(*) from physical_box where status='BLOCKED'"))}
        else {assertEquals("COMPLETED",state());assertEquals(0,count("select count(*) from refund_request"));assertEquals(1,count("select count(*) from complaint"));assertEquals(2,count("select count(*) from physical_box where status='DELIVERED'"))}
        }
    }
    @Test fun `AC-06 idempotent unfit and admin block reject changed payload without duplicate records`() {
        pay();val r=unfit();assertEquals(200,r.statusCode(),r.body());assertEquals(json(r),json(unfit()))
        err("IDEMPOTENCY_KEY_REUSED",unfit(reason="Different"));assertEquals(1,count("select count(*) from box_inspection where box_id=?",UUID.fromString(boxId())));assertEquals(1,count("select count(*) from refund_request"))
        val box=jdbc.queryForObject("select id from physical_box where status='AVAILABLE' limit 1",UUID::class.java)!!.toString()
        val a=unfit(box,asAdmin=true,token=admin);assertEquals(200,a.statusCode(),a.body());assertEquals(json(a),json(unfit(box,asAdmin=true,token=admin)))
        err("IDEMPOTENCY_KEY_REUSED",unfit(box,asAdmin=true,token=admin,reason="Other"))
    }
    @Test fun `AC-07 batch block processes delivered paid and unpaid orders with the existing response schema`() {
        pay();tick();assertEquals(200,complete(start()).statusCode())
        clock.time=fixtureNow();owner=login("owner@example.test")["accessToken"].asString();customer=login("customer@example.test")["accessToken"].asString();admin=login("admin@example.test")["accessToken"].asString()
        newOrder();pay();val paid=order;newOrder();val unpaid=order
        val r=call("/api/v1/admin/sale-batches/$batch/block","POST",mapOf("reason" to "Violation"),admin)
        assertEquals(200,r.statusCode(),r.body());assertEquals("BLOCKED",json(r)["status"].asString())
        assertEquals(2,count("select count(*) from complaint where status='OPEN'"));assertEquals(2,count("select count(*) from physical_box where status='DELIVERED'"))
        assertEquals(8,count("select count(*) from physical_box where status='BLOCKED'"));assertEquals(1,count("select count(*) from refund_request where order_id=?",paid))
        assertEquals(0,count("select count(*) from refund_request where order_id=?",unpaid));assertEquals("CANCELLED",state())
        assertEquals(409,call("/api/v1/admin/sale-batches/$batch/block","POST",mapOf("reason" to "Again"),admin).statusCode())
    }
    @Test fun `AC-07 batch can omit delivered incidents`() {
        pay();tick();complete(start());val r=call("/api/v1/admin/sale-batches/$batch/block","POST",mapOf("reason" to "Violation","includeDeliveredBoxesIncident" to false),admin)
        assertEquals(200,r.statusCode());assertEquals(0,count("select count(*) from complaint"));assertEquals("COMPLETED",state())
    }
    @Test fun `AC-08 suspend requires an explicit decision for existing orders`() {
        err("EXISTING_ORDERS_DECISION_REQUIRED",suspend());assertEquals("PENDING_PAYMENT",state())
        val r=suspend("CANCEL_ALL");assertEquals(200,r.statusCode(),r.body());assertEquals("CANCELLED",state())
        assertEquals(1,json(r)["suspensionOrderSummary"]["cancelledUnpaidOrders"].asInt());assertEquals(0,count("select count(*) from refund_request"))
    }
    @Test fun `AC-09 continue safe orders while unpaid orders cancel and batch pauses`() {
        pay();val retained=order;newOrder()
        val r=suspend("CONTINUE_IF_SAFE");assertEquals(200,r.statusCode(),r.body());assertEquals("CANCELLED",state())
        assertEquals(1,count("select count(*) from customer_order where id=? and status='CONFIRMED'",retained));assertEquals("PAUSED",batchStatus(batch))
        assertEquals(1,json(r)["suspensionOrderSummary"]["retainedConfirmedOrders"].asInt());assertEquals(0,count("select count(*) from refund_request"))
    }
    @Test fun `AC-10 cancel all refunds full and unsafe boxes stay blocked after reactivation`() {
        pay();jdbc.update("update physical_box set safe_until=now()+interval '1 second' where id=?",UUID.fromString(boxId()))
        val r=suspend("CANCEL_ALL");assertEquals(200,r.statusCode(),r.body());assertEquals("CANCELLED",state());assertEquals("BLOCKED",batchStatus(batch))
        assertEquals(1,count("select count(*) from refund_request where requested_amount=60000"));assertEquals(1,count("select count(*) from physical_box where status='BLOCKED'"))
        assertEquals(200,call("/api/v1/admin/stores/$storeId/status","POST",mapOf("action" to "REACTIVATE","reason" to "Fixed"),admin).statusCode())
        assertEquals("BLOCKED",batchStatus(batch));assertEquals(1,count("select count(*) from physical_box where status='BLOCKED'"))
    }
    @Test fun `AC-10 absence of handover actor or pending incident forces cancellation`() {
        pay();jdbc.update("update account set status='LOCKED' where id=?",accountId("owner@example.test"))
        val r=suspend("CONTINUE_IF_SAFE");assertEquals(200,r.statusCode(),r.body());assertFalse(json(r)["suspensionOrderSummary"]["hasAuthorizedHandoverActor"].asBoolean())
        assertEquals("CANCELLED",state());assertEquals(1,count("select count(*) from refund_request"))
    }
    @Test fun `AC-10 qualified staff can keep safe orders when owner is suspended`() {
        pay();staffWith(storeId,owner,"staff@example.test",listOf("PICKUP_HANDLE"));jdbc.update("update account set status='LOCKED' where id=?",accountId("owner@example.test"))
        val r=suspend("CONTINUE_IF_SAFE");assertEquals(200,r.statusCode(),r.body());assertTrue(json(r)["suspensionOrderSummary"]["hasAuthorizedHandoverActor"].asBoolean());assertEquals("CONFIRMED",state())
    }
    @Test fun `AC-11 permissions evidence ownership and box attachment access are scoped`() {
        val file=evidence();val box=jdbc.queryForObject("select id from physical_box where status='AVAILABLE' limit 1",UUID::class.java)!!.toString()
        assertEquals(401,unfit(box,token=null).statusCode());assertEquals(403,unfit(box,token=customer).statusCode());assertEquals(403,unfit(box,token=owner,asAdmin=true).statusCode())
        val staff=staffWith(storeId,owner,"staff@example.test",listOf("BOX_MANAGE"));val packer=staffWith(storeId,owner,"packer@example.test",listOf("BOX_PACK"))
        assertEquals(400,unfit(box,token=staff,files=listOf(file)).statusCode())
        assertEquals(200,unfit(box,files=listOf(file)).statusCode())
        for(t in listOf(owner,staff,packer,admin))assertEquals(200,download("/api/v1/files/$file/content",t).statusCode())
        assertEquals(404,download("/api/v1/files/$file/content",customer).statusCode())
        assertEquals(400,unfit(boxId(),files=listOf(file)).statusCode())
    }
    @Test fun `AC-03 refund insert failure rolls back block inspection and cancellation`() {
        pay();jdbc.execute("alter table refund_request add constraint test_reject_box_refund check(reason_code <> 'STORE_FAULT')")
        try {assertEquals(409,unfit().statusCode());assertEquals("CONFIRMED",state());assertEquals(0,count("select count(*) from box_inspection"));assertEquals(0,count("select count(*) from physical_box where status='BLOCKED'"))}
        finally {jdbc.execute("alter table refund_request drop constraint test_reject_box_refund")}
    }
    @Test fun `AC-07 batch skips existing post-delivery complaints and reports only new complaints`() {
        pay();tick();complete(start());assertEquals(200,unfit().statusCode())
        val r=call("/api/v1/admin/sale-batches/$batch/block","POST",mapOf("reason" to "Violation"),admin)
        assertEquals(200,r.statusCode(),r.body());assertEquals(2,count("select count(*) from complaint"))
        assertEquals(2,count("select count(*) from box_inspection i join physical_box b on b.id=i.box_id where i.result='FAILED' and b.status='DELIVERED'"))
        val after=jdbc.queryForObject("select after_state from audit_log where action='SALE_BATCH_BLOCKED'",String::class.java)!!
        assertEquals(1,mapper.readTree(after)["createdComplaints"].asInt())
    }

    @Test fun `AC-10 continue safe refuses expired boxes and pending handover incidents`() {
        pay();jdbc.update("update physical_box set safe_until=now()+interval '1 second' where id=?",UUID.fromString(boxId()))
        val r=suspend("CONTINUE_IF_SAFE");assertEquals(200,r.statusCode(),r.body());assertEquals("CANCELLED",state())
        assertEquals(1,count("select count(*) from refund_request where requested_amount=60000"))
    }
    @Test fun `AC-10 unresolved handover incident cannot be retained by suspension`() {
        pay();tick();start()
        assertEquals(201,call("$base/orders/$order/pickup-incidents","POST",mapOf("reason" to "Unresolved safety issue","customerArrived" to true),owner).statusCode())
        assertEquals(200,suspend("CONTINUE_IF_SAFE").statusCode());assertEquals("CANCELLED",state())
        assertEquals(1,count("select count(*) from refund_request"))
    }

}
