// AC-01..14 (T7.4): No-show, receipt deadline and box recheck through HTTP/PostgreSQL.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.payment.gateway.FakePaymentGateway
import com.foodrescue.backend.pickup.service.NoShowOverdueService
import com.foodrescue.backend.pickup.service.NoShowCompensationService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*

@Import(PickupClockConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NoShowTests : StoreTestFixtures() {
    @Autowired lateinit var clock: PickupTestClock
    @Autowired lateinit var gateway: PaymentGateway
    @Autowired lateinit var overdue: NoShowOverdueService
    @Autowired lateinit var compensation: NoShowCompensationService
    private lateinit var owner: String
    private lateinit var customer: String
    private lateinit var admin: String
    private lateinit var storeId: String
    private lateinit var order: UUID
    private lateinit var batch: UUID
    private lateinit var start: Instant
    private lateinit var end: Instant
    private val base get() = "/api/v1/stores/$storeId"
    @BeforeEach fun setup() {
        clock.time = fixtureNow(); (gateway as FakePaymentGateway).reset()
        owner = user("owner@example.test"); admin = makeAdmin("admin@example.test")
        storeId = store(owner, admin); openAllWeek(storeId, owner); batch = openOffer(storeId, owner, boxes=2)
        customer = user("customer@example.test"); order = UUID.fromString(json(placeOrder(customer,batch,2))["id"].asString())
        val code = json(call("/api/v1/orders/$order/payment","POST",token=customer))["providerOrderCode"].asLong()
        assertEquals(200,call("/api/v1/payments/webhooks/payos","POST",(gateway as FakePaymentGateway).webhookFor(code,60000,"paid")).statusCode())
        start=jdbc.queryForObject("select pickup_starts_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant()
        end=jdbc.queryForObject("select pickup_ends_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant()
    }
    private fun tick(time: Instant=end) {
        clock.time=time; owner=login("owner@example.test")["accessToken"].asString()
        customer=login("customer@example.test")["accessToken"].asString(); admin=login("admin@example.test")["accessToken"].asString()
    }
    private fun confirm(key: String="no-show-key", evidence: String="Customer did not arrive", opened: Boolean=true,
        ready: Boolean=true, files: List<String> = emptyList(), token: String?=owner) = callWithHeader("$base/orders/$order/no-show","POST",
        mapOf("storeOpenedOnTime" to opened,"boxesReadyAndCompliant" to ready,"evidence" to evidence,"attachmentIds" to files),token,"Idempotency-Key",key)
    private fun confirmed(): String {tick(); val r=confirm();assertEquals(201,r.statusCode(),r.body());return json(r)["id"].asString()}
    private fun state()=jdbc.queryForObject("select status from customer_order where id=?",String::class.java,order)
    private fun err(code: String,r: java.net.http.HttpResponse<String>) {assertEquals(409,r.statusCode(),r.body());assertEquals(code,json(r)["code"].asString())}
    private fun boxId()=jdbc.queryForObject("select box_id from box_allocation where order_id=? limit 1",UUID::class.java,order)!!.toString()
    private fun recheck(box: String=boxId(), result: String="PASSED", safe: Instant=end.plusSeconds(86400), target: UUID?=null, notes: String="Rechecked", token: String?=owner) =
        call("$base/boxes/$box/recheck","POST",mapOf("result" to result,"safeUntil" to safe,"targetBatchId" to target,"notes" to notes),token)
    private fun parallel(vararg calls: ()->Int): List<Int> {
        val pool=Executors.newFixedThreadPool(calls.size);val latch=CountDownLatch(1)
        try {val futures=calls.map { c->pool.submit(Callable {latch.await();c()}) };latch.countDown();return futures.map {it.get(30,TimeUnit.SECONDS)}.sorted()}
        finally {pool.shutdownNow()}
    }
    @Test fun `AC-01,02 overdue job boundary and concurrent claims`() {
        assertEquals(0,overdue.markOverdue(end.minusNanos(1)));assertEquals("CONFIRMED",state())
        assertEquals(listOf(0,1),parallel({overdue.markOverdue(end)},{overdue.markOverdue(end)}))
        assertEquals("PENDING_NO_SHOW",state());assertEquals(0,overdue.markOverdue(end.plusSeconds(1)))
        assertEquals(1,count("select count(*) from audit_log where action='ORDER_MARKED_PENDING_NO_SHOW'"))
    }
    @Test fun `AC-02,06 active handover protects order from scan and no-show`() {
        tick(start)
        val session=callWithHeader("$base/orders/$order/pickup-sessions/start","POST",null,owner,"Idempotency-Key","handover")
        assertEquals(201,session.statusCode(),session.body());jdbc.update("update customer_order set status='CONFIRMED' where id=?",order);tick()
        assertEquals(0,overdue.markOverdue(end));err("HANDOVER_IN_PROGRESS",confirm())
        jdbc.update("update pickup_session set status='RECONCILING' where order_id=?",order)
        assertEquals(0,overdue.markOverdue(end));err("HANDOVER_IN_PROGRESS",confirm())
    }
    @Test fun `AC-03 overdue list is filtered and read only`() {
        tick();val version=jdbc.queryForObject("select version from customer_order where id=?",Long::class.java,order)
        val r=call("$base/no-shows/overdue",token=owner);assertEquals(200,r.statusCode(),r.body());val row=json(r)[0]
        assertEquals(2,row["boxes"].size());assertEquals(30000,row["projectedAmounts"]["retainedAmount"].asInt())
        assertEquals(60000,row["money"]["verifiedPaid"].asInt());assertFalse(row["customerArrivedIncident"].asBoolean())
        assertEquals(version,jdbc.queryForObject("select version from customer_order where id=?",Long::class.java,order))
        assertEquals(0,json(call("$base/no-shows/overdue?date=2000-01-01",token=owner)).size())
        assertEquals(400,call("$base/no-shows/overdue?status=CANCELLED",token=owner).statusCode())
    }
    @Test fun `AC-04,05,07 confirmation is atomic replayable and conserves money`() {
        val id=confirmed();assertEquals("NO_SHOW",state());assertEquals(200,confirm().statusCode())
        err("IDEMPOTENCY_KEY_REUSED",confirm(evidence="Different"));err("NO_SHOW_ALREADY_CONFIRMED",confirm(key="other"))
        val detail=json(call("$base/no-shows/$id",token=owner));assertEquals("HELD",detail["compensationStatus"].asString())
        assertEquals(30000,detail["amounts"]["retainedAmount"].asInt());assertEquals(30000,detail["amounts"]["refundAmount"].asInt())
        assertEquals(0,detail["amounts"]["commissionAmount"].asInt());assertEquals("PENDING",detail["notificationStatus"].asString())
        assertTrue(detail["notificationReceivedAt"].isNull);assertTrue(detail["disputeDeadline"].isNull)
        assertEquals(1,count("select count(*) from financial_event where type='NO_SHOW_CONFIRMED'"))
        assertEquals(1,count("select count(*) from refund_request where reason_code='NO_SHOW_REFUND' and requested_amount=30000"))
        assertEquals(2,count("select count(*) from physical_box where status='AWAITING_INSPECTION'"))
        assertEquals(2,count("select count(*) from box_allocation where order_id=? and status='RELEASED'",order))
        assertEquals(1,count("select count(*) from customer_order where id=? and pickup_code_hash is null and pickup_code_encrypted is null and pickup_code_key_id is null",order))
        assertFalse(json(call("/api/v1/orders/$order/pickup-code",token=customer))["usable"].asBoolean())
    }
    @Test fun `AC-05 zero verified money creates no zero refund obligation`() {
        jdbc.update("update payment set status='REJECTED' where order_id=?",order);confirmed()
        assertEquals(0,count("select count(*) from refund_request"));assertEquals(1,count("select count(*) from no_show_case where retained_amount=0 and refund_amount=0"))
    }
    @Test fun `AC-06 invalid states window preconditions and body are rejected`() {
        err("PICKUP_WINDOW_NOT_ENDED",confirm());tick()
        err("NO_SHOW_PRECONDITION_FAILED",confirm(opened=false));err("NO_SHOW_PRECONDITION_FAILED",confirm(ready=false))
        assertEquals(400,confirm(evidence=" ").statusCode());assertEquals(400,confirm(key="invalid key").statusCode())
        assertEquals(400,call("$base/orders/$order/no-show","POST",mapOf("evidence" to "missing flags"),owner).statusCode())
        for(s in listOf("COMPLETED","CANCELLED","EXPIRED")) {jdbc.update("update customer_order set status=? where id=?",s,order);err(if(s=="COMPLETED") "ORDER_ALREADY_COMPLETED" else "INVALID_ORDER_STATUS",confirm())}
        assertEquals(0,count("select count(*) from no_show_case"))
    }
    @Test fun `AC-06 incident on pending no-show protects customer and permits store cancellation`() {
        tick();assertEquals(1,overdue.markOverdue(end))
        val r=call("$base/orders/$order/pickup-incidents","POST",mapOf("reason" to "Customer arrived but unsafe food","customerArrived" to true),owner)
        assertEquals(201,r.statusCode(),r.body());assertEquals("HANDOVER_IN_PROGRESS",state());err("CUSTOMER_ARRIVED_INCIDENT",confirm())
        assertEquals(200,call("$base/orders/$order/cancel","POST",mapOf("reason" to "Store fault"),owner).statusCode())
    }
    @Test fun `AC-07 simultaneous confirmation or store cancellation yields one result`() {
        tick();assertEquals(listOf(200,201),concurrently({confirm()},{confirm()}))
        assertEquals(1,count("select count(*) from no_show_case"));assertEquals(1,count("select count(*) from refund_request"))
    }
    @Test fun `AC-07 no-show racing cancellation cannot both succeed`() {
        tick();val result=concurrently({confirm()},{call("$base/orders/$order/cancel","POST",mapOf("reason" to "Cannot supply"),owner)})
        assertTrue(result==listOf(200,409)||result==listOf(201,409),result.toString())
        assertEquals(1,count("select count(*) from refund_request"))
    }
    @Test fun `AC-08 receipt starts snapshot dispute window and replay preserves first receipt`() {
        confirmed();val notification=jdbc.queryForObject("select notification_id from no_show_case",UUID::class.java)!!
        assertEquals(0,compensation.releaseEligible(end.plusSeconds(72*3600)))
        assertEquals(1,count("select count(*) from no_show_case where dispute_deadline is null and compensation_status='HELD'"))
        compensation.recordNotificationReceived(notification,end)
        compensation.recordNotificationReceived(notification,end.plusSeconds(3600))
        assertEquals(end.plusSeconds(48*3600),jdbc.queryForObject("select dispute_deadline from no_show_case",java.sql.Timestamp::class.java)!!.toInstant())
        assertEquals(0,compensation.releaseEligible(end.plusSeconds(48*3600-1)))
        assertEquals(listOf(0,1),parallel({compensation.releaseEligible(end.plusSeconds(48*3600))},{compensation.releaseEligible(end.plusSeconds(48*3600))}))
        assertEquals(1,count("select count(*) from no_show_case where compensation_status='ELIGIBLE'"))
    }
    @Test fun `AC-08 open complaint holds compensation past deadline`() {
        confirmed();val notification=jdbc.queryForObject("select notification_id from no_show_case",UUID::class.java)!!;compensation.recordNotificationReceived(notification,end)
        jdbc.update("""insert into complaint(id,created_at,updated_at,version,store_id,order_id,customer_id,type,description,status,complaint_code)
            select ?,now(),now(),0,store_id,id,customer_id,'NO_SHOW','Disputed','OPEN','CMP-TEST-NOSHOW' from customer_order where id=?""",UUID.randomUUID(),order)
        assertEquals(0,compensation.releaseEligible(end.plusSeconds(49*3600)))
        jdbc.update("update complaint set status='CLOSED'");assertEquals(1,compensation.releaseEligible(end.plusSeconds(49*3600)))
    }
    @Test fun `AC-09,10 passed without a batch stays out of stock and failed blocks box`() {
        confirmed();val box=boxId()
        assertEquals(2,json(call("$base/boxes/awaiting-inspection",token=owner)).size())
        assertEquals(200,recheck(box).statusCode());assertEquals(2,count("select count(*) from physical_box where status='AWAITING_INSPECTION'"))
        assertEquals(0,json(call("$base/sale-batches/$batch/box-counts",token=owner))["available"].asInt())
        assertEquals(400,recheck(box,"FAILED",notes=" ").statusCode())
        assertEquals(200,recheck(box,"FAILED",safe=end.minusSeconds(1),notes="Unsafe").statusCode())
        assertEquals(1,count("select count(*) from physical_box where status='BLOCKED'"));err("BOX_NOT_AWAITING_INSPECTION",recheck(box))
    }
    @Test fun `AC-11,12 passed reassignment validates target batch and retains history`() {
        val target=openOffer(storeId,owner,boxes=1)
        jdbc.update("update sale_batch set pickup_starts_at=pickup_starts_at+interval '1 day',pickup_ends_at=pickup_ends_at+interval '1 day' where id=?",target)
        confirmed();val box=boxId();val safe=end.plusSeconds(2*86400)
        err("TARGET_BATCH_INELIGIBLE",recheck(box,target=batch,safe=safe))
        err("TARGET_BATCH_INELIGIBLE",recheck(box,target=target,safe=end.plusSeconds(1)))
        jdbc.update("update sale_batch set minimum_value=999999 where id=?",target);err("TARGET_BATCH_INELIGIBLE",recheck(box,target=target,safe=safe))
        jdbc.update("update sale_batch set minimum_value=60000,status='ENDED' where id=?",target);err("TARGET_BATCH_INELIGIBLE",recheck(box,target=target,safe=safe))
        jdbc.update("update sale_batch set status='OPEN' where id=?",target)
        assertEquals(200,recheck(box,target=target,safe=safe).statusCode())
        assertEquals(2,json(call("$base/sale-batches/$target/box-counts",token=owner))["available"].asInt())
        assertEquals(1,count("select count(*) from no_show_case"));assertEquals(2,count("select count(*) from box_allocation where order_id=? and status='RELEASED'",order))
        assertEquals(1,count("select count(*) from box_inspection where box_id=? and result='PASSED'",UUID.fromString(box)))
    }
    @Test fun `AC-13 permissions and evidence downloads follow the owning store`() {
        val png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")
        val f=upload(png,owner,"proof.png","image/png");assertEquals(201,f.statusCode(),f.body());val file=json(f)["id"].asString()
        tick();val staff=staffWith(storeId,owner,"staff@example.test",listOf("NO_SHOW_CONFIRM"));val boxStaff=staffWith(storeId,owner,"boxes@example.test",listOf("BOX_MANAGE"))
        assertEquals(401,confirm(token=null).statusCode());assertEquals(403,confirm(token=customer).statusCode());assertEquals(403,confirm(token=boxStaff).statusCode())
        assertEquals(403,call("$base/no-shows/overdue",token=boxStaff).statusCode());assertEquals(200,call("$base/no-shows/overdue",token=staff).statusCode())
        val r=confirm(files=listOf(file));assertEquals(201,r.statusCode(),r.body());assertEquals(1,json(r)["attachmentFileIds"].size())
        for(t in listOf(owner,staff,customer,admin)) assertEquals(200,download("/api/v1/files/$file/content",t).statusCode())
        val other=user("other@example.test");assertEquals(404,download("/api/v1/files/$file/content",other).statusCode())
        assertEquals(403,recheck(token=staff).statusCode());assertEquals(200,recheck(token=boxStaff).statusCode())
    }
    @Test fun `AC-04 transaction rolls back when financial event insert fails`() {
        tick();val notificationsBefore=count("select count(*) from notification");jdbc.execute("alter table financial_event add constraint test_reject_no_show check(type <> 'NO_SHOW_CONFIRMED')")
        try {assertEquals(409,confirm().statusCode());assertEquals("CONFIRMED",state());assertEquals(0,count("select count(*) from no_show_case"))
            assertEquals(0,count("select count(*) from refund_request"));assertEquals(0,count("select count(*) from notification where type='NO_SHOW'"));assertEquals(notificationsBefore,count("select count(*) from notification"))
            assertEquals(2,count("select count(*) from box_allocation where order_id=? and status='CONFIRMED'",order))
        } finally {jdbc.execute("alter table financial_event drop constraint test_reject_no_show")}
    }
    @Test fun `AC-12 allocated and unrelated boxes cannot be rechecked`() {
        val box=boxId();jdbc.update("update physical_box set status='AWAITING_INSPECTION' where id=?",UUID.fromString(box))
        err("BOX_ALLOCATED",recheck(box))
        jdbc.update("update box_allocation set status='RELEASED',released_at=now() where order_id=?",order)
        err("BOX_NOT_FROM_NO_SHOW",recheck(box))
    }
    @Test fun `AC-04 existing blocked boxes remain blocked when no-show releases allocations`() {
        val box=boxId();jdbc.update("update physical_box set status='BLOCKED' where id=?",UUID.fromString(box));confirmed()
        assertEquals(1,count("select count(*) from physical_box where status='BLOCKED'"))
        assertEquals(1,json(call("$base/boxes/awaiting-inspection",token=owner)).size())
    }
    @Test fun `AC-05,13 existing refund or foreign evidence rolls back confirmation`() {
        val png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")
        val file=json(upload(png,customer,"customer.png","image/png"))["id"].asString()
        tick();assertEquals(400,confirm(files=listOf(file)).statusCode());assertEquals(0,count("select count(*) from no_show_case"))
        jdbc.update("""insert into refund_request(id,created_at,updated_at,version,order_id,payment_id,reason,requested_amount,status)
            select ?,now(),now(),0,order_id,id,'Existing refund',received_amount,'PENDING' from payment where order_id=?""",UUID.randomUUID(),order)
        err("REFUND_AMOUNT_CONFLICT",confirm());assertEquals("CONFIRMED",state())
        assertEquals(0,count("select count(*) from no_show_case"));assertEquals(1,count("select count(*) from refund_request"))
    }

}
