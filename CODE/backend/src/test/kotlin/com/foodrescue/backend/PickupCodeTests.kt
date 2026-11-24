// AC-01..14 (T7.1): PickupCodeTests verifies pickup codes through real HTTP and PostgreSQL.
package com.foodrescue.backend

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.payment.gateway.FakePaymentGateway
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.pickup.service.PickupCodeBackfillService
import com.foodrescue.backend.pickup.service.PickupCodeService
import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class PickupTestClock : Clock() {
    var time: Instant = Instant.now()
    override fun instant() = time
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(time, zone)
}
@TestConfiguration
class PickupClockConfiguration {
    @Bean @Primary fun pickupTestClock() = PickupTestClock()
}

@Import(PickupClockConfiguration::class)
// Release this extra Clock-specific context's pool before the remaining profile contexts start.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PickupCodeTests : StoreTestFixtures() {
    @Autowired lateinit var gateway: PaymentGateway
    @Autowired lateinit var pickup: PickupCodeService
    @Autowired lateinit var backfill: PickupCodeBackfillService
    @Autowired lateinit var clock: PickupTestClock
    @Autowired lateinit var tm: PlatformTransactionManager
    private val fake get() = gateway as FakePaymentGateway
    private lateinit var owner: String; private lateinit var customer: String; private lateinit var store: String
    private lateinit var batch: UUID
    @BeforeEach fun reset() { fake.reset(); clock.time = fixtureNow() }
    private fun setup() {
        owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test")
        store = store(owner, admin); openAllWeek(store, owner); batch = openOffer(store, owner, boxes = 8)
        customer = user("customer@example.test")
    }
    private fun payable(): Pair<UUID, Long> {
        val id = UUID.fromString(json(placeOrder(customer, batch, 2))["id"].asString())
        return id to json(call("/api/v1/orders/$id/payment", "POST", token = customer))["providerOrderCode"].asLong()
    }
    private fun hook(code: Long, amount: Long = 60000, ref: String = UUID.randomUUID().toString()) =
        call("/api/v1/payments/webhooks/payos", "POST", fake.webhookFor(code, amount, ref))
    private fun get(id: UUID, token: String? = customer) = call("/api/v1/orders/$id/pickup-code", token = token)
    private fun verify(code: String, token: String? = owner, storeId: String = store) =
        call("/api/v1/stores/$storeId/pickup-codes/verify", "POST", mapOf("code" to code), token)
    private fun confirmed(): UUID {
        val (id, code) = payable(); assertEquals(200, hook(code).statusCode()); return id
    }

    @Test fun `AC-01,02,03,05,12 duplicates preserve one encrypted code and verification is read only`() {
        setup(); val (id, providerCode) = payable(); val ref = "same-transfer"
        assertEquals(listOf(200, 200), concurrently({ hook(providerCode, ref = ref) }, { hook(providerCode, ref = ref) }))
        val r = get(id); assertEquals(200, r.statusCode(), r.body()); assertEquals("no-store", r.headers().firstValue("Cache-Control").get())
        val code = json(r)["code"].asString(); assertEquals(code, json(get(id))["code"].asString())
        assertTrue(json(r)["usable"].asBoolean()); assertEquals(60000, json(r)["money"]["verifiedPaid"].asInt())
        val row = jdbc.queryForMap("select pickup_code_hash,pickup_code_encrypted,pickup_code_key_id,version from customer_order where id=?", id)
        assertFalse(row.values.contains(code)); val version = row["version"]
        val v = verify(code.lowercase().chunked(4).joinToString("-")); assertEquals(200, v.statusCode(), v.body())
        assertEquals(id.toString(), json(v)["orderId"].asString()); assertEquals(2, json(v)["boxes"].size())
        assertEquals(json(r)["pickupAddress"], json(v)["pickupAddress"])
        assertFalse(v.body().contains(code)); assertFalse(v.body().contains("pickupCodeHash"))
        assertEquals(version, jdbc.queryForObject("select version from customer_order where id=?", Long::class.java, id))
        assertEquals(1, count("select count(*) from payment where order_id=?", id))
        assertEquals(0, count("select count(*) from pickup_session"))
        assertEquals(1, count("select count(*) from financial_event where order_id=?", id))
    }

    @Test fun `AC-04,06,07 only owning customer and authorized store staff may use codes`() {
        setup(); val id = confirmed(); val code = json(get(id))["code"].asString()
        assertEquals(401, get(id, null).statusCode()); assertEquals(404, get(id, owner).statusCode())
        assertEquals(401, verify(code, null).statusCode()); assertEquals(403, verify(code, customer).statusCode())
        val viewer = staffWith(store, owner, "viewer@example.test", listOf("ORDER_VIEW"))
        assertEquals(403, verify(code, viewer).statusCode())
        val staff = staffWith(store, owner, "staff@example.test", listOf("PICKUP_HANDLE"))
        assertEquals(200, verify(code, staff).statusCode())
        jdbc.update("update staff_assignment set status='REVOKED',revoked_at=now() where account_id=?", accountId("staff@example.test"))
        assertEquals(403, verify(code, staff).statusCode())
        val otherOwner = user("other@example.test"); val admin = login("admin@example.test")["accessToken"].asString()
        val otherStore = store(otherOwner, admin, "Other shop")
        assertEquals(403, verify(code, owner, otherStore).statusCode())
        assertEquals(404, verify(code, otherOwner, otherStore).statusCode())
        val boxCode = jdbc.queryForObject("select b.box_code from physical_box b join box_allocation a on a.box_id=b.id where a.order_id=? limit 1", String::class.java, id)!!
        val orderCode = jdbc.queryForObject("select order_code from customer_order where id=?", String::class.java, id)!!
        for (internalCode in listOf(boxCode, orderCode)) assertNotEquals(200, verify(internalCode).statusCode())
        assertEquals(404, verify("0".repeat(16)).statusCode())
    }

    @Test fun `AC-08 invalid attempts survive rollback and both quotas are enforced`() {
        setup(); repeat(60) { assertEquals(400, verify("bad").statusCode()) }
        val limited = verify("bad"); assertEquals(429, limited.statusCode()); assertTrue(limited.headers().firstValue("Retry-After").get().toLong() > 0)
        jdbc.update("delete from auth_rate_limit where policy like 'PICKUP_VERIFY_%'")
        // Seed the atomic store counter close to its limit without 300 expensive HTTP requests.
        assertEquals(400, verify("bad").statusCode())
        jdbc.update("update auth_rate_limit set count=300 where policy='PICKUP_VERIFY_STORE'")
        assertEquals(429, verify("0".repeat(16)).statusCode())
    }

    @Test fun `AC-09,10 code is invalid at exact end or non confirmed states even without a job`() {
        setup(); val id = confirmed(); val code = json(get(id))["code"].asString()
        val start = jdbc.queryForObject("select pickup_starts_at from customer_order where id=?", java.sql.Timestamp::class.java, id)!!.toInstant()
        val end = jdbc.queryForObject("select pickup_ends_at from customer_order where id=?", java.sql.Timestamp::class.java, id)!!.toInstant()
        val customerId = accountId("customer@example.test"); val ownerId = accountId("owner@example.test")
        clock.time = start.minusSeconds(1); assertTrue(pickup.mine(customerId, id).usable)
        clock.time = end.minusNanos(1); assertEquals(id, pickup.verify(ownerId, UUID.fromString(store), code).orderId)
        for (time in listOf(end, end.plusSeconds(1))) {
            clock.time = time; assertNull(pickup.mine(customerId, id).code)
            assertEquals(404, assertThrows(ApiException::class.java) { pickup.verify(ownerId, UUID.fromString(store), code) }.status)
        }
        clock.time = start
        for (state in listOf("PENDING_PAYMENT", "EXPIRED", "CANCELLED", "COMPLETED", "NO_SHOW", "PENDING_NO_SHOW", "HANDOVER_IN_PROGRESS")) {
            jdbc.update("update customer_order set status=? where id=?", state, id)
            assertFalse(pickup.mine(customerId, id).usable); assertNull(pickup.mine(customerId, id).code)
            assertEquals(404, assertThrows(ApiException::class.java) { pickup.verify(ownerId, UUID.fromString(store), code) }.status)
        }
    }

    @Test fun `AC-11 non qualifying payments never get codes`() {
        setup(); val (shortId, shortCode) = payable(); hook(shortCode, 59000)
        val (overId, overCode) = payable(); hook(overCode, 61000)
        val (lateId, lateCode) = payable(); call("/api/v1/orders/$lateId/cancel", "POST", mapOf("reason" to "changed mind"), customer); hook(lateCode)
        for (id in listOf(shortId, overId, lateId)) {
            assertNull(json(get(id))["code"].takeUnless { it.isNull }); assertFalse(json(get(id))["usable"].asBoolean())
            assertEquals(0, count("select count(*) from customer_order where id=? and pickup_code_hash is not null", id))
        }
    }

    @Test fun `AC-02 confirmation rolls back when code persistence fails`() {
        setup(); val (id, providerCode) = payable()
        jdbc.execute("alter table customer_order add constraint test_reject_pickup_code check (pickup_code_encrypted is null)")
        try {
            assertEquals(503, hook(providerCode).statusCode())
            assertEquals(0, count("select count(*) from payment where order_id=?", id))
            assertEquals(1, count("select count(*) from customer_order where id=? and status='PENDING_PAYMENT' and pickup_code_hash is null", id))
            assertEquals(2, count("select count(*) from box_allocation where order_id=? and status='HELD'", id))
            assertEquals(0, count("select count(*) from financial_event where order_id=?", id))
        } finally { jdbc.execute("alter table customer_order drop constraint test_reject_pickup_code") }
        assertEquals(200, hook(providerCode).statusCode()); assertNotNull(json(get(id))["code"])
    }

    @Test fun `AC-12 corrupt encrypted code is never silently replaced`() {
        setup(); val id = confirmed(); val hash = jdbc.queryForObject("select pickup_code_hash from customer_order where id=?", String::class.java, id)
        jdbc.update("update customer_order set pickup_code_encrypted='damaged' where id=?", id)
        val r = get(id); assertEquals(503, r.statusCode()); assertFalse(r.body().contains("damaged"))
        assertEquals(hash, jdbc.queryForObject("select pickup_code_hash from customer_order where id=?", String::class.java, id))
    }

    @Test fun `AC-14 backfill is concurrent idempotent and respects status time and snapshots`() {
        setup(); val legacy = order(store, batch, "customer@example.test")
        clock.time = jdbc.queryForObject("select pickup_ends_at from customer_order where id=?", java.sql.Timestamp::class.java, legacy)!!.toInstant().minusSeconds(1)
        val ended = order(store, batch, "customer@example.test"); jdbc.update("update customer_order set status='COMPLETED' where id=?", ended)
        assertTrue(legacy in backfill.candidates()); assertFalse(ended in backfill.candidates())
        assertThrows(Exception::class.java) { TransactionTemplate(tm).execute {
            jdbc.update("update customer_order set pickup_code_key_id='partial' where id=?", legacy)
        } }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val tasks = (1..2).map { pool.submit(java.util.concurrent.Callable { start.await(); backfill.issueOne(legacy) }) }
            start.countDown()
            assertEquals(1, tasks.count { it.get(30, java.util.concurrent.TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
        val code = json(get(legacy))["code"].asString(); assertFalse(backfill.issueOne(legacy)); assertEquals(code, json(get(legacy))["code"].asString())
        assertFalse(backfill.issueOne(ended)); assertEquals(0, count("select count(*) from financial_event"))
        assertThrows(Exception::class.java) { TransactionTemplate(tm).execute { jdbc.update("update customer_order set pickup_address_snapshot='changed' where id=?", legacy) } }
        val incomplete = order(store, batch, "customer@example.test")
        jdbc.update("update customer_order set pickup_code_hash='legacy-hash-without-ciphertext' where id=?", incomplete)
        assertThrows(IllegalStateException::class.java) { backfill.issueOne(incomplete) }
        assertEquals("legacy-hash-without-ciphertext", jdbc.queryForObject("select pickup_code_hash from customer_order where id=?", String::class.java, incomplete))
        val expired = order(store, batch, "customer@example.test"); clock.time = Instant.now().plusSeconds(86400)
        assertFalse(backfill.issueOne(expired))
    }
}
