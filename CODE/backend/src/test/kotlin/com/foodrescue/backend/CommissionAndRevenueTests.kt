// AC-01..10 (T8.3): Financial posting and owner reports through HTTP and PostgreSQL.
package com.foodrescue.backend
import com.foodrescue.backend.support.StoreTestFixtures
import com.foodrescue.backend.payment.gateway.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import java.util.UUID

@Import(PickupClockConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommissionAndRevenueTests : StoreTestFixtures() {
    @Autowired lateinit var clock: PickupTestClock
    @Autowired lateinit var gateway: PaymentGateway
    @Autowired lateinit var posting: com.foodrescue.backend.finance.service.LedgerPostingService
    @Autowired lateinit var compensation: com.foodrescue.backend.pickup.service.NoShowCompensationService
    lateinit var owner: String; lateinit var customer: String; lateinit var admin: String
    lateinit var storeId: String; lateinit var order: UUID; lateinit var payment: UUID
    val base get() = "/api/v1/stores/$storeId/finance"
    @BeforeEach fun setup() {
        clock.time=fixtureNow().truncatedTo(java.time.temporal.ChronoUnit.MICROS); (gateway as FakePaymentGateway).reset()
        owner=user("owner@example.test"); admin=makeAdmin("admin@example.test")
        storeId=store(owner,admin); openAllWeek(storeId,owner)
        val batch=openOffer(storeId,owner,boxes=2); customer=user("customer@example.test")
        order=UUID.fromString(json(placeOrder(customer,batch,2))["id"].asString())
        val code=json(call("/api/v1/orders/$order/payment","POST",token=customer))["providerOrderCode"].asLong()
        assertEquals(200,call("/api/v1/payments/webhooks/payos","POST",(gateway as FakePaymentGateway).webhookFor(code,60000,"paid")).statusCode())
        payment=jdbc.queryForObject("select id from payment where order_id=?",UUID::class.java,order)!!
    }
    fun completed(total: Int=60000, rate: String="0.10") {
        if(total!=60000 || rate!="0.10") {
            val id=UUID.randomUUID()
            jdbc.update("""insert into customer_order select (jsonb_populate_record(null::customer_order,to_jsonb(o)||jsonb_build_object(
                'id',cast(? as text),'order_code',cast(? as text),'quantity',1,'unit_price',cast(? as numeric),'total_amount',cast(? as numeric),
                'commission_rate',cast(? as numeric),'pickup_code_hash',null,'pickup_code_encrypted',null,'pickup_code_key_id',null,'idempotency_key',null))).*
                from customer_order o where o.id=?""",id.toString(),"ROUND-$id",total,total,rate,order)
            order=id
            // Seed a verified receipt for this isolated rounding fixture; accepted terms stay immutable.
            payment=UUID.randomUUID()
            jdbc.update("""insert into payment(id,created_at,updated_at,version,order_id,provider,provider_transaction_id,received_amount,currency,status,verified_at,received_at)
                values (?,now(),now(),0,?,'TEST',?,?,'VND','VERIFIED',now(),now())""",payment,order,"ROUND-$id",total)
            event("PAYMENT_RECEIVED")
            jdbc.update("""insert into ledger_entry(id,created_at,event_id,store_id,type,amount,currency,occurred_at,business_key)
                select ?,now(),id,?,'PAYMENT_RECEIPT',?,'VND',occurred_at,? from financial_event where business_key=?""",
                UUID.randomUUID(),UUID.fromString(storeId),total,"PAYMENT_RECEIPT:$id","PAYMENT_RECEIVED:$id")
        }
        jdbc.update("update customer_order set status='COMPLETED',completed_at=? where id=?",java.sql.Timestamp.from(clock.time),order)
        event("ORDER_COMPLETED")
    }
    fun event(type: String, caseId: UUID?=null) {
        jdbc.update("insert into financial_event(id,created_at,order_id,type,occurred_at,business_key,no_show_case_id,payment_id) values (?,now(),?,?,?, ?,?,?)",UUID.randomUUID(),order,type,java.sql.Timestamp.from(clock.time),"$type:$order",caseId,if(type=="PAYMENT_RECEIVED") payment else null)
    }
    fun reconcile()=call("/api/v1/admin/finance/ledger/reconcile","POST",token=admin)
    fun ledger()=json(call("$base/orders/$order/ledger",token=owner))
    fun noShow(): UUID {
        jdbc.update("update customer_order set status='NO_SHOW' where id=?",order)
        val id=UUID.randomUUID()
        jdbc.update("insert into no_show_case(id,created_at,updated_at,version,order_id,verified_by_id,verified_at,evidence,retained_amount,refund_amount,compensation_status) values (?,now(),now(),0,?,?,now(),'Absent',18000,42000,'HELD')",id,order,accountId("owner@example.test"))
        event("NO_SHOW_CONFIRMED",id); return id
    }
    fun complaint(): String {
        val r=callWithHeader("/api/v1/complaints","POST",mapOf("orderId" to order,"type" to "FOOD_SAFETY","description" to "Unsafe food"),customer,"Idempotency-Key","complaint")
        assertEquals(201,r.statusCode(),r.body()); return json(r)["id"].asString()
    }
    @Test fun `AC-01 actual total and HALF_UP rounding`() {
        for ((total,rate,expected) in listOf(Triple(10005,"0.15",1501),Triple(10003,"0.15",1500),Triple(10010,"0.05",501))) {
            if(total!=10005) { jdbc.execute("TRUNCATE financial_event CASCADE") }
            completed(total,rate); val r=reconcile(); assertEquals(200,r.statusCode(),r.body())
            val l=ledger(); assertEquals(expected,l["grossCommission"].asInt()); assertEquals(total,l["netAllocated"].asInt());assertTrue(l["balanced"].asBoolean())
        }
    }
    @Test fun `AC-02 non-completed orders have zero allocation`() {
        for(status in listOf("PENDING_PAYMENT","EXPIRED","CANCELLED")) {
            jdbc.update("update customer_order set status=? where id=?",status,order)
            assertEquals(200,reconcile().statusCode()); assertEquals(0,ledger()["netCommission"].asInt())
        }
    }
    @Test fun `AC-03 04 no-show held eligible and forfeited reports`() {
        noShow(); posting.postPendingEvents(); assertEquals(200,reconcile().statusCode()); assertEquals(0,ledger()["netCommission"].asInt())
        assertEquals(18000,json(call("$base/entitlements",token=owner))["summary"]["totalHeldAmount"].asInt())
        jdbc.update("update no_show_case set notification_received_at=?,dispute_deadline=?",java.sql.Timestamp.from(clock.time),java.sql.Timestamp.from(clock.time.plusSeconds(48*3600)))
        assertEquals(0,compensation.releaseEligible(clock.time.plusSeconds(48*3600-1)))
        assertEquals(1,compensation.releaseEligible(clock.time.plusSeconds(48*3600)))
        assertEquals(18000,json(call("$base/entitlements",token=owner))["summary"]["totalEligibleToSettleAmount"].asInt())
        assertEquals(200,reconcile().statusCode()); assertEquals(1,count("select count(*) from ledger_entry where type='NO_SHOW_COMPENSATION'"))
    }
    @Test fun `AC-06 07 complaint holds revenue and refund posts linked adjustments`() {
        completed(); reconcile(); val id=complaint()
        assertEquals(54000,json(call("$base/entitlements",token=owner))["summary"]["totalHeldAmount"].asInt())
        val decision=call("/api/v1/admin/complaints/$id/decide","POST",mapOf("outcome" to "PARTIAL","responsibility" to "STORE","reason" to "Confirmed","refundAllocations" to listOf(mapOf("paymentId" to payment,"amount" to 30000))),admin)
        assertEquals(200,decision.statusCode(),decision.body()); reconcile(); assertEquals(0,ledger()["commissionAdjustment"].asInt())
        val rid=jdbc.queryForObject("select id from refund_request where complaint_id=?",UUID::class.java,UUID.fromString(id))!!
        assertEquals(200,call("/api/v1/admin/refund-requests/$rid/approve","POST",mapOf("amount" to 30000,"note" to "Approved"),admin).statusCode())
        val r=call("/api/v1/admin/refund-requests/$rid/confirm","POST",mapOf("bankTransactionId" to "T83-REFUND","evidenceFileId" to imageId(admin),"transferredAt" to clock.time),admin)
        assertEquals(200,r.statusCode(),r.body()); assertTrue(ledger()["balanced"].asBoolean()); assertEquals(-3000,ledger()["commissionAdjustment"].asInt())
        assertEquals(2,count("select count(*) from ledger_entry where type='ADJUSTMENT' and original_entry_id is not null"))
    }
    @Test fun `AC-08 parallel reconciliation and replay are idempotent`() {
        completed(); assertEquals(listOf(200,200),concurrently({reconcile()},{reconcile()}))
        repeat(3) {assertEquals(200,reconcile().statusCode())}
        assertEquals(2,count("select count(*) from ledger_entry where type in ('STORE_PAYABLE','PLATFORM_COMMISSION')"))
    }
    @Test fun `AC-09 filters pagination empty and read-only reports`() {
        completed(); reconcile()
        assertEquals(1,json(call("$base/revenue",token=owner))["summary"]["completedOrderCount"].asInt())
        assertEquals(0,json(call("$base/revenue?orderCode=missing",token=owner))["orders"]["totalElements"].asInt())
        assertEquals(400,call("$base/revenue?from=2027-01-01T00:00:00Z&to=2026-01-01T00:00:00Z",token=owner).statusCode())
        assertEquals(400,call("$base/entitlements?status=INVALID",token=owner).statusCode())
        assertEquals(400,call("$base/revenue?size=101",token=owner).statusCode())
        assertEquals(400,call("$base/revenue?sort=passwordHash",token=owner).statusCode())
        val from=clock.time.plusSeconds(1)
        assertEquals(0,json(call("$base/revenue?from=$from",token=owner))["summary"]["completedOrderCount"].asInt())
        val second=json(call("$base/revenue?page=1&size=1",token=owner))
        assertEquals(0,second["orders"]["content"].size());assertEquals(1,second["summary"]["completedOrderCount"].asInt())
        assertEquals(0,json(call("$base/entitlements?status=HELD",token=owner))["items"]["totalElements"].asInt())
        assertEquals(200,call("/api/v1/admin/finance/orders/$order/ledger",token=admin).statusCode())
    }
    @Test fun `AC-10 owners only and admin authorization`() {
        val staff=staffWith(storeId,owner,"staff@example.test",listOf("BOX_MANAGE","PICKUP_HANDLE","NO_SHOW_CONFIRM","MENU_VIEW","BOX_PACK","ORDER_VIEW"))
        val other=user("other@example.test")
        for(path in listOf("$base/revenue","$base/entitlements","$base/orders/$order/ledger")) {
            assertEquals(401,call(path).statusCode())
            for(token in listOf(customer,other,staff)) assertEquals(403,call(path,token=token).statusCode())
        }
        assertEquals(403,call("/api/v1/admin/finance/orders/$order/ledger",token=owner).statusCode())
        assertEquals(403,call("/api/v1/admin/finance/ledger/reconcile","POST",token=owner).statusCode())
    }
    @Test fun `AC-02 fully refunded cancellation balances with zero commission`() {
        jdbc.update("update customer_order set status='CANCELLED' where id=?",order)
        jdbc.update("insert into refund_request(id,created_at,updated_at,version,order_id,payment_id,reason,requested_amount,status,reason_code) values (?,now(),now(),0,?,?,'Cancelled',60000,'PENDING','CUSTOMER_CANCEL')",UUID.randomUUID(),order,payment)
        refundAll(60000);assertTrue(ledger()["balanced"].asBoolean());assertEquals(0,ledger()["netCommission"].asInt())
    }
    fun refundAll(amount: Int) {
        val ids=jdbc.queryForList("select id from refund_request where status='PENDING' order by created_at",UUID::class.java)
        ids.forEachIndexed { index,rid ->
            val value=jdbc.queryForObject("select requested_amount from refund_request where id=?",Int::class.java,rid)!!
            assertEquals(200,call("/api/v1/admin/refund-requests/$rid/approve","POST",mapOf("amount" to value,"note" to "Approved"),admin).statusCode())
            val r=call("/api/v1/admin/refund-requests/$rid/confirm","POST",mapOf("bankTransactionId" to "REF-$index","evidenceFileId" to imageId(admin),"transferredAt" to clock.time),admin)
            assertEquals(200,r.statusCode(),r.body())
        }
        assertEquals(amount,ledger()["refundedVerified"].asInt())
    }
    @Test fun `AC-01 synchronous completion posts in handover transaction`() {
        clock.time=jdbc.queryForObject("select pickup_starts_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant()
        owner=login("owner@example.test")["accessToken"].asString()
        val started=callWithHeader("/api/v1/stores/$storeId/orders/$order/pickup-sessions/start","POST",null,owner,"Idempotency-Key","pickup")
        assertEquals(201,started.statusCode(),started.body());val sid=json(started)["id"].asString()
        val done=callWithHeader("/api/v1/stores/$storeId/pickup-sessions/$sid/complete","POST",null,owner,"Idempotency-Key","pickup")
        assertEquals(200,done.statusCode(),done.body());assertTrue(ledger()["balanced"].asBoolean())
        assertEquals(2,count("select count(*) from ledger_entry where type in ('STORE_PAYABLE','PLATFORM_COMMISSION')"))
    }
    @Test fun `AC-03 05 synchronous no-show forfeiture waits for refund then reverses compensation`() {
        clock.time=jdbc.queryForObject("select pickup_ends_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant()
        owner=login("owner@example.test")["accessToken"].asString();customer=login("customer@example.test")["accessToken"].asString();admin=login("admin@example.test")["accessToken"].asString()
        val r=callWithHeader("/api/v1/stores/$storeId/orders/$order/no-show","POST",mapOf("storeOpenedOnTime" to true,"boxesReadyAndCompliant" to true,"evidence" to "Absent"),owner,"Idempotency-Key","noshow")
        assertEquals(201,r.statusCode(),r.body());assertEquals(30000,ledger()["grossNoShowCompensation"].asInt())
        val c=call("/api/v1/complaints","POST",mapOf("orderId" to order,"type" to "NO_SHOW","description" to "Store was closed"),customer)
        assertEquals(201,c.statusCode(),c.body());val id=json(c)["id"].asString()
        val d=call("/api/v1/admin/complaints/$id/decide","POST",mapOf("outcome" to "APPROVED","responsibility" to "STORE","reason" to "Confirmed","refundAllocations" to listOf(mapOf("paymentId" to payment,"amount" to 30000))),admin)
        assertEquals(200,d.statusCode(),d.body());assertEquals(0,ledger()["noShowAdjustment"].asInt())
        assertEquals(30000,json(call("$base/entitlements",token=owner))["summary"]["totalHeldAmount"].asInt())
        refundAll(60000);assertEquals(-30000,ledger()["noShowAdjustment"].asInt());assertTrue(ledger()["balanced"].asBoolean())
        assertEquals(200,reconcile().statusCode());assertEquals(1,count("select count(*) from ledger_entry where type='ADJUSTMENT'"))
    }
    @Test fun `AC-06 platform responsibility and explicit adjustments preserve balance`() {
        completed();reconcile();val id=complaint()
        val d=call("/api/v1/admin/complaints/$id/decide","POST",mapOf("outcome" to "PARTIAL","responsibility" to "PLATFORM","reason" to "Platform fault","refundAllocations" to listOf(mapOf("paymentId" to payment,"amount" to 10000))),admin)
        assertEquals(200,d.statusCode(),d.body());refundAll(10000)
        assertEquals(-10000,ledger()["commissionAdjustment"].asInt());assertEquals(0,ledger()["storeAdjustment"].asInt());assertTrue(ledger()["balanced"].asBoolean())
    }
    @Test fun `AC-06 explicit zero refund adjustments post immediately and are idempotent`() {
        completed();reconcile();val id=complaint()
        val d=call("/api/v1/admin/complaints/$id/decide","POST",mapOf("outcome" to "PARTIAL","responsibility" to "SHARED","reason" to "Reallocate","storeAdjustmentAmount" to 1000,"commissionAdjustmentAmount" to -1000),admin)
        assertEquals(200,d.statusCode(),d.body());assertEquals(-1000,ledger()["commissionAdjustment"].asInt());assertEquals(1000,ledger()["storeAdjustment"].asInt())
        assertTrue(ledger()["balanced"].asBoolean());reconcile();assertEquals(2,count("select count(*) from ledger_entry where type='ADJUSTMENT'"))
    }

}
