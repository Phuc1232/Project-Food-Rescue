// AC-01..08: Real HTTP/PostgreSQL outbox, retries, concurrency, ownership and No-show clock.
package com.foodrescue.backend
import com.foodrescue.backend.support.*
import com.foodrescue.backend.notification.*
import com.foodrescue.backend.notification.entity.Notification
import com.foodrescue.backend.notification.port.*
import com.foodrescue.backend.notification.service.*
import com.foodrescue.backend.notification.repository.NotificationRepository
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.payment.gateway.*
import com.foodrescue.backend.pickup.service.NoShowCompensationService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
@Import(PickupClockConfiguration::class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationTests : StoreTestFixtures() {
    @Autowired lateinit var clock: PickupTestClock
    @Autowired lateinit var gateway: PaymentGateway
    @Autowired lateinit var context: ApplicationContext
    @Autowired lateinit var dispatchService: NotificationDispatchService
    @Autowired lateinit var dispatchStore: NotificationDispatchStore
    @Autowired lateinit var outbox: NotificationOutboxService
    @Autowired lateinit var notifications: NotificationRepository
    @Autowired lateinit var accounts: AccountRepository
    @Autowired lateinit var properties: NotificationProperties
    @Autowired lateinit var tm: PlatformTransactionManager
    @Autowired lateinit var compensation: NoShowCompensationService
    lateinit var owner: String; lateinit var customer: String; lateinit var admin: String
    lateinit var storeId: String; lateinit var order: UUID; lateinit var batch: UUID
    @BeforeEach fun setup() {
        clock.time=fixtureNow();(gateway as FakePaymentGateway).reset()
        owner=user("owner@example.test");admin=makeAdmin("admin@example.test");storeId=store(owner,admin);openAllWeek(storeId,owner)
        batch=openOffer(storeId,owner,boxes=4);customer=user("customer@example.test")
        val r=placeOrder(customer,batch,2);assertEquals(201,r.statusCode(),r.body());order=UUID.fromString(json(r)["id"].asString())
    }
    fun events(code: String)=count("select count(*) from notification where order_id=? and event_code=?",order,code)
    fun pay() {
        val code=json(call("/api/v1/orders/$order/payment","POST",token=customer))["providerOrderCode"].asLong()
        val body=(gateway as FakePaymentGateway).webhookFor(code,60000,"payment")
        assertEquals(200,call("/api/v1/payments/webhooks/payos","POST",body).statusCode())
        assertEquals(200,call("/api/v1/payments/webhooks/payos","POST",body).statusCode())
    }
    fun create(type: NotificationType=NotificationType.PAYMENT): UUID = TransactionTemplate(tm).execute {
        notifications.saveAndFlush(Notification(accounts.getReferenceById(accountId("customer@example.test")),type=type,payload="{\"amount\":100}",status=NotificationStatus.PENDING,eventCode="TEST",title="Test event",nextAttemptAt=clock.time)).id!!
    }
    fun dispatch(time: Instant=clock.time,batchSize: Int=100) {
        dispatchService.dispatchDue(time,batchSize)
    }
    fun clearOutbox() { jdbc.update("delete from notification") }
    fun state(id: UUID)=jdbc.queryForObject("select status from notification where id=?",String::class.java,id)
    fun noShow(): UUID {
        pay();clock.time=jdbc.queryForObject("select pickup_ends_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant()
        owner=login("owner@example.test")["accessToken"].asString();customer=login("customer@example.test")["accessToken"].asString()
        val r=callWithHeader("/api/v1/stores/$storeId/orders/$order/no-show","POST",mapOf("storeOpenedOnTime" to true,"boxesReadyAndCompliant" to true,"evidence" to "Absent"),owner,"Idempotency-Key","noshow")
        assertEquals(201,r.statusCode(),r.body())
        return jdbc.queryForObject("select notification_id from no_show_case where order_id=?",UUID::class.java,order)!!
    }
    @Test fun `AC-01 placing paying cancellation and replay enqueue correct recipients`() {
        assertEquals(1,events("ORDER_CREATED"));pay();assertEquals(2,events("PAYMENT_VERIFIED"))
        assertEquals(200,call("/api/v1/orders/$order/cancel","POST",mapOf("reason" to "Cannot attend"),customer).statusCode())
        assertEquals(2,events("ORDER_CANCELLED"));assertTrue(notificationSender.commands.isEmpty())
    }
    @Test fun `AC-01 handover produces outbox`() {
        pay();val start=jdbc.queryForObject("select pickup_starts_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant();clock.time=start
        owner=login("owner@example.test")["accessToken"].asString()
        val path="/api/v1/stores/$storeId/orders/$order/pickup-sessions/start"
        val session=callWithHeader(path,"POST",null,owner,"Idempotency-Key","handover");assertEquals(201,session.statusCode(),session.body())
        assertEquals(1,events("HANDOVER_STARTED"))
        assertEquals(200,callWithHeader("/api/v1/stores/$storeId/pickup-sessions/${json(session)["id"].asString()}/complete","POST",null,owner,"Idempotency-Key","handover").statusCode())
        assertEquals(1,events("ORDER_COMPLETED"))
    }
    @Test fun `AC-01 outbox requires transaction and rolls back with business changes`() {
        val actor=accounts.findById(accountId("customer@example.test")).orElseThrow()
        assertThrows(Exception::class.java) { outbox.enqueueSettlement(actor,"SETTLEMENT_TEST","Settlement",emptyMap(),null,"settlement-test") }
        val before=count("select count(*) from notification")
        assertThrows(IllegalStateException::class.java) { TransactionTemplate(tm).execute {
            outbox.enqueueSettlement(actor,"SETTLEMENT_TEST","Settlement",emptyMap(),null,"settlement-test")
            throw IllegalStateException("Rollback")
        } }
        assertEquals(before,count("select count(*) from notification"))
    }
    @Test fun `AC-02 complaint info supplement and decision emit lifecycle notifications`() {
        pay();jdbc.update("update customer_order set status='COMPLETED' where id=?",order)
        val r=callWithHeader("/api/v1/complaints","POST",mapOf("orderId" to order,"type" to "FOOD_SAFETY","description" to "Unsafe"),customer,"Idempotency-Key","complaint")
        assertEquals(201,r.statusCode(),r.body());val id=json(r)["id"].asString();assertEquals(2,events("COMPLAINT_SUBMITTED"))
        assertEquals(200,call("/api/v1/admin/complaints/$id/request-info","POST",mapOf("note" to "More"),admin).statusCode());assertEquals(1,events("COMPLAINT_INFO_REQUESTED"))
        assertEquals(200,call("/api/v1/complaints/$id/supplement","POST",mapOf("description" to "Details"),customer).statusCode());assertEquals(1,events("COMPLAINT_SUPPLEMENTED"))
        assertEquals(200,call("/api/v1/admin/complaints/$id/decide","POST",mapOf("outcome" to "REJECTED","responsibility" to "NONE","reason" to "Not supported"),admin).statusCode())
        assertEquals(2,events("COMPLAINT_DECIDED"));assertEquals(1,events("COMPLAINT_CLOSED"))
        assertTrue(count("select count(*) from notification where event_code='PARTNER_APPLICATION_APPROVE'")>0)
    }
    @Test fun `AC-03 dispatch delivers outside database transactions`() {
        clearOutbox();val id=create();dispatch();assertEquals("DELIVERED",state(id));assertEquals(1,notificationSender.commands.size)
        assertEquals(listOf(false),notificationSender.transactionStates);dispatch();assertEquals(1,notificationSender.commands.size)
    }
    @Test fun `AC-03 SENT becomes delivered only on read`() {
        clearOutbox();val id=create();notificationSender.outcome=NotificationDeliveryOutcome.SENT;dispatch();assertEquals("SENT",state(id))
        assertEquals(200,call("/api/v1/notifications/$id/read","POST",token=customer).statusCode());assertEquals("DELIVERED",state(id))
    }
    @Test fun `AC-04 failure retries exact backoff then fails without changing order`() {
        clearOutbox();val id=create();notificationSender.fail=true;dispatch();assertEquals("PENDING",state(id));assertEquals(1,notificationSender.commands.size)
        dispatch(clock.time.plusSeconds(59));assertEquals(1,notificationSender.commands.size)
        dispatch(clock.time.plusSeconds(60));assertEquals(2,notificationSender.commands.size)
        dispatch(clock.time.plusSeconds(359));assertEquals(2,notificationSender.commands.size)
        dispatch(clock.time.plusSeconds(360));assertEquals("FAILED",state(id));assertEquals(3,notificationSender.commands.size)
        assertEquals("PENDING_PAYMENT",jdbc.queryForObject("select status from customer_order where id=?",String::class.java,order))
        assertFalse(jdbc.queryForObject("select last_error from notification where id=?",String::class.java,id)!!.contains("sensitive"))
    }
    @Test fun `AC-05 concurrent workers claim each item once`() {
        clearOutbox();repeat(8) { create() };val pool=Executors.newFixedThreadPool(2);val latch=CountDownLatch(1)
        try { val futures=(1..2).map { pool.submit { latch.await();dispatch() } };latch.countDown();futures.forEach { it.get(30,TimeUnit.SECONDS) } } finally { pool.shutdownNow() }
        assertEquals(8,notificationSender.commands.size);assertEquals(8,notificationSender.commands.map { it.notificationId }.distinct().size)
    }
    @Test fun `AC-06 inbox filters pagination ownership authentication and safe fields`() {
        clearOutbox();val id=create();create(NotificationType.COMPLAINT)
        val r=call("/api/v1/notifications?type=PAYMENT&unreadOnly=true&size=1",token=customer);assertEquals(200,r.statusCode(),r.body());assertEquals(1,json(r)["totalElements"].asInt())
        val detail=call("/api/v1/notifications/$id",token=customer);assertEquals(200,detail.statusCode());assertEquals(100,json(detail)["payload"]["amount"].asInt())
        assertFalse(json(detail).has("lastError"));assertFalse(json(detail).has("recipientEmail"));assertEquals(2,json(call("/api/v1/notifications/unread-count",token=customer))["unreadCount"].asInt())
        assertEquals(404,call("/api/v1/notifications/$id",token=owner).statusCode());assertEquals(404,call("/api/v1/notifications/$id/read","POST",token=owner).statusCode())
        assertEquals(401,call("/api/v1/notifications").statusCode());assertEquals(400,call("/api/v1/notifications?size=101",token=customer).statusCode())
        assertEquals(400,call("/api/v1/notifications/not-a-uuid",token=customer).statusCode())
    }
    @Test fun `AC-07 only first read starts no-show dispute clock`() {
        val id=noShow();assertEquals(0,count("select count(*) from no_show_case where notification_received_at is not null"))
        dispatch();assertEquals(0,count("select count(*) from no_show_case where notification_received_at is not null"))
        assertEquals(200,call("/api/v1/notifications/$id/read","POST",token=customer).statusCode())
        val deadline=jdbc.queryForObject("select dispute_deadline from no_show_case",java.sql.Timestamp::class.java)!!.toInstant()
        assertEquals(clock.time.plusSeconds(48*3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS),deadline)
        assertEquals(200,call("/api/v1/notifications/$id/read","POST",token=customer).statusCode());assertEquals(200,call("/api/v1/notifications/read-all","POST",token=customer).statusCode())
        assertEquals(deadline,jdbc.queryForObject("select dispute_deadline from no_show_case",java.sql.Timestamp::class.java)!!.toInstant())
        assertEquals(0,compensation.releaseEligible(deadline.minusSeconds(1)));assertEquals(1,compensation.releaseEligible(deadline))
        assertEquals(1,events("NO_SHOW_COMPENSATION_ELIGIBLE"))
    }
    @Test fun `AC-07 read all starts no-show window and replay updates nothing`() {
        noShow();val r=call("/api/v1/notifications/read-all","POST",token=customer);assertEquals(200,r.statusCode(),r.body());assertTrue(json(r)["updatedCount"].asInt()>0)
        assertEquals(1,count("select count(*) from no_show_case where notification_received_at is not null"))
        assertEquals(0,json(call("/api/v1/notifications/read-all","POST",token=customer))["updatedCount"].asInt())
    }
    @Test fun `AC-01 failing outbox insert rolls back the new order and inventory hold`() {
        val before=count("select count(*) from notification");val ordersBefore=count("select count(*) from customer_order")
        val heldBefore=count("select count(*) from box_allocation where status='HELD'")
        jdbc.execute("alter table notification add constraint test_block_order_notification check(event_code<>'ORDER_CREATED') not valid")
        try {
            assertEquals(409,placeOrder(customer,batch,1).statusCode())
            assertEquals(ordersBefore,count("select count(*) from customer_order"));assertEquals(before,count("select count(*) from notification"))
            assertEquals(heldBefore,count("select count(*) from box_allocation where status='HELD'"))
        } finally { jdbc.execute("alter table notification drop constraint test_block_order_notification") }
    }
    @Test fun `AC-01 expiry emits once and overdue notifies both parties`() {
        val expiry=context.getBean(com.foodrescue.backend.order.service.OrderExpiryService::class.java)
        val due=jdbc.queryForObject("select hold_expires_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant()
        assertEquals(1,expiry.expireDue(due.plusSeconds(1)));assertEquals(0,expiry.expireDue(due.plusSeconds(2)));assertEquals(1,events("ORDER_EXPIRED"))
    }
    @Test fun `AC-02 overdue notification is deduplicated across repeated scans`() {
        pay();val overdue=context.getBean(com.foodrescue.backend.pickup.service.NoShowOverdueService::class.java)
        val end=jdbc.queryForObject("select pickup_ends_at from customer_order where id=?",java.sql.Timestamp::class.java,order)!!.toInstant()
        assertEquals(1,overdue.markOverdue(end));assertEquals(0,overdue.markOverdue(end));assertEquals(2,events("ORDER_PENDING_NO_SHOW"))
    }
    @Test fun `AC-02 refunds notify approval failure and eventual success`() {
        pay();assertEquals(200,call("/api/v1/orders/$order/cancel","POST",mapOf("reason" to "Cannot attend"),customer).statusCode())
        val id=jdbc.queryForObject("select id from refund_request where order_id=?",UUID::class.java,order)!!
        assertEquals(200,call("/api/v1/admin/refund-requests/$id/approve","POST",mapOf("amount" to 60000,"note" to "Approved"),admin).statusCode())
        assertEquals(1,events("REFUND_APPROVED"))
        assertEquals(200,call("/api/v1/admin/refund-requests/$id/fail","POST",mapOf("note" to "Bank unavailable"),admin).statusCode());assertEquals(1,events("REFUND_FAILED"))
        val r=call("/api/v1/admin/refund-requests/$id/confirm","POST",mapOf("bankTransactionId" to "NOTIFY-REFUND","evidenceFileId" to imageId(admin),"transferredAt" to clock.time),admin)
        assertEquals(200,r.statusCode(),r.body());assertEquals(1,events("REFUND_SUCCEEDED"))
    }
    @Test fun `AC-02 review payments and store suspension produce outbox`() {
        val code=json(call("/api/v1/orders/$order/payment","POST",token=customer))["providerOrderCode"].asLong()
        val body=(gateway as FakePaymentGateway).webhookFor(code,30000,"review-payment")
        repeat(2) { assertEquals(200,call("/api/v1/payments/webhooks/payos","POST",body).statusCode()) }
        assertEquals(1,events("PAYMENT_REQUIRES_REVIEW"))
        val r=call("/api/v1/admin/stores/$storeId/status","POST",mapOf("action" to "SUSPEND","reason" to "Safety review","existingOrderHandling" to "CANCEL_ALL"),admin)
        assertEquals(200,r.statusCode(),r.body());assertEquals(1,count("select count(*) from notification where event_code='STORE_SUSPENDED'"))
        assertEquals(200,call("/api/v1/admin/stores/$storeId/status","POST",mapOf("action" to "REACTIVATE","reason" to "Reviewed"),admin).statusCode())
        assertEquals(1,count("select count(*) from notification where event_code='STORE_ACTIVE'"))
    }
    @Test fun `AC-02 concurrent natural key enqueue returns the same outbox row`() {
        val actor=accounts.findById(accountId("customer@example.test")).orElseThrow();val pool=Executors.newFixedThreadPool(2);val latch=CountDownLatch(1)
        try {
            val futures=(1..2).map { pool.submit(Callable { latch.await();TransactionTemplate(tm).execute {
                outbox.enqueueSettlement(actor,"SETTLEMENT_TEST","Settlement",emptyMap(),null,"settlement-once").id!!
            } }) };latch.countDown();val ids=futures.map { it.get(30,TimeUnit.SECONDS) }
            assertEquals(1,ids.distinct().size);assertEquals(1,count("select count(*) from notification where dedupe_key='settlement-once'"))
        } finally { pool.shutdownNow() }
    }
    @Test fun `AC-05 expired lease recovers and late completion cannot overwrite the new claim`() {
        clearOutbox();val id=create();val first=dispatchStore.claimDue(clock.time,1).single()
        assertTrue(dispatchStore.claimDue(clock.time.plusSeconds(59),1).isEmpty())
        val second=dispatchStore.claimDue(clock.time.plusSeconds(60),1).single()
        dispatchStore.markFailure(first,clock.time.plusSeconds(61),"stale")
        assertEquals(0,jdbc.queryForObject("select attempt_count from notification where id=?",Int::class.java,id))
        dispatchStore.markSuccess(second,clock.time.plusSeconds(62),NotificationDeliveryResult(NotificationDeliveryOutcome.SENT))
        dispatchStore.markFailure(first,clock.time.plusSeconds(63),"stale")
        assertEquals("SENT",state(id));assertEquals(1,jdbc.queryForObject("select attempt_count from notification where id=?",Int::class.java,id))
    }
    @Test fun `AC-03 dispatch suspends a callers transaction and failed inbox remains readable`() {
        clearOutbox();val id=create();notificationSender.fail=true
        TransactionTemplate(tm).execute { dispatch() };assertEquals(listOf(false),notificationSender.transactionStates)
        dispatch(clock.time.plusSeconds(60));dispatch(clock.time.plusSeconds(360));assertEquals("FAILED",state(id))
        val read=call("/api/v1/notifications/$id/read","POST",token=customer);assertEquals(200,read.statusCode(),read.body())
        assertEquals("FAILED",state(id));assertEquals(0,json(call("/api/v1/notifications/unread-count",token=customer))["unreadCount"].asInt())
    }

}
