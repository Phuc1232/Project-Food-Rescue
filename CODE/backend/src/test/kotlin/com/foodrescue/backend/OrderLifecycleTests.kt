// AC-01..07 (T5.3): OrderLifecycleTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.order.service.OrderExpiryService
import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OrderLifecycleTests : StoreTestFixtures() {
    @Autowired lateinit var expiry: OrderExpiryService
    private lateinit var owner: String; private lateinit var admin: String; private lateinit var store: String
    private fun setup(boxes: Int = 3): UUID {
        owner = user("owner@example.test"); admin = makeAdmin("admin@example.test"); store = store(owner, admin); openAllWeek(store, owner)
        return openOffer(store, owner, boxes = boxes)
    }
    private fun available(batch: UUID) = json(call("/api/v1/stores/$store/sale-batches/$batch/box-counts", token = owner))["available"].asInt()
    private fun orderId(r: java.net.http.HttpResponse<String>) = UUID.fromString(json(r)["id"].asString())
    private fun status(id: UUID) = jdbc.queryForObject("select status from customer_order where id=?", String::class.java, id)!!
    private val later: Instant get() = Instant.now().plusSeconds(11 * 60)

    @Test fun `AC-01 expired holds release the boxes`() {
        val batch = setup(); val c = user("customer@example.test")
        val expired = orderId(placeOrder(c, batch, 2)); assertEquals(1, available(batch))
        assertEquals(0, expiry.expireDue(Instant.now()))                // not due yet
        val fresh = orderId(placeOrder(c, batch, 1))
        // hold_expires_at is part of the immutable terms; expiry is driven by the scan instant instead
        assertEquals(2, expiry.expireDue(later))
        for (id in listOf(expired, fresh)) assertEquals("EXPIRED", status(id))
        assertEquals("HOLD_EXPIRED", jdbc.queryForObject("select status_reason from customer_order where id=?", String::class.java, expired))
        assertEquals(3, count("select count(*) from box_allocation where status='RELEASED' and release_reason='HOLD_EXPIRED' and released_at is not null"))
        assertEquals(0, count("select count(*) from physical_box where status='RESERVED'")); assertEquals(3, available(batch))
        assertEquals(2, count("select count(*) from audit_log where action='ORDER_EXPIRED' and actor_id is null"))
    }

    @Test fun `AC-02 concurrent and repeated runs expire once`() {
        val batch = setup(boxes = 3); val ids = (1..3).map { orderId(placeOrder(user("c$it@example.test"), batch)) }
        val pool = Executors.newFixedThreadPool(3); val start = CountDownLatch(1)
        try {
            val futures = (1..3).map { pool.submit(Callable { start.await(); expiry.expireDue(later) }) }
            start.countDown()
            assertEquals(3, futures.sumOf { it.get(30, TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
        assertEquals(0, expiry.expireDue(later))
        assertTrue(ids.all { status(it) == "EXPIRED" })
        assertEquals(3, count("select count(*) from audit_log where action='ORDER_EXPIRED'")); assertEquals(3, count("select count(*) from box_allocation where status='RELEASED'"))
    }

    @Test fun `AC-03 released boxes that are no longer safe are not sellable`() {
        val batch = setup(boxes = 1); val c = user("customer@example.test")
        placeOrder(c, batch)
        jdbc.update("update physical_box set safe_until = now() + interval '1 hour'")      // now ends before pickup end
        expiry.expireDue(later)
        assertEquals("AVAILABLE", jdbc.queryForObject("select status from physical_box", String::class.java)); assertEquals(0, available(batch))
    }

    @Test fun `AC-04 customers cancel unpaid orders`() {
        val batch = setup(); val c = user("customer@example.test"); val other = user("other@example.test")
        val id = orderId(placeOrder(c, batch, 2))
        assertEquals(404, call("/api/v1/orders/$id/cancel", "POST", mapOf("reason" to "x"), other).statusCode())
        assertField(call("/api/v1/orders/$id/cancel", "POST", mapOf("reason" to " "), c), "reason")
        val r = call("/api/v1/orders/$id/cancel", "POST", mapOf("reason" to "Đổi ý"), c); assertEquals(200, r.statusCode(), r.body())
        assertEquals("CANCELLED", json(r)["status"].asString()); assertTrue(json(r)["statusReason"].asString().startsWith("CUSTOMER_CANCELLED"))
        assertEquals(3, available(batch)); assertEquals(0, count("select count(*) from refund"))
        assertEquals(409, call("/api/v1/orders/$id/cancel", "POST", mapOf("reason" to "x"), c).statusCode())
        // job and cancel racing on one order: exactly one outcome
        val id2 = orderId(placeOrder(c, batch, 1))
        val pool = Executors.newFixedThreadPool(2); val start = CountDownLatch(1)
        try {
            val job = pool.submit(Callable { start.await(); expiry.expireDue(later) })
            val cancel = pool.submit(Callable { start.await(); call("/api/v1/orders/$id2/cancel", "POST", mapOf("reason" to "x"), c).statusCode() })
            start.countDown()
            val jobCount = job.get(30, TimeUnit.SECONDS); val code = cancel.get(30, TimeUnit.SECONDS)
            assertTrue((jobCount == 1 && code == 409 && status(id2) == "EXPIRED") || (jobCount == 0 && code == 200 && status(id2) == "CANCELLED"), "$jobCount $code ${status(id2)}")
        } finally { pool.shutdownNow() }
        assertEquals(1, count("select count(*) from box_allocation where order_id=? and status='RELEASED'", id2)); assertEquals(3, available(batch))
    }

    @Test fun `AC-05 customer list, detail and money`() {
        val batch = setup(); val c = user("customer@example.test"); user("other@example.test")
        val a = orderId(placeOrder(c, batch, 2)); val b = orderId(placeOrder(c, batch, 1))
        call("/api/v1/orders/$b/cancel", "POST", mapOf("reason" to "x"), c)
        val all = json(call("/api/v1/orders", token = c)); assertEquals(2, all["totalElements"].asInt())
        assertEquals(b.toString(), all["content"][0]["id"].asString()); assertTrue(all["content"][0]["customerName"].isNull)
        assertEquals(listOf(a.toString()), items(json(call("/api/v1/orders?status=PENDING_PAYMENT", token = c))["content"]).map { it["id"].asString() })
        val from = URLEncoder.encode(Instant.now().plusSeconds(60).toString(), "UTF-8")
        assertEquals(0, json(call("/api/v1/orders?from=$from", token = c))["totalElements"].asInt())
        assertField(call("/api/v1/orders?from=$from&to=${URLEncoder.encode(Instant.now().toString(), "UTF-8")}", token = c), "to")
        assertEquals(0, json(call("/api/v1/orders", token = login("other@example.test")["accessToken"].asString()))["totalElements"].asInt())
        val m = json(call("/api/v1/orders/$a/money", token = c))
        assertEquals(60000, m["totalAmount"].asInt()); assertEquals(0, m["verifiedPaid"].asInt()); assertEquals(60000, m["remainingToPay"].asInt())
        assertEquals(30000, m["noShowRetentionIfApplied"].asInt())
        assertEquals(404, call("/api/v1/orders/$a/money", token = login("other@example.test")["accessToken"].asString()).statusCode())
    }

    @Test fun `AC-06 store sees its orders with held boxes`() {
        val batch = setup(); val c = user("customer@example.test")
        val id = orderId(placeOrder(c, batch, 2))
        val viewer = staffWith(store, owner, "viewer@example.test", listOf("ORDER_VIEW")); val packer = staffWith(store, owner, "packer@example.test", listOf("BOX_PACK"))
        val list = json(call("/api/v1/stores/$store/orders", token = viewer)); assertEquals(1, list["totalElements"].asInt())
        assertEquals("User", list["content"][0]["customerName"].asString())
        val d = call("/api/v1/stores/$store/orders/$id", token = owner); assertEquals(200, d.statusCode())
        assertEquals(2, json(d)["boxes"].size()); assertEquals("HELD", json(d)["boxes"][0]["allocationStatus"].asString())
        assertFalse(d.body().contains("customer@example.test"))
        for (t in listOf(packer, c, admin)) assertEquals(403, call("/api/v1/stores/$store/orders", token = t).statusCode())
        val other = user("other@example.test"); val otherStore = store(other, admin, "Khác")
        assertEquals(404, call("/api/v1/stores/$otherStore/orders/$id", token = other).statusCode())
    }
}
