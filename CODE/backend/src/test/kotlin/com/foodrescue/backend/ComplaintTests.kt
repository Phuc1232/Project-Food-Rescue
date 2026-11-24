// AC-01..11: Complaint HTTP workflow, financial invariants and authorization.
package com.foodrescue.backend
import com.foodrescue.backend.support.StoreTestFixtures
import com.foodrescue.backend.payment.gateway.*
import com.foodrescue.backend.pickup.service.NoShowCompensationService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import java.time.Instant
import java.util.UUID

@Import(PickupClockConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ComplaintTests : StoreTestFixtures() {
    @Autowired lateinit var clock: PickupTestClock
    @Autowired lateinit var gateway: PaymentGateway
    @Autowired lateinit var compensation: NoShowCompensationService
    lateinit var owner: String; lateinit var customer: String; lateinit var admin: String
    lateinit var storeId: String; lateinit var order: UUID; lateinit var payment: UUID
    @BeforeEach fun setup() {
        clock.time=fixtureNow(); (gateway as FakePaymentGateway).reset()
        owner=user("owner@example.test"); admin=makeAdmin("admin@example.test")
        storeId=store(owner,admin); openAllWeek(storeId,owner)
        val batch=openOffer(storeId,owner,boxes=2); customer=user("customer@example.test")
        order=UUID.fromString(json(placeOrder(customer,batch,2))["id"].asString())
        val code=json(call("/api/v1/orders/$order/payment","POST",token=customer))["providerOrderCode"].asLong()
        assertEquals(200,call("/api/v1/payments/webhooks/payos","POST",(gateway as FakePaymentGateway).webhookFor(code,60000,"paid")).statusCode())
        payment=jdbc.queryForObject("select id from payment where order_id=?",UUID::class.java,order)!!
        jdbc.update("update customer_order set status='COMPLETED' where id=?",order)
    }
    fun submit(type: String="FOOD_SAFETY", key: String="submit", description: String="Unsafe food", files: List<String> = emptyList(), token: String?=customer)=
        callWithHeader("/api/v1/complaints","POST",mapOf("orderId" to order,"type" to type,"description" to description,"attachmentIds" to files),token,"Idempotency-Key",key)
    fun complaint(): String { val r=submit(); assertEquals(201,r.statusCode(),r.body()); return json(r)["id"].asString() }
    fun decide(id: String, amount: Int=30000, outcome: String="PARTIAL", key: String="decision")=callWithHeader("/api/v1/admin/complaints/$id/decide","POST",
        mapOf("outcome" to outcome,"responsibility" to "STORE","reason" to "Evidence confirmed", "refundAllocations" to if(amount==0) emptyList() else listOf(mapOf("paymentId" to payment,"amount" to amount))),admin,"Idempotency-Key",key)
    fun noShow(late: Boolean=false) {
        jdbc.update("update customer_order set status='NO_SHOW' where id=?",order)
        jdbc.update("""insert into no_show_case(id,created_at,updated_at,version,order_id,verified_by_id,verified_at,evidence,retained_amount,refund_amount,compensation_status,notification_received_at,dispute_deadline)
            values (?,now(),now(),0,?,?,now(),'Not arrived',18000,42000,'HELD',now()-interval '49 hours',?)""",UUID.randomUUID(),order,accountId("owner@example.test"),java.sql.Timestamp.from(clock.time.plusSeconds(if(late) -1 else 3600)))
    }
    @Test fun `AC-01 submit evidence replay duplicate and conflicting key`() {
        val f=imageId(customer); val r=submit(files=listOf(f)); assertEquals(201,r.statusCode(),r.body())
        assertTrue(json(r)["complaintCode"].asString().startsWith("CMP-"))
        assertEquals(200,submit(files=listOf(f)).statusCode()); assertEquals(409,submit(description="Changed").statusCode())
        assertEquals(409,submit(key="new").statusCode()); assertEquals(400,submit(key="bad key").statusCode())
        assertEquals(200,download("/api/v1/files/$f",customer).statusCode())
    }
    @Test fun `AC-02 timely and null deadline dispute remains protected`() {
        noShow(); assertEquals(201,submit("NO_SHOW").statusCode())
        assertEquals("DISPUTED",jdbc.queryForObject("select compensation_status from no_show_case",String::class.java))
        assertEquals(0,compensation.releaseEligible(clock.time.plusSeconds(172800)))
    }
    @Test fun `AC-03 late support does not restore dispute`() {
        noShow(true); val r=submit("NO_SHOW"); assertEquals(201,r.statusCode(),r.body());assertFalse(json(r)["withinDisputeWindow"].asBoolean())
        assertEquals("HELD",jdbc.queryForObject("select compensation_status from no_show_case",String::class.java))
    }
    @Test fun `AC-04 list filters and complete admin detail`() {
        val id=complaint(); val list=call("/api/v1/admin/complaints?status=OPEN&type=FOOD_SAFETY&storeId=$storeId&orderId=$order",token=admin)
        assertEquals(200,list.statusCode(),list.body()); assertEquals(1,json(list)["totalElements"].asInt())
        val detail=call("/api/v1/admin/complaints/$id",token=admin);assertEquals(200,detail.statusCode(),detail.body())
        for(field in listOf("complaint","order","money","boxes","payments","refundRequests","handover","history")) assertTrue(json(detail).has(field),field)
        assertEquals(0,json(call("/api/v1/admin/complaints?status=CLOSED",token=admin))["totalElements"].asInt())
    }
    @Test fun `AC-05 request information and supplement evidence`() {
        val id=complaint(); assertEquals(200,call("/api/v1/admin/complaints/$id/request-info","POST",mapOf("note" to "More evidence"),admin).statusCode())
        val r=call("/api/v1/complaints/$id/supplement","POST",mapOf("description" to "Additional details","attachmentIds" to listOf(imageId(customer))),customer)
        assertEquals(200,r.statusCode(),r.body()); assertEquals("INVESTIGATING",json(r)["status"].asString())
        assertEquals(409,call("/api/v1/complaints/$id/supplement","POST",mapOf("description" to "Again"),customer).statusCode())
    }
    @Test fun `AC-06 refund confirmation automatically closes complaint`() {
        val id=complaint(); assertEquals(200,decide(id).statusCode())
        val rid=jdbc.queryForObject("select id from refund_request where complaint_id=?",UUID::class.java,UUID.fromString(id))!!
        assertEquals(200,call("/api/v1/admin/refund-requests/$rid/approve","POST",mapOf("amount" to 30000,"note" to "Approved"),admin).statusCode())
        val r=call("/api/v1/admin/refund-requests/$rid/confirm","POST",mapOf("bankTransactionId" to "BANK1","evidenceFileId" to imageId(admin),"transferredAt" to clock.time),admin)
        assertEquals(200,r.statusCode(),r.body()); assertEquals("CLOSED",json(call("/api/v1/complaints/$id",token=customer))["status"].asString())
    }
    @Test fun `AC-07 additional refund uses original payment and forfeits compensation`() {
        noShow(); jdbc.update("""insert into refund_request(id,created_at,updated_at,version,order_id,payment_id,reason,requested_amount,status,reason_code)
            values (?,now(),now(),0,?,?,'No show',42000,'PENDING','NO_SHOW_REFUND')""",UUID.randomUUID(),order,payment)
        val r=submit("NO_SHOW");assertEquals(201,r.statusCode(),r.body());val id=json(r)["id"].asString()
        val d=decide(id,18000,"APPROVED"); assertEquals(200,d.statusCode(),d.body())
        assertEquals(2,count("select count(*) from refund_request where payment_id=?",payment))
        assertEquals("FORFEITED",jdbc.queryForObject("select compensation_status from no_show_case",String::class.java))
    }
    @Test fun `AC-08 rejected complaint closes and releases dispute`() {
        noShow(); val r=submit("NO_SHOW"); val id=json(r)["id"].asString();assertEquals(200,decide(id,0,"REJECTED").statusCode())
        assertEquals("HELD",jdbc.queryForObject("select compensation_status from no_show_case",String::class.java))
        assertEquals("CLOSED",json(call("/api/v1/complaints/$id",token=customer))["status"].asString())
    }
    @Test fun `AC-09 over-refund immutable decision replay and premature close`() {
        val id=complaint(); assertEquals(409,decide(id,60001).statusCode());assertEquals(200,decide(id).statusCode())
        assertEquals(200,decide(id).statusCode());assertEquals(409,decide(id,20000).statusCode());assertEquals(409,decide(id,key="new").statusCode())
        assertEquals(409,call("/api/v1/admin/complaints/$id/close","POST",token=admin).statusCode())
        assertEquals(1,count("select count(*) from financial_event where type='COMPLAINT_RESOLVED'"))
    }
    @Test fun `AC-10 concurrent decisions serialize`() {
        val id=complaint();assertEquals(listOf(200,409),concurrently({decide(id,key="one")},{decide(id,key="two")}))
        assertEquals(1,count("select count(*) from refund_request where complaint_id=?",UUID.fromString(id)))
    }
    @Test fun `AC-11 ownership role and evidence authorization`() {
        val id=complaint();val other=user("other@example.test")
        assertEquals(401,submit(token=null).statusCode());assertEquals(404,call("/api/v1/complaints/$id",token=other).statusCode())
        assertEquals(403,call("/api/v1/admin/complaints",token=customer).statusCode())
        assertEquals(404,submit(type="OTHER",token=other).statusCode());assertEquals(400,submit(type="OTHER",key="foreign-file",files=listOf(imageId(other))).statusCode())
    }
    @Test fun `AC-02 null deadline and exact deadline remain timely`() {
        noShow();jdbc.update("update no_show_case set notification_received_at=null,dispute_deadline=null")
        val r=submit("NO_SHOW");assertEquals(201,r.statusCode(),r.body());assertTrue(json(r)["withinDisputeWindow"].asBoolean())
        assertEquals("DISPUTED",jdbc.queryForObject("select compensation_status from no_show_case",String::class.java))
    }
    @Test fun `AC-02 boundary and DECIDED complaints block release job`() {
        noShow();clock.time=clock.time.truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        jdbc.update("update no_show_case set dispute_deadline=?",java.sql.Timestamp.from(clock.time))
        val r=submit("NO_SHOW");assertEquals(201,r.statusCode(),r.body());assertTrue(json(r)["withinDisputeWindow"].asBoolean())
        // An approved platform resolution leaves store compensation held, protected while money is pending.
        jdbc.update("update complaint set status='DECIDED'");jdbc.update("update no_show_case set compensation_status='HELD'")
        assertEquals(0,compensation.releaseEligible(clock.time.plusSeconds(1)))
    }
    @Test fun `AC-08 rejection past deadline eligible unless another complaint remains active`() {
        noShow(true);jdbc.update("update no_show_case set compensation_status='DISPUTED'")
        val id=json(submit("NO_SHOW"))["id"].asString()
        assertEquals(200,decide(id,0,"REJECTED").statusCode())
        assertEquals("ELIGIBLE",jdbc.queryForObject("select compensation_status from no_show_case",String::class.java))
    }
    @Test fun `AC-08 another active complaint preserves disputed retention`() {
        noShow();val id=json(submit("NO_SHOW"))["id"].asString()
        assertEquals(201,submit("OTHER",key="another").statusCode())
        assertEquals(200,decide(id,0,"REJECTED").statusCode())
        assertEquals("DISPUTED",jdbc.queryForObject("select compensation_status from no_show_case",String::class.java))
    }
    @Test fun `AC-06 all source obligations must be fulfilled before auto close`() {
        val second=UUID.randomUUID()
        jdbc.update("update payment set received_amount=30000 where id=?",payment)
        jdbc.update("""insert into payment(id,created_at,updated_at,version,order_id,provider,provider_transaction_id,received_amount,currency,status,received_at,verified_at)
            values (?,now(),now(),0,?,'TEST','second-payment',30000,'VND','VERIFIED',now(),now())""",second,order)
        val id=complaint()
        val d=call("/api/v1/admin/complaints/$id/decide","POST",mapOf("outcome" to "APPROVED","responsibility" to "PLATFORM","reason" to "Full refund",
            "refundAllocations" to listOf(mapOf("paymentId" to payment,"amount" to 30000),mapOf("paymentId" to second,"amount" to 30000))),admin)
        assertEquals(200,d.statusCode(),d.body())
        val ids=jdbc.queryForList("select id from refund_request where complaint_id=? order by id",UUID::class.java,UUID.fromString(id))
        for((index,rid) in ids.withIndex()) {
            assertEquals(200,call("/api/v1/admin/refund-requests/$rid/approve","POST",mapOf("amount" to 30000,"note" to "Approved"),admin).statusCode())
            if(index==0) {
                assertEquals(200,call("/api/v1/admin/refund-requests/$rid/fail","POST",mapOf("note" to "Bank unavailable"),admin).statusCode())
                assertEquals("DECIDED",json(call("/api/v1/complaints/$id",token=customer))["status"].asString())
            }
            val r=call("/api/v1/admin/refund-requests/$rid/confirm","POST",mapOf("bankTransactionId" to "MULTI-$index","evidenceFileId" to imageId(admin),"transferredAt" to clock.time),admin)
            assertEquals(200,r.statusCode(),r.body())
            assertEquals(if(index==0) "DECIDED" else "CLOSED",json(call("/api/v1/complaints/$id",token=customer))["status"].asString())
        }
        assertEquals(2,count("select count(*) from ledger_entry where type='CUSTOMER_REFUND'"))
    }
    @Test fun `AC-09 invalid allocations and rejection money rollback without events`() {
        val id=complaint()
        fun decision(alloc: List<Map<String,Any>>,outcome: String="APPROVED",adjustment: Number=0)=call("/api/v1/admin/complaints/$id/decide","POST",
            mapOf("outcome" to outcome,"responsibility" to "STORE","reason" to "Check evidence","refundAllocations" to alloc,"storeAdjustmentAmount" to adjustment),admin)
        for(amount in listOf(0,-1,0.5)) assertEquals(400,decision(listOf(mapOf("paymentId" to payment,"amount" to amount))).statusCode())
        assertEquals(400,decision(listOf(mapOf("paymentId" to payment,"amount" to 100),mapOf("paymentId" to payment,"amount" to 100))).statusCode())
        assertEquals(400,decision(listOf(mapOf("paymentId" to UUID.randomUUID(),"amount" to 100))).statusCode())
        assertEquals(400,decision(emptyList(),"REJECTED",100).statusCode())
        assertEquals(400,decision(emptyList(),adjustment=1.5).statusCode())
        assertEquals(0,count("select count(*) from financial_event where type='COMPLAINT_RESOLVED'"))
        assertEquals("OPEN",json(call("/api/v1/complaints/$id",token=customer))["status"].asString())
    }
    @Test fun `AC-10 different complaints on same payment cannot reserve more than received`() {
        val id=complaint();val other=json(submit("OTHER",key="other-type"))["id"].asString()
        assertEquals(listOf(200,409),concurrently({decide(id,40000,key="one")},{decide(other,40000,key="two")}))
        assertEquals(1,count("select count(*) from refund_request where payment_id=?",payment))
    }
    @Test fun `AC-01 concurrent submission creates one complaint and evidence is single use`() {
        assertEquals(listOf(200,201),concurrently({submit()},{submit()}))
        assertEquals(1,count("select count(*) from complaint"))
        val f=imageId(customer);assertEquals(201,submit("OTHER",key="other-type",files=listOf(f)).statusCode())
        assertEquals(400,submit("PAYMENT",key="reused-evidence",files=listOf(f)).statusCode())
    }

    @Test fun `AC-09 approving an existing obligation cannot consume another complaint reservation`() {
        val first=complaint();assertEquals(200,decide(first,60000,"APPROVED").statusCode())
        val rid=jdbc.queryForObject("select id from refund_request where complaint_id=?",UUID::class.java,UUID.fromString(first))!!
        assertEquals(200,call("/api/v1/admin/refund-requests/$rid/approve","POST",mapOf("amount" to 30000,"note" to "Partial transfer"),admin).statusCode())
        val second=json(submit("OTHER",key="another"))["id"].asString();assertEquals(200,decide(second,30000,key="another-decision").statusCode())
        val r=call("/api/v1/admin/refund-requests/$rid/approve","POST",mapOf("amount" to 60000,"note" to "Cannot consume reserved funds"),admin)
        assertEquals(400,r.statusCode(),r.body())
    }

}
