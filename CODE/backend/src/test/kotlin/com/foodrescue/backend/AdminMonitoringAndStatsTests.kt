// AC-01..07 (T8.5): Admin inspection, activity and reconciled financial reporting over HTTP/PostgreSQL.
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
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AdminMonitoringAndStatsTests: StoreTestFixtures() {
    @Autowired lateinit var clock: PickupTestClock
    @Autowired lateinit var gateway: PaymentGateway
    @Autowired lateinit var expiry: com.foodrescue.backend.order.service.OrderExpiryService
    @Autowired lateinit var compensation: com.foodrescue.backend.pickup.service.NoShowCompensationService
    lateinit var owner: String;lateinit var admin: String;lateinit var customer: String;lateinit var storeId: String
    val menu get()="/api/v1/admin/stores/$storeId/menu-price-audit"
    val logs="/api/v1/admin/audit-logs";val stats="/api/v1/admin/finance/statistics"
    @BeforeEach fun setup() {
        clock.time=fixtureNow().truncatedTo(java.time.temporal.ChronoUnit.MICROS);(gateway as FakePaymentGateway).reset()
        owner=user("owner@example.test");admin=makeAdmin("admin@example.test");customer=user("customer@example.test")
        storeId=store(owner,admin);openAllWeek(storeId,owner)
    }
    fun order(paid: Boolean=true,ownerEmail: String="owner@example.test"): UUID {
        val batch=openOffer(storeId,owner,ownerEmail=ownerEmail,boxes=2);val id=UUID.fromString(json(placeOrder(customer,batch,2))["id"].asString())
        if(paid) {val code=json(call("/api/v1/orders/$id/payment","POST",token=customer))["providerOrderCode"].asLong();assertEquals(200,call("/api/v1/payments/webhooks/payos","POST",(gateway as FakePaymentGateway).webhookFor(code,60000,"PAID-$id")).statusCode())}
        return id
    }
    fun complete(): UUID {
        val id=order();val start=jdbc.queryForObject("select pickup_starts_at from customer_order where id=?",java.sql.Timestamp::class.java,id)!!.toInstant()
        clock.time=start;owner=login("owner@example.test")["accessToken"].asString();admin=login("admin@example.test")["accessToken"].asString();customer=login("customer@example.test")["accessToken"].asString()
        val s=callWithHeader("/api/v1/stores/$storeId/orders/$id/pickup-sessions/start","POST",null,owner,"Idempotency-Key","HANDOVER-$id")
        assertEquals(201,s.statusCode(),s.body());val r=callWithHeader("/api/v1/stores/$storeId/pickup-sessions/${json(s)["id"].asString()}/complete","POST",null,owner,"Idempotency-Key","HANDOVER-$id")
        assertEquals(200,r.statusCode(),r.body());return id
    }
    fun report(query: String="?storeId=$storeId")=call(stats+query,token=admin)
    @Test fun `AC-01 empty menu report is available to admin`() {val r=call(menu,token=admin);assertEquals(200,r.statusCode(),r.body());assertEquals(0,json(r)["items"]["totalElements"].asInt())}
    @Test fun `AC-01 price history documents and immutable packed snapshot mismatch`() {
        val r=call("/api/v1/stores/$storeId/menu-items","POST",mapOf("name" to "Bread","categoryCode" to "BAKERY","listedPrice" to 25000,"ingredients" to "Flour","safetyWarnings" to "Gluten"),owner)
        assertEquals(201,r.statusCode(),r.body());val item=json(r)["item"];val id=item["id"].asString()
        val batch=openOffer(storeId,owner,boxes=1);val box=jdbc.queryForObject("select id from physical_box where current_batch_id=?",UUID::class.java,batch)!!
        jdbc.update("insert into box_item(id,created_at,store_id,box_id,menu_item_id,quantity,item_name_snapshot,unit_price_snapshot,safety_warnings_snapshot,expires_at) values (?,now(),?,?,?,2,'Bread',25000,'Gluten',?)",UUID.randomUUID(),UUID.fromString(storeId),box,UUID.fromString(id),java.sql.Timestamp.from(clock.time.plusSeconds(172800)))
        assertEquals(200,call("/api/v1/stores/$storeId/menu-items/$id","PATCH",mapOf("version" to item["version"].asLong(),"listedPrice" to 30000,"priceChangeReason" to "New price"),owner).statusCode())
        val newerBox=box(storeId,batch)
        jdbc.update("insert into box_item(id,created_at,store_id,box_id,menu_item_id,quantity,item_name_snapshot,unit_price_snapshot,safety_warnings_snapshot,expires_at) values (?,now(),?,?,?,1,'Bread',30000,'Gluten',?)",UUID.randomUUID(),UUID.fromString(storeId),newerBox,UUID.fromString(id),java.sql.Timestamp.from(clock.time.plusSeconds(172800)))
        for(menuId in listOf<String?>(null,id)) {
            val doc=call("/api/v1/stores/$storeId/price-documents","POST",mapOf("fileId" to fileId(owner),"menuItemId" to menuId,"description" to "Price proof","appliesFrom" to "2026-11-01"),owner)
            assertEquals(201,doc.statusCode(),doc.body())
        }
        val before=count("select count(*) from audit_log");val result=call("$menu?menuItemId=$id&onlyMismatchedWithPackedBoxes=true",token=admin)
        assertEquals(200,result.statusCode(),result.body());val v=json(result)["items"]["content"][0]
        assertTrue(v["hasPackedPriceMismatch"].asBoolean());assertEquals(2,v["priceHistory"].size());assertEquals(2,v["documents"].size());val prices=v["packedBoxPriceSummary"];assertEquals(2,prices.size())
        val old=(0 until prices.size()).map { prices[it] }.first { it["unitPriceSnapshot"].asInt()==25000 }
        val current=(0 until prices.size()).map { prices[it] }.first { it["unitPriceSnapshot"].asInt()==30000 }
        assertEquals(5000,old["priceDeltaFromCurrentListedPrice"].asInt());assertEquals(2,old["totalQuantity"].asInt());assertEquals(1,old["boxCount"].asInt());assertFalse(current["differsFromCurrentListedPrice"].asBoolean())
        assertEquals(before,count("select count(*) from audit_log"))
    }
    @Test fun `AC-02 audit filters include finance actions and safe owner identity`() {
        val r=call("/api/v1/stores/$storeId/finance/payout-account","PUT",mapOf("bankCode" to "VCB","accountNumber" to "1234567890","accountHolderName" to "OWNER"),owner);assertEquals(200,r.statusCode())
        val a=call("$logs?storeId=$storeId&entityType=store_payout_account&action=payout_account_updated&actorId=${accountId("owner@example.test")}",token=admin)
        assertEquals(200,a.statusCode(),a.body());assertEquals(1,json(a)["totalElements"].asInt());assertEquals("OWNER",json(a)["content"][0]["actorRole"].asString());assertFalse(a.body().contains("1234567890"));assertTrue(json(a)["content"][0].has("correlationId"))
        assertEquals(0,json(call("$logs?action=NOT_AN_ACTION",token=admin))["totalElements"].asInt())
    }
    @Test fun `AC-03 completed lifecycle draft confirmed and partial payout reconcile`() {
        complete();val initial=report();assertEquals(200,initial.statusCode(),initial.body());val root=json(initial)
        assertEquals(60000,root["ledger"]["verifiedCollectedAmount"].asInt());assertEquals(6000,root["ledger"]["netCommissionAmount"].asInt());assertEquals(54000,root["settlementAndHold"]["eligibleUnassignedAmount"].asInt());assertEquals(8,root["orderCountsByStatus"].size())
        val d=call("/api/v1/admin/finance/settlements/draft","POST",mapOf("storeId" to storeId,"periodStartsAt" to clock.time.minusSeconds(86400),"periodEndsAt" to clock.time.plusSeconds(1)),admin);assertEquals(200,d.statusCode(),d.body());val sid=json(d)["id"].asString()
        assertEquals(54000,json(report())["settlementAndHold"]["draftUnconfirmedAmount"].asInt());assertEquals(200,call("/api/v1/admin/finance/settlements/$sid/confirm","POST",token=admin).statusCode())
        val a=call("/api/v1/stores/$storeId/finance/payout-account","PUT",mapOf("bankCode" to "VCB","accountNumber" to "1234567890","accountHolderName" to "OWNER"),owner);val aid=json(a)["id"].asString()
        assertEquals(200,call("/api/v1/admin/finance/stores/$storeId/payout-accounts/$aid/verify","POST",mapOf("expectedVersion" to 1),admin).statusCode())
        val p=call("/api/v1/admin/finance/settlements/$sid/payouts","POST",mapOf("payoutAccountId" to aid,"expectedAccountVersion" to 1,"amount" to 10000,"transferReference" to "STAT-BANK","transferredAt" to clock.time,"evidenceFileId" to imageId(admin)),admin);assertEquals(201,p.statusCode(),p.body())
        val final=json(report());assertEquals(10000,final["settlementAndHold"]["paidOutVerifiedAmount"].asInt());assertEquals(44000,final["settlementAndHold"]["confirmedRemainingPayableAmount"].asInt());assertEquals(54000,final["settlementAndHold"]["confirmedSettledLedgerAmount"].asInt())
    }
    @Test fun `AC-03 04 UTC windows and multi-store totals use independent clocks`() {
        val firstStore=storeId;val firstOwner=owner
        val secondOwner=user("owner2@example.test");val second=store(secondOwner,admin);openAllWeek(second,secondOwner)
        storeId=second;owner=secondOwner;order(ownerEmail="owner2@example.test");storeId=firstStore;owner=firstOwner
        complete();val scoped=json(report());val all=json(report(""));assertEquals(scoped["ledger"]["netCommissionAmount"].asInt(),all["ledger"]["netCommissionAmount"].asInt());assertEquals(120000,all["ledger"]["verifiedCollectedAmount"].asInt())
        val future=clock.time.plusSeconds(1);assertEquals(0,json(report("?from=$future"))["ledger"]["netCommissionAmount"].asInt())
        val boundary=json(report("?from=${clock.time}&to=${clock.time}"));assertEquals(60000,boundary["ledger"]["completedOrderGrossRevenue"].asInt());assertEquals(0,boundary["totalOrderCount"].asInt())
        assertEquals(60000,json(report("?storeId=$second"))["ledger"]["verifiedCollectedAmount"].asInt());assertEquals(2,all["byStore"].size())
        assertEquals(all["ledger"]["verifiedCollectedAmount"].asInt(),all["byStore"].sumOf { it["ledger"]["verifiedCollectedAmount"].asInt() })
    }
    @Test fun `AC-05 filters and missing resources return controlled errors`() {
        for(path in listOf("$logs?entityType=BAD","$logs?sort=passwordHash","$logs?size=101","$stats?from=2027-01-01T00:00:00Z&to=2026-01-01T00:00:00Z")) assertEquals(400,call(path,token=admin).statusCode())
        for(path in listOf("$menu?menuItemId=${UUID.randomUUID()}","$logs?storeId=${UUID.randomUUID()}","$stats?storeId=${UUID.randomUUID()}")) assertEquals(404,call(path,token=admin).statusCode())
    }
    @Test fun `AC-06 only admins may inspect menu audit and finance`() {
        val staff=staffWith(storeId,owner,"staff@example.test",listOf("MENU_VIEW","BOX_PACK","BOX_MANAGE","PICKUP_HANDLE","ORDER_VIEW","NO_SHOW_CONFIRM"))
        for(path in listOf(menu,logs,stats)) {assertEquals(401,call(path).statusCode());for(token in listOf(owner,customer,staff)) assertEquals(403,call(path,token=token).statusCode())}
    }
    @Test fun `AC-03 lifecycle holds no-show refund cancellation expiry and settled compensation reconcile`() {
        val noShow=order();val cancelled=order()
        val cancel=callWithHeader("/api/v1/orders/$cancelled/cancel","POST",mapOf("reason" to "Changed plans"),customer,"Idempotency-Key","CANCEL-STATS")
        assertEquals(200,cancel.statusCode(),cancel.body())
        val refund=jdbc.queryForObject("select id from refund_request where order_id=?",UUID::class.java,cancelled)!!
        assertEquals(200,call("/api/v1/admin/refund-requests/$refund/approve","POST",mapOf("amount" to 60000,"note" to "Approved"),admin).statusCode())
        val refunded=call("/api/v1/admin/refund-requests/$refund/confirm","POST",mapOf("bankTransactionId" to "STAT-CANCEL","transferredAt" to clock.time,"evidenceFileId" to imageId(admin)),admin);assertEquals(200,refunded.statusCode(),refunded.body())
        val expired=order(false);val expiryAt=jdbc.queryForObject("select hold_expires_at from customer_order where id=?",java.sql.Timestamp::class.java,expired)!!.toInstant()
        assertEquals(1,expiry.expireDue(expiryAt.plusSeconds(1)));order(false)
        val delivered=complete()
        val c=call("/api/v1/complaints","POST",mapOf("orderId" to delivered,"type" to "FOOD_SAFETY","description" to "Unsafe food"),customer);assertEquals(201,c.statusCode(),c.body())
        clock.time=jdbc.queryForObject("select pickup_ends_at from customer_order where id=?",java.sql.Timestamp::class.java,noShow)!!.toInstant()
        owner=login("owner@example.test")["accessToken"].asString();admin=login("admin@example.test")["accessToken"].asString();customer=login("customer@example.test")["accessToken"].asString()
        val n=callWithHeader("/api/v1/stores/$storeId/orders/$noShow/no-show","POST",mapOf("storeOpenedOnTime" to true,"boxesReadyAndCompliant" to true,"evidence" to "Absent"),owner,"Idempotency-Key","STAT-NOSHOW");assertEquals(201,n.statusCode(),n.body())
        val notification=jdbc.queryForObject("select notification_id from no_show_case where order_id=?",UUID::class.java,noShow)!!;compensation.recordNotificationReceived(notification,clock.time)
        val dispute=call("/api/v1/complaints","POST",mapOf("orderId" to noShow,"type" to "NO_SHOW","description" to "Dispute"),customer);assertEquals(201,dispute.statusCode(),dispute.body())
        val held=json(report());assertEquals(84000,held["settlementAndHold"]["heldAmount"].asInt());assertEquals(60000,held["ledger"]["verifiedRefundedAmount"].asInt())
        for(status in listOf("PENDING_PAYMENT","EXPIRED","CANCELLED","COMPLETED","NO_SHOW")) assertEquals(1,held["orderCountsByStatus"][status].asInt())
        val reject=call("/api/v1/admin/complaints/${json(dispute)["id"].asString()}/decide","POST",mapOf("outcome" to "REJECTED","responsibility" to "NONE","reason" to "No store fault"),admin);assertEquals(200,reject.statusCode(),reject.body())
        clock.time=clock.time.plusSeconds(49*3600);assertEquals(1,compensation.releaseEligible(clock.time))
        owner=login("owner@example.test")["accessToken"].asString();admin=login("admin@example.test")["accessToken"].asString()
        val d=call("/api/v1/admin/finance/settlements/draft","POST",mapOf("storeId" to storeId,"periodStartsAt" to clock.time.minusSeconds(4*86400),"periodEndsAt" to clock.time.plusSeconds(1)),admin)
        assertEquals(200,d.statusCode(),d.body());val sid=json(d)["id"].asString();assertEquals(30000,json(d)["totalAmount"].asInt())
        assertEquals(200,call("/api/v1/admin/finance/settlements/$sid/confirm","POST",token=admin).statusCode())
        val a=call("/api/v1/stores/$storeId/finance/payout-account","PUT",mapOf("bankCode" to "VCB","accountNumber" to "1234567890","accountHolderName" to "OWNER"),owner);val aid=json(a)["id"].asString()
        assertEquals(200,call("/api/v1/admin/finance/stores/$storeId/payout-accounts/$aid/verify","POST",mapOf("expectedVersion" to 1),admin).statusCode())
        val pay=call("/api/v1/admin/finance/settlements/$sid/payouts","POST",mapOf("payoutAccountId" to aid,"expectedAccountVersion" to 1,"amount" to 10000,"transferReference" to "STAT-NOSHOW-PART","transferredAt" to clock.time,"evidenceFileId" to imageId(admin)),admin);assertEquals(201,pay.statusCode(),pay.body())
        val final=json(report());assertEquals(54000,final["settlementAndHold"]["heldAmount"].asInt());assertEquals(30000,final["settlementAndHold"]["confirmedSettledLedgerAmount"].asInt());assertEquals(20000,final["settlementAndHold"]["confirmedRemainingPayableAmount"].asInt())
        assertEquals(final["ledger"]["totalStoreEntitlementNetAmount"].asInt(),final["settlementAndHold"]["heldAmount"].asInt()+final["settlementAndHold"]["eligibleUnsettledAmount"].asInt()+final["settlementAndHold"]["confirmedSettledLedgerAmount"].asInt())
    }
    @Test fun `AC-02 recursive audit redaction roles and all filters`() {
        val staff=staffWith(storeId,owner,"staff@example.test",listOf("ORDER_VIEW"));val ids=listOf(null,accountId("owner@example.test"),accountId("admin@example.test"),accountId("staff@example.test"),accountId("customer@example.test"))
        val entity=UUID.randomUUID()
        ids.forEach { actor -> jdbc.update("insert into audit_log(id,created_at,store_id,entity_type,entity_id,action,actor_id,occurred_at,after_state,correlation_id) values (?,now(),?,'ACCOUNT',?,'ROLE_PROBE',?,?,?,?)",UUID.randomUUID(),UUID.fromString(storeId),entity,actor,java.sql.Timestamp.from(clock.time),"{\"nested\":{\"accountNumber\":\"9876543210\",\"password\":\"HIDDEN-PASSWORD\",\"largeAmount\":9007199254740993.00}}","TEST-CORRELATION") }
        val r=call("$logs?storeId=$storeId&entityType=ACCOUNT&entityId=$entity&action=role_probe&from=${clock.time}&to=${clock.time}",token=admin)
        assertEquals(200,r.statusCode(),r.body());assertEquals(5,json(r)["totalElements"].asInt());assertEquals(setOf("SYSTEM","OWNER","ADMIN","STAFF","CUSTOMER"),(0 until json(r)["content"].size()).map { json(r)["content"][it]["actorRole"].asString() }.toSet());assertFalse(r.body().contains("9876543210"));assertFalse(r.body().contains("HIDDEN-PASSWORD"));assertTrue(r.body().contains("9007199254740993"),r.body())
    }

}
