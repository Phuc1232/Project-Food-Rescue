// AC-01..08 (T5.2): OrderPlacementTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OrderPlacementTests : StoreTestFixtures() {
    private lateinit var owner: String; private lateinit var admin: String; private lateinit var store: String
    private fun setup(boxes: Int = 3): UUID {
        owner = user("owner@example.test"); admin = makeAdmin("admin@example.test"); store = store(owner, admin); openAllWeek(store, owner)
        return openOffer(store, owner, boxes = boxes)
    }
    private fun available(batch: UUID) = json(call("/api/v1/stores/$store/sale-batches/$batch/box-counts", token = owner))["available"].asInt()
    private fun preview(token: String, batch: UUID, q: Int) = call("/api/v1/orders/preview", "POST", mapOf("batchId" to batch, "quantity" to q), token)

    @Test fun `AC-01 preview shows the terms and holds nothing`() {
        val batch = setup(); val c = user("customer@example.test")
        val r = preview(c, batch, 2); assertEquals(200, r.statusCode(), r.body())
        val p = json(r)
        assertEquals(60000, p["totalAmount"].asInt()); assertEquals(30000, p["unitPrice"].asInt()); assertEquals(60000, p["minimumValuePerBox"].asInt())
        assertEquals("12 Lê Lợi, Quận 1, TP.HCM", p["pickupAddress"].asString()); assertEquals(10, p["holdMinutes"].asInt())
        assertEquals(1, p["policy"]["version"].asInt()); assertEquals(30000, p["policy"]["noShowRetentionAmount"].asInt())
        assertEquals("Có gluten", p["safetyWarnings"][0].asString())
        assertEquals(0, count("select count(*) from customer_order")); assertEquals(3, available(batch))
    }

    @Test fun `AC-02 placing holds exactly N boxes with a full snapshot`() {
        val batch = setup(); val c = user("customer@example.test")
        val before = Instant.now()
        val r = placeOrder(c, batch, 2); assertEquals(201, r.statusCode(), r.body())
        val o = json(r)
        assertEquals("PENDING_PAYMENT", o["status"].asString()); assertTrue(o["orderCode"].asString().matches(Regex("FR-\\d{6}-[A-Z2-9]{6}")))
        assertEquals(60000, o["totalAmount"].asInt()); assertEquals("VND", o["currency"].asString())
        val hold = Instant.parse(o["holdExpiresAt"].asString())
        assertTrue(Duration.between(before, hold).toMinutes() in 9..10)
        assertEquals(2, count("select count(*) from box_allocation where status='HELD' and order_id=?", UUID.fromString(o["id"].asString())))
        assertEquals(2, count("select count(*) from physical_box where status='RESERVED'")); assertEquals(1, available(batch))
        assertEquals(1, count("select count(*) from audit_log where action='ORDER_CREATED'"))
        assertEquals(200, call("/api/v1/orders/${o["id"].asString()}", token = c).statusCode())
    }

    @Test fun `AC-03 not enough boxes holds nothing`() {
        val batch = setup(boxes = 2); val c = user("customer@example.test")
        val r = placeOrder(c, batch, 3); assertEquals(409, r.statusCode()); assertEquals("NOT_ENOUGH_BOXES", json(r)["code"].asString())
        assertEquals("2", json(r)["fieldErrors"][0]["message"].asString())
        assertEquals(0, count("select count(*) from customer_order")); assertEquals(0, count("select count(*) from box_allocation"))
        assertEquals(2, available(batch))
    }

    @Test fun `AC-04 customers racing for the last boxes never share a box`() {
        val batch = setup(boxes = 1); val a = user("a@example.test"); val b = user("b@example.test")
        assertEquals(listOf(201, 409), concurrently({ placeOrder(a, batch) }, { placeOrder(b, batch) }))
        assertEquals(1, count("select count(*) from box_allocation where status='HELD'"))
        // many customers for 3 boxes
        val batch2 = openOffer(store, owner, boxes = 3)
        val tokens = (1..6).map { user("c$it@example.test") }
        val results = concurrently(*tokens.map { t -> { placeOrder(t, batch2) } }.toTypedArray())
        assertEquals(3, results.count { it == 201 }); assertEquals(3, results.count { it == 409 })
        assertEquals(0, count("select count(*) from (select box_id from box_allocation where status in ('HELD','CONFIRMED') group by box_id having count(*) > 1) x"))
        assertEquals(0, available(batch2))
    }

    @Test fun `AC-05 idempotency key`() {
        val batch = setup(); val c = user("customer@example.test"); val key = "order-key-1"
        val first = placeOrder(c, batch, 1, key); assertEquals(201, first.statusCode())
        val again = placeOrder(c, batch, 1, key); assertEquals(200, again.statusCode())
        assertEquals(json(first)["id"].asString(), json(again)["id"].asString()); assertEquals(1, count("select count(*) from box_allocation"))
        assertEquals("IDEMPOTENCY_KEY_REUSED", json(placeOrder(c, batch, 2, key))["code"].asString())
        assertEquals(listOf(200, 201), concurrently({ placeOrder(c, batch, 1, "k2") }, { placeOrder(c, batch, 1, "k2") }))
        assertEquals(2, count("select count(*) from customer_order"))
        assertField(callWithHeader("/api/v1/orders", "POST", mapOf("batchId" to batch, "quantity" to 1, "acceptedPolicyVersion" to 1), c, "X-Other", "x"), "Idempotency-Key")
        assertField(placeOrder(c, batch, 1, "bad key!"), "Idempotency-Key")
        // another customer may use the same key value
        assertEquals(201, placeOrder(user("other@example.test"), batch, 1, key).statusCode())
    }

    @Test fun `AC-06 snapshot survives later changes, policy must match`() {
        val batch = setup(); val c = user("customer@example.test")
        val id = json(placeOrder(c, batch, 1))["id"].asString()
        call("/api/v1/stores/$store/sale-batches/$batch/status", "POST", mapOf("action" to "PAUSE"), owner)
        val v = json(call("/api/v1/stores/$store/sale-batches/$batch", token = owner))["version"].asLong()
        assertEquals(409, call("/api/v1/stores/$store/sale-batches/$batch", "PATCH", mapOf("version" to v, "sellingPrice" to 45000), owner).statusCode()) // has orders
        val r = call("/api/v1/admin/financial-policies", "POST", mapOf("basedOnVersion" to 1, "commissionRate" to 0.2, "noShowRetentionRate" to 0.3,
            "disputeWindowHours" to 48, "cancellationTerms" to "x", "refundTerms" to "y", "reason" to "z"), admin)
        assertEquals(201, r.statusCode(), r.body())
        val o = json(call("/api/v1/orders/$id", token = c))
        assertEquals(1, o["policy"]["version"].asInt()); assertEquals(0.5, o["policy"]["noShowRetentionRate"].asDouble()); assertEquals(30000, o["unitPrice"].asInt())
        call("/api/v1/stores/$store/sale-batches/$batch/status", "POST", mapOf("action" to "RESUME"), owner)
        val stale = placeOrder(c, batch, 1, policyVersion = 1)
        assertEquals("POLICY_CHANGED", json(stale)["code"].asString()); assertEquals("2", json(stale)["fieldErrors"][0]["message"].asString())
        assertEquals(201, placeOrder(c, batch, 1, policyVersion = 2).statusCode())
    }

    @Test fun `AC-07 closed offers, quantities, own store, auth`() {
        val batch = setup(); val c = user("customer@example.test")
        assertField(placeOrder(c, batch, 0), "quantity"); assertField(placeOrder(c, batch, 6), "quantity")
        assertEquals("OWN_STORE", json(placeOrder(owner, batch))["code"].asString())
        assertEquals("OWN_STORE", json(preview(owner, batch, 1))["code"].asString())
        call("/api/v1/stores/$store/sale-batches/$batch/status", "POST", mapOf("action" to "PAUSE"), owner)
        assertEquals("BATCH_NOT_OPEN", json(placeOrder(c, batch))["code"].asString())
        call("/api/v1/stores/$store/sale-batches/$batch/status", "POST", mapOf("action" to "RESUME"), owner)
        jdbc.update("update sale_batch set order_deadline=now()-interval '1 minute' where id=?", batch)
        assertEquals("ORDER_DEADLINE_PASSED", json(placeOrder(c, batch))["code"].asString())
        val batch2 = openOffer(store, owner)
        jdbc.update("update store set status='SUSPENDED' where id=?", UUID.fromString(store))
        assertEquals("STORE_NOT_ACTIVE", json(placeOrder(c, batch2))["code"].asString())
        assertEquals(404, placeOrder(c, UUID.randomUUID()).statusCode())
        assertEquals(401, placeOrder("", batch2).statusCode().let { if (it == 401) 401 else it })
        assertEquals(0, count("select count(*) from customer_order"))
    }

    @Test fun `AC-08 customers only see their own orders`() {
        val batch = setup(); val c = user("customer@example.test"); val other = user("other@example.test")
        val id = json(placeOrder(c, batch))["id"].asString()
        assertEquals(404, call("/api/v1/orders/$id", token = other).statusCode()); assertEquals(404, call("/api/v1/orders/$id", token = owner).statusCode())
        assertEquals(401, call("/api/v1/orders/$id").statusCode())
    }
}
