// AC-01..11 (T8.4): Settlement and manual payout invariants over HTTP/PostgreSQL.
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
class SettlementAndPayoutTests : StoreTestFixtures() {
    @Autowired lateinit var clock: PickupTestClock
    @Autowired lateinit var gateway: PaymentGateway
    @Autowired lateinit var dataSource: javax.sql.DataSource
    lateinit var owner: String; lateinit var admin: String; lateinit var customer: String
    lateinit var storeId: String; lateinit var order: UUID
    val base get()="/api/v1/stores/$storeId/finance"
    val management="/api/v1/admin/finance"
    @BeforeEach fun setup(info: TestInfo) {
        clock.time=fixtureNow().truncatedTo(java.time.temporal.ChronoUnit.MICROS);(gateway as FakePaymentGateway).reset()
        owner=user("owner@example.test");admin=makeAdmin("admin@example.test");storeId=store(owner,admin);openAllWeek(storeId,owner)
        val batch=openOffer(storeId,owner,boxes=2);customer=user("customer@example.test");order=UUID.fromString(json(placeOrder(customer,batch,2))["id"].asString())
        val code=json(call("/api/v1/orders/$order/payment","POST",token=customer))["providerOrderCode"].asLong()
        assertEquals(200,call("/api/v1/payments/webhooks/payos","POST",(gateway as FakePaymentGateway).webhookFor(code,60000,"paid")).statusCode())
        if(info.displayName.contains("no-show")) return
        jdbc.update("update customer_order set status='COMPLETED',completed_at=? where id=?",java.sql.Timestamp.from(clock.time),order)
        jdbc.update("insert into financial_event(id,created_at,order_id,type,occurred_at,business_key) values (?,now(),?,'ORDER_COMPLETED',?,?)",UUID.randomUUID(),order,java.sql.Timestamp.from(clock.time),"ORDER_COMPLETED:$order")
        assertEquals(200,call("$management/ledger/reconcile","POST",token=admin).statusCode())
    }
    fun account(number: String="1234567890")=call("$base/payout-account","PUT",mapOf("bankCode" to "VCB","accountNumber" to number,"accountHolderName" to "NGUYEN VAN A"),owner)
    fun verified(): String {
        val a=account();assertEquals(200,a.statusCode(),a.body());val id=json(a)["id"].asString()
        val r=call("$management/stores/$storeId/payout-accounts/$id/verify","POST",mapOf("expectedVersion" to 1,"note" to "Checked"),admin)
        assertEquals(200,r.statusCode(),r.body());return id
    }
    fun draft(start: java.time.Instant=clock.time.minusSeconds(60),end: java.time.Instant=clock.time.plusSeconds(1))=call("$management/settlements/draft","POST",mapOf("storeId" to storeId,"periodStartsAt" to start,"periodEndsAt" to end),admin)
    fun confirmed(): String {val d=draft();assertEquals(200,d.statusCode(),d.body());val id=json(d)["id"].asString();val c=call("$management/settlements/$id/confirm","POST",token=admin);assertEquals(200,c.statusCode(),c.body());return id}
    fun payout(s: String,a: String,amount: Int=54000,ref: String="BANK-001",status: String="VERIFIED",version: Int=1,evidence: Boolean=true)=call("$management/settlements/$s/payouts","POST",mapOf("payoutAccountId" to a,"expectedAccountVersion" to version,"amount" to amount,"transferReference" to ref,"status" to status,"transferredAt" to if(evidence) clock.time else null,"evidenceFileId" to if(evidence) imageId(admin) else null),admin)
    fun detail(s: String)=json(call("$base/settlements/$s",token=owner))
    fun adjustment(amount: Int) {
        val event=UUID.randomUUID();jdbc.update("insert into financial_event(id,created_at,order_id,type,occurred_at,business_key) values (?,now(),?,'ADJUSTMENT',?,?)",event,order,java.sql.Timestamp.from(clock.time.plusSeconds(2)),"ADJ-$event")
        for((type,value) in listOf("STORE_PAYABLE" to amount,"PLATFORM_COMMISSION" to -amount)) jdbc.update("""insert into ledger_entry(id,created_at,event_id,store_id,type,amount,currency,original_entry_id,occurred_at,business_key)
            select ?,now(),?,store_id,'ADJUSTMENT',?,'VND',id,?,? from ledger_entry where type=? and store_id=?""",UUID.randomUUID(),event,value,java.sql.Timestamp.from(clock.time.plusSeconds(2)),"ADJ-$event-$type",type,UUID.fromString(storeId))
    }
    @Test fun `AC-01 account versions encrypted and owner responses masked`() {
        val a=account();assertEquals(200,a.statusCode(),a.body());assertFalse(a.body().contains("1234567890"));assertEquals("******7890",json(a)["accountNumberMasked"].asString())
        val cipher=jdbc.queryForObject("select account_number_ciphertext from store_payout_account",String::class.java)!!;assertFalse(cipher.contains("1234567890"))
        assertEquals(200,account("9876543210").statusCode());assertEquals(2,count("select count(*) from store_payout_account"));assertEquals(1,count("select count(*) from store_payout_account where effective_until is null"))
        val reveal=call("$management/stores/$storeId/payout-accounts/active-for-payout",token=admin);assertEquals(200,reveal.statusCode(),reveal.body());assertEquals("9876543210",json(reveal)["accountNumber"].asString())
        assertEquals(1,count("select count(*) from audit_log where action='PAYOUT_ACCOUNT_REVEALED'"));assertEquals(0,count("select count(*) from audit_log where after_state like '%9876543210%'"))
    }
    @Test fun `AC-02 verification rejects stale version and notifies exactly once`() {
        val a=json(account())["id"].asString();val path="$management/stores/$storeId/payout-accounts/$a/verify"
        assertEquals(409,call(path,"POST",mapOf("expectedVersion" to 2),admin).statusCode())
        repeat(2) {val r=call(path,"POST",mapOf("expectedVersion" to 1),admin);assertEquals(200,r.statusCode(),r.body())}
        assertEquals(1,count("select count(*) from audit_log where action='PAYOUT_ACCOUNT_VERIFIED'"))
        account("9876543210");assertEquals(409,call(path,"POST",mapOf("expectedVersion" to 1),admin).statusCode())
    }
    @Test fun `AC-03 draft includes eligible entries rebuilds and isolates complaints`() {
        val d=draft();assertEquals(200,d.statusCode(),d.body());val id=json(d)["id"].asString();assertEquals(54000,json(d)["totalAmount"].asInt());assertEquals(1,json(d)["lines"].size())
        val complaint=call("/api/v1/complaints","POST",mapOf("orderId" to order,"type" to "FOOD_SAFETY","description" to "Unsafe food"),customer);assertEquals(201,complaint.statusCode(),complaint.body())
        assertEquals(409,call("$management/settlements/$id/confirm","POST",token=admin).statusCode())
        val rebuilt=draft();assertEquals(200,rebuilt.statusCode(),rebuilt.body());assertEquals(id,json(rebuilt)["id"].asString());assertEquals(0,json(rebuilt)["lines"].size());assertTrue(json(rebuilt)["heldItems"].size()>0)
    }
    @Test fun `AC-04 07 confirmation concurrent idempotent immutable and no duplicate settlement lines`() {
        val id=json(draft())["id"].asString();assertEquals(listOf(200,200),concurrently({call("$management/settlements/$id/confirm","POST",token=admin)},{call("$management/settlements/$id/confirm","POST",token=admin)}))
        assertThrows(Exception::class.java) {jdbc.update("delete from settlement_line where settlement_id=?",UUID.fromString(id))}
        val next=draft(clock.time.plusSeconds(1),clock.time.plusSeconds(4));assertEquals(200,next.statusCode(),next.body());assertEquals(0,json(next)["lines"].size())
        assertEquals(1,count("select count(*) from settlement_line"));assertEquals(1,count("select count(*) from audit_log where action='SETTLEMENT_CONFIRMED'"))
    }
    @Test fun `AC-05 08 negative adjustment enters next period traces origin and blocks payout`() {
        val a=verified();val first=confirmed();adjustment(-60000)
        val d=draft(clock.time.plusSeconds(1),clock.time.plusSeconds(4));assertEquals(200,d.statusCode(),d.body());val second=json(d)["id"].asString()
        assertEquals(60000,json(d)["recoveryAmount"].asInt());assertEquals(first,json(d)["lines"][0]["originalSettlementId"].asString())
        assertEquals(200,call("$management/settlements/$second/confirm","POST",token=admin).statusCode());assertEquals(409,payout(second,a,1).statusCode())
    }
    @Test fun `AC-06 changed draft fingerprint and imbalanced ledger block confirmation`() {
        val id=json(draft(end=clock.time.plusSeconds(3)))["id"].asString();adjustment(-1000)
        assertEquals(409,call("$management/settlements/$id/confirm","POST",token=admin).statusCode())
        assertEquals(200,draft(end=clock.time.plusSeconds(3)).statusCode());assertEquals(200,call("$management/settlements/$id/confirm","POST",token=admin).statusCode())
        // Missing commission is an exception even if the store payable exists.
        val event=UUID.randomUUID();jdbc.update("insert into financial_event(id,created_at,order_id,type,occurred_at,business_key) values (?,now(),?,'ADJUSTMENT',?,?)",event,order,java.sql.Timestamp.from(clock.time),"BAD-$event")
        jdbc.update("insert into ledger_entry(id,created_at,event_id,store_id,type,amount,currency,occurred_at) values (?,now(),?,?,'PLATFORM_COMMISSION',1,'VND',?)",UUID.randomUUID(),event,UUID.fromString(storeId),java.sql.Timestamp.from(clock.time))
        val bad=draft(clock.time.plusSeconds(3),clock.time.plusSeconds(5));assertEquals(200,bad.statusCode(),bad.body());assertTrue(json(bad)["unresolvedExceptions"].size()>0)
        assertEquals(409,call("$management/settlements/${json(bad)["id"].asString()}/confirm","POST",token=admin).statusCode())
    }
    @Test fun `AC-09 unverified or changed recipient blocks payout and pending verify`() {
        val s=confirmed();val a=json(account())["id"].asString();assertEquals(409,payout(s,a).statusCode())
        assertEquals(200,call("$management/stores/$storeId/payout-accounts/$a/verify","POST",mapOf("expectedVersion" to 1),admin).statusCode())
        val pending=payout(s,a,10000,status="PENDING");assertEquals(201,pending.statusCode(),pending.body());val id=json(pending)["id"].asString()
        account("9876543210");assertTrue(detail(s)["payouts"][0]["recipientSnapshot"].asString().contains("******7890"));assertEquals(1,detail(s)["payouts"][0]["payoutAccountVersion"].asInt());assertEquals(409,payout(s,a,10000,ref="BANK-002").statusCode())
        assertEquals(409,call("$management/payouts/$id/verify","POST",mapOf("transferredAt" to clock.time,"evidenceFileId" to imageId(admin)),admin).statusCode())
    }
    @Test fun `AC-10 partial pending failed verified payouts reserve funds and prevent overpayment`() {
        val a=verified();val s=confirmed();val pending=payout(s,a,20000,status="PENDING",evidence=false);assertEquals(201,pending.statusCode(),pending.body());val p=json(pending)["id"].asString()
        assertEquals(409,payout(s,a,40000,ref="BANK-002").statusCode());assertEquals(409,payout(s,a,1).statusCode())
        assertEquals(200,call("$management/payouts/$p/fail","POST",mapOf("reason" to "Bank rejected"),admin).statusCode())
        assertEquals(201,payout(s,a,20000,ref="BANK-003").statusCode());assertEquals("PARTIALLY_PAID",detail(s)["status"].asString())
        val rest=payout(s,a,34000,ref="BANK-004",status="PENDING",evidence=false);assertEquals(201,rest.statusCode(),rest.body())
        assertEquals(400,call("$management/payouts/${json(rest)["id"].asString()}/verify","POST",mapOf("transferredAt" to clock.time),admin).statusCode())
        assertEquals(200,call("$management/payouts/${json(rest)["id"].asString()}/verify","POST",mapOf("transferredAt" to clock.time,"evidenceFileId" to imageId(admin)),admin).statusCode())
        assertEquals("PAID",detail(s)["status"].asString());assertEquals(0,detail(s)["remainingPayableAmount"].asInt())
    }
    @Test fun `AC-10 concurrent payouts serialize amount and global reference`() {
        val a=verified();val s=confirmed();assertEquals(listOf(201,409),concurrently({payout(s,a,40000,ref="BANK-A")},{payout(s,a,40000,ref="BANK-B")}))
        assertEquals(40000,detail(s)["verifiedPaidAmount"].asInt())
    }
    @Test fun `AC-11 owner admin queries evidence and staff outsider access`() {
        val a=verified();val s=confirmed();val r=payout(s,a);assertEquals(201,r.statusCode(),r.body());val file=json(r)["evidenceFileIds"][0].asString()
        for(token in listOf(owner,admin)) assertEquals(200,download("/api/v1/files/$file",token).statusCode())
        val staff=staffWith(storeId,owner,"staff@example.test",listOf("MENU_VIEW","BOX_PACK","BOX_MANAGE","PICKUP_HANDLE","ORDER_VIEW","NO_SHOW_CONFIRM"));val other=user("other@example.test")
        for(token in listOf(staff,other,customer)) {
            for(path in listOf("$base/settlements","$base/payout-accounts","$base/settlements/$s")) assertEquals(403,call(path,token=token).statusCode())
            assertEquals(403,call("$base/payout-account","PUT",mapOf("bankCode" to "VCB","accountNumber" to "1111222233","accountHolderName" to "FORBIDDEN"),token).statusCode())
            assertEquals(403,download("/api/v1/files/$file",token).statusCode())
        }
        assertEquals(404,call("$base/settlements/${UUID.randomUUID()}",token=owner).statusCode())
        assertEquals(403,call("$management/stores/$storeId/payout-accounts/active-for-payout",token=owner).statusCode())
        assertEquals(401,call("$base/settlements").statusCode());assertEquals(403,call("$management/settlements",token=owner).statusCode())
        assertEquals(1,json(call("$base/settlements?status=PAID",token=owner))["totalElements"].asInt())
        assertEquals(400,call("$base/settlements?sort=passwordHash",token=owner).statusCode())
    }
    @Test fun `AC-03 07 no-show held then eligible confirms SETTLED and sends one notification`() {
        clock.time=jdbc.queryForObject("select pickup_ends_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant()
        owner=login("owner@example.test")["accessToken"].asString();admin=login("admin@example.test")["accessToken"].asString()
        val confirmed=callWithHeader("/api/v1/stores/$storeId/orders/$order/no-show","POST",mapOf("storeOpenedOnTime" to true,"boxesReadyAndCompliant" to true,"evidence" to "Absent"),owner,"Idempotency-Key","NOSHOW-84")
        assertEquals(201,confirmed.statusCode(),confirmed.body())
        val d=draft();assertEquals(200,d.statusCode(),d.body());assertEquals(0,json(d)["lines"].size());assertEquals(1,json(d)["heldItems"].size())
        val refund=jdbc.queryForObject("select id from refund_request where order_id=?",UUID::class.java,order)!!
        assertEquals(200,call("/api/v1/admin/refund-requests/$refund/approve","POST",mapOf("amount" to 30000,"note" to "Approved"),admin).statusCode())
        val paid=call("/api/v1/admin/refund-requests/$refund/confirm","POST",mapOf("bankTransactionId" to "NOSHOW-REFUND","transferredAt" to clock.time,"evidenceFileId" to imageId(admin)),admin)
        assertEquals(200,paid.statusCode(),paid.body())
        jdbc.update("update no_show_case set compensation_status='ELIGIBLE' where order_id=?",order)
        val rebuilt=draft();assertEquals(30000,json(rebuilt)["totalAmount"].asInt());val id=json(rebuilt)["id"].asString()
        repeat(2) {val result=call("$management/settlements/$id/confirm","POST",token=admin);assertEquals(200,result.statusCode(),result.body())}
        assertEquals("SETTLED",jdbc.queryForObject("select compensation_status from no_show_case where order_id=?",String::class.java,order))
        assertEquals(1,count("select count(*) from notification where event_code='SETTLEMENT_CONFIRMED'"))
    }
    @Test fun `AC-05 08 recovery carries forward and is consumed only once`() {
        val originalTime=clock.time;val account=verified();confirmed();adjustment(-60000)
        fun period(start: Long,end: Long): String {
            val d=draft(originalTime.plusSeconds(start),originalTime.plusSeconds(end));assertEquals(200,d.statusCode(),d.body());val id=json(d)["id"].asString()
            val c=call("$management/settlements/$id/confirm","POST",token=admin);assertEquals(200,c.statusCode(),c.body());return id
        }
        period(1,3)
        clock.time=originalTime.plusSeconds(4);adjustment(20000);val third=period(3,7)
        assertEquals(60000,detail(third)["carriedOverRecoveryAmount"].asInt());assertEquals(0,detail(third)["payableAmount"].asInt());assertEquals(409,payout(third,account,1).statusCode())
        clock.time=originalTime.plusSeconds(8);adjustment(50000);val fourth=period(7,11)
        assertEquals(40000,detail(fourth)["carriedOverRecoveryAmount"].asInt());assertEquals(10000,detail(fourth)["payableAmount"].asInt())
        assertEquals(409,payout(fourth,account,10001).statusCode());val result=payout(fourth,account,10000,ref="RECOVERY-CAP")
        assertEquals(201,result.statusCode(),result.body());assertEquals("PAID",detail(fourth)["status"].asString())
    }

    @Test fun `AC-10 transfer reference is unique across stores racing concurrently`() {
        val firstAccount=verified();val first=confirmed()
        val secondOwner=user("owner2@example.test");val secondStore=store(secondOwner,admin);openAllWeek(secondStore,secondOwner)
        val batch=openOffer(secondStore,secondOwner,ownerEmail="owner2@example.test",boxes=2);val secondOrder=UUID.fromString(json(placeOrder(customer,batch,2))["id"].asString())
        val code=json(call("/api/v1/orders/$secondOrder/payment","POST",token=customer))["providerOrderCode"].asLong()
        assertEquals(200,call("/api/v1/payments/webhooks/payos","POST",(gateway as FakePaymentGateway).webhookFor(code,60000,"paid-second")).statusCode())
        jdbc.update("update customer_order set status='COMPLETED',completed_at=? where id=?",java.sql.Timestamp.from(clock.time),secondOrder)
        jdbc.update("insert into financial_event(id,created_at,order_id,type,occurred_at,business_key) values (?,now(),?,'ORDER_COMPLETED',?,?)",UUID.randomUUID(),secondOrder,java.sql.Timestamp.from(clock.time),"ORDER_COMPLETED:$secondOrder")
        assertEquals(200,call("$management/ledger/reconcile","POST",token=admin).statusCode())
        val d=call("$management/settlements/draft","POST",mapOf("storeId" to secondStore,"periodStartsAt" to clock.time.minusSeconds(60),"periodEndsAt" to clock.time.plusSeconds(1)),admin)
        assertEquals(200,d.statusCode(),d.body());val second=json(d)["id"].asString();assertEquals(200,call("$management/settlements/$second/confirm","POST",token=admin).statusCode())
        val a=call("/api/v1/stores/$secondStore/finance/payout-account","PUT",mapOf("bankCode" to "VCB","accountNumber" to "9876543210","accountHolderName" to "SECOND OWNER"),secondOwner)
        assertEquals(200,a.statusCode(),a.body());val secondAccount=json(a)["id"].asString()
        assertEquals(200,call("$management/stores/$secondStore/payout-accounts/$secondAccount/verify","POST",mapOf("expectedVersion" to 1),admin).statusCode())
        assertEquals(listOf(201,409),concurrently({payout(first,firstAccount,10000,ref="GLOBAL-REFERENCE")},{payout(second,secondAccount,10000,ref="GLOBAL-REFERENCE")}))
        assertEquals(1,count("select count(*) from store_payout where transfer_reference='GLOBAL-REFERENCE'"))
    }

    @Test fun `AC-06 completion event with missing allocation is an unresolved exception`() {
        jdbc.execute("TRUNCATE ledger_entry CASCADE")
        val d=draft();assertEquals(200,d.statusCode(),d.body())
        assertTrue(json(d)["unresolvedExceptions"].any { it["reason"].asString()=="MISSING_LEDGER_ENTRY" })
        val id=json(d)["id"].asString();assertEquals(409,call("$management/settlements/$id/confirm","POST",token=admin).statusCode())
    }

    @Test fun `AC-06 10 busy order returns retryable conflict without a deadlock`() {
        dataSource.connection.use { connection ->
            connection.autoCommit=false
            connection.prepareStatement("select id from customer_order where id=? for update").use { statement ->
                statement.setObject(1,order);statement.executeQuery().close()
            }
            try {
                val result=java.util.concurrent.CompletableFuture.supplyAsync {draft()}.get(10,java.util.concurrent.TimeUnit.SECONDS)
                assertEquals(409,result.statusCode(),result.body())
            } finally {connection.rollback()}
        }
        val after=draft();assertEquals(200,after.statusCode(),after.body())
    }

    @Test fun `AC-03 07 eligible no-show can settle while customer refund remains pending`() {
        clock.time=jdbc.queryForObject("select pickup_ends_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant()
        owner=login("owner@example.test")["accessToken"].asString();admin=login("admin@example.test")["accessToken"].asString()
        val n=callWithHeader("/api/v1/stores/$storeId/orders/$order/no-show","POST",mapOf("storeOpenedOnTime" to true,"boxesReadyAndCompliant" to true,"evidence" to "Absent"),owner,"Idempotency-Key","NOSHOW-PENDING-REFUND")
        assertEquals(201,n.statusCode(),n.body())
        // The T7.4 release job determines eligibility after the window; refund fulfillment is independent.
        jdbc.update("update no_show_case set compensation_status='ELIGIBLE' where order_id=?",order)
        val d=draft();assertEquals(200,d.statusCode(),d.body());assertEquals(30000,json(d)["totalAmount"].asInt());assertEquals(0,json(d)["unresolvedExceptions"].size())
        val id=json(d)["id"].asString();val c=call("$management/settlements/$id/confirm","POST",token=admin)
        assertEquals(200,c.statusCode(),c.body());assertEquals(1,count("select count(*) from refund_request where order_id=? and status='PENDING'",order))
        assertEquals("SETTLED",jdbc.queryForObject("select compensation_status from no_show_case where order_id=?",String::class.java,order))
    }

    @Test fun `AC-10 pending payout can verify using its existing evidence without duplicate attachment`() {
        val account=verified();val settlement=confirmed();val pending=payout(settlement,account,10000,status="PENDING")
        assertEquals(201,pending.statusCode(),pending.body());val id=json(pending)["id"].asString();val file=json(pending)["evidenceFileIds"][0].asString()
        val verified=call("$management/payouts/$id/verify","POST",mapOf("transferredAt" to clock.time,"evidenceFileId" to file),admin)
        assertEquals(200,verified.statusCode(),verified.body());assertEquals("VERIFIED",json(verified)["status"].asString())
        assertEquals(1,count("select count(*) from attachment where payout_id=?",UUID.fromString(id)))
    }

}
