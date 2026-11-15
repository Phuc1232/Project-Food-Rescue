// AC-01..09 (T4.3): SaleBatchTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SaleBatchTests : StoreTestFixtures() {
    private fun base(storeId: String) = "/api/v1/stores/$storeId/sale-batches"
    private data class Ctx(val owner: String, val admin: String, val store: String, val template: String)
    private fun setup(): Ctx {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test"); val store = store(owner, admin)
        openAllWeek(store, owner)
        val t = call("/api/v1/stores/$store/box-templates", "POST", mapOf("name" to "Hộp bánh", "categoryCode" to "BAKERY", "description" to "Bánh",
            "safetyWarnings" to "Có gluten", "defaultSellingPrice" to 30000, "defaultMinimumValue" to 60000), owner)
        return Ctx(owner, admin, store, json(t)["id"].asString())
    }
    private fun batchBody(c: Ctx, extra: Map<String, Any?> = emptyMap()) = mapOf("templateId" to c.template, "orderDeadline" to tomorrowAt(9),
        "pickupStartsAt" to tomorrowAt(10), "pickupEndsAt" to tomorrowAt(12)) + extra
    private fun create(c: Ctx, extra: Map<String, Any?> = emptyMap()) = call(base(c.store), "POST", batchBody(c, extra), c.owner)
    private fun action(c: Ctx, id: String, a: String, token: String = c.owner) = call("${base(c.store)}/$id/status", "POST", mapOf("action" to a), token)
    private fun counts(c: Ctx, id: String) = json(call("${base(c.store)}/$id/box-counts", token = c.owner))

    @Test fun `AC-01 create a draft batch with template defaults and no stock`() {
        val c = setup()
        val r = create(c); assertEquals(201, r.statusCode(), r.body())
        val b = json(r)
        assertEquals("DRAFT", b["status"].asString()); assertEquals(30000, b["sellingPrice"].asInt()); assertEquals(60000, b["minimumValue"].asInt())
        assertEquals("Có gluten", b["safetyWarnings"].asString()); assertEquals(0, b["available"].asInt())
        assertEquals(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).toString(), b["saleDate"].asString())
        assertEquals(0, counts(c, b["id"].asString())["packed"].asInt())
        val custom = json(create(c, mapOf("sellingPrice" to 40000, "minimumValue" to 80000))); assertEquals(40000, custom["sellingPrice"].asInt())
    }

    @Test fun `AC-02 invalid batches`() {
        val c = setup()
        val inactive = json(call("/api/v1/stores/${c.store}/box-templates", "POST", mapOf("name" to "Cũ", "categoryCode" to "BAKERY", "description" to "x",
            "safetyWarnings" to "x", "defaultSellingPrice" to 1000, "defaultMinimumValue" to 1000), c.owner))
        call("/api/v1/stores/${c.store}/box-templates/${inactive["id"].asString()}/status", "POST", mapOf("version" to inactive["version"].asLong(), "active" to false, "reason" to "x"), c.owner)
        val cases = listOf(mapOf("sellingPrice" to 0) to "sellingPrice", mapOf("minimumValue" to 60000.5) to "minimumValue",
            mapOf("minimumValue" to 10000) to "minimumValue", mapOf("orderDeadline" to tomorrowAt(11)) to "orderDeadline",
            mapOf("pickupEndsAt" to tomorrowAt(9)) to "pickupEndsAt", mapOf("pickupStartsAt" to tomorrowAt(5), "orderDeadline" to tomorrowAt(4)) to "pickupStartsAt",
            mapOf("pickupEndsAt" to tomorrowAt(22)) to "pickupStartsAt",
            mapOf("orderDeadline" to Instant.now().minusSeconds(3600).toString()) to "orderDeadline",
            mapOf("templateId" to inactive["id"].asString()) to "templateId", mapOf("templateId" to UUID.randomUUID().toString()) to "templateId")
        for ((extra, field) in cases) assertField(create(c, extra), field)
        assertEquals(0, count("select count(*) from sale_batch"))
    }

    @Test fun `AC-03 AC-07 publishing needs a sellable box and counts come from real boxes`() {
        val c = setup()
        val id = json(create(c))["id"].asString(); val bid = UUID.fromString(id)
        val r = action(c, id, "PUBLISH"); assertEquals(409, r.statusCode()); assertEquals("NO_ELIGIBLE_BOX", json(r)["code"].asString())
        box(c.store, bid, value = 50000)                                   // below minimum value
        box(c.store, bid, safeUntil = Instant.parse(tomorrowAt(11)))       // unsafe before pickup end (BR-18)
        assertEquals("NO_ELIGIBLE_BOX", json(action(c, id, "PUBLISH"))["code"].asString())
        assertEquals("DRAFT", json(call("${base(c.store)}/$id", token = c.owner))["status"].asString())
        val ok1 = box(c.store, bid); box(c.store, bid, value = 90000); box(c.store, bid, status = "RESERVED"); box(c.store, bid, status = "ALLOCATED")
        box(c.store, bid, status = "DELIVERED"); box(c.store, bid, status = "BLOCKED"); box(c.store, bid, status = "AWAITING_INSPECTION")
        val k = counts(c, id)
        assertEquals(mapOf("packed" to 9, "eligible" to 4, "held" to 1, "allocated" to 1, "delivered" to 1, "blocked" to 1, "awaitingInspection" to 1,
            "ineligible" to 2, "available" to 2), listOf("packed", "eligible", "held", "allocated", "delivered", "blocked", "awaitingInspection", "ineligible", "available")
            .associateWith { k[it].asInt() })
        val published = action(c, id, "PUBLISH"); assertEquals(200, published.statusCode(), published.body())
        assertEquals("OPEN", json(published)["status"].asString()); assertEquals(2, json(published)["available"].asInt())
        // raising the minimum value (while paused) drops boxes that no longer qualify (UC-60 A1)
        assertEquals(200, action(c, id, "PAUSE").statusCode())
        val v = json(call("${base(c.store)}/$id", token = c.owner))["version"].asLong()
        assertEquals(200, call("${base(c.store)}/$id", "PATCH", mapOf("version" to v, "minimumValue" to 80000), c.owner).statusCode())
        assertEquals(1, counts(c, id)["available"].asInt()); assertNotNull(ok1)
    }

    @Test fun `AC-04 suspended store and passed deadline cannot open`() {
        val c = setup()
        val before = json(create(c))["id"].asString()
        assertEquals(200, setStatus(c.store, c.admin, "SUSPEND").statusCode())
        // T3.3: suspension blocked the draft created before it
        assertEquals("BLOCKED", batchStatus(UUID.fromString(before))); assertEquals("CONFLICT", json(action(c, before, "PUBLISH"))["code"].asString())
        val id = json(create(c))["id"].asString(); box(c.store, UUID.fromString(id))
        assertEquals("STORE_NOT_ACTIVE", json(action(c, id, "PUBLISH"))["code"].asString())
        assertEquals(200, setStatus(c.store, c.admin, "REACTIVATE", "OK").statusCode())
        jdbc.update("update sale_batch set order_deadline=now()-interval '1 minute' where id=?", UUID.fromString(id))
        assertEquals("ORDER_DEADLINE_PASSED", json(action(c, id, "PUBLISH"))["code"].asString())
    }

    @Test fun `AC-05 transitions`() {
        val c = setup()
        val id = json(create(c))["id"].asString(); box(c.store, UUID.fromString(id))
        assertEquals(409, action(c, id, "PAUSE").statusCode()); assertEquals(409, action(c, id, "RESUME").statusCode())
        assertEquals(listOf(200, 409), concurrently({ action(c, id, "PUBLISH") }, { action(c, id, "PUBLISH") }))
        assertEquals(409, action(c, id, "RESUME").statusCode())
        assertEquals(200, action(c, id, "PAUSE").statusCode()); assertEquals(200, action(c, id, "RESUME").statusCode())
        assertEquals(200, action(c, id, "END").statusCode())
        for (a in listOf("PUBLISH", "PAUSE", "RESUME", "END")) assertEquals(409, action(c, id, a).statusCode(), a)
        assertEquals(1, count("select count(*) from audit_log where action='SALE_BATCH_PUBLISHED'"))
    }

    @Test fun `AC-06 edits only while draft or paused, orders freeze the terms`() {
        val c = setup(); user("customer@example.test")
        val b = json(create(c)); val id = b["id"].asString(); box(c.store, UUID.fromString(id))
        val r = call("${base(c.store)}/$id", "PATCH", mapOf("version" to b["version"].asLong(), "sellingPrice" to 35000, "safetyWarnings" to "Có gluten, sữa"), c.owner)
        assertEquals(200, r.statusCode(), r.body()); assertEquals(35000, json(r)["sellingPrice"].asInt())
        assertEquals(409, call("${base(c.store)}/$id", "PATCH", mapOf("version" to b["version"].asLong(), "sellingPrice" to 36000), c.owner).statusCode())
        assertField(call("${base(c.store)}/$id", "PATCH", mapOf("version" to json(r)["version"].asLong(), "safetyWarnings" to " "), c.owner), "safetyWarnings")
        assertEquals(200, action(c, id, "PUBLISH").statusCode())
        val open = json(call("${base(c.store)}/$id", token = c.owner))
        assertEquals(409, call("${base(c.store)}/$id", "PATCH", mapOf("version" to open["version"].asLong(), "sellingPrice" to 36000), c.owner).statusCode())
        assertEquals(200, action(c, id, "PAUSE").statusCode())
        order(c.store, UUID.fromString(id), "customer@example.test")
        val paused = json(call("${base(c.store)}/$id", token = c.owner))
        assertEquals(409, call("${base(c.store)}/$id", "PATCH", mapOf("version" to paused["version"].asLong(), "pickupEndsAt" to tomorrowAt(13)), c.owner).statusCode())
        assertEquals(200, call("${base(c.store)}/$id", "PATCH", mapOf("version" to paused["version"].asLong(), "orderDeadline" to tomorrowAt(9, 30)), c.owner).statusCode())
        assertEquals(1, count("select count(*) from audit_log where action='SALE_BATCH_UPDATED' and before_state like '%sellingPrice%'"))
    }

    @Test fun `AC-08 admin blocks a batch for good`() {
        val c = setup()
        val id = json(create(c))["id"].asString(); box(c.store, UUID.fromString(id)); action(c, id, "PUBLISH")
        assertEquals(403, call("/api/v1/admin/sale-batches/$id/block", "POST", mapOf("reason" to "x"), c.owner).statusCode())
        assertField(call("/api/v1/admin/sale-batches/$id/block", "POST", mapOf("reason" to ""), c.admin), "reason")
        val r = call("/api/v1/admin/sale-batches/$id/block", "POST", mapOf("reason" to "Hộp vi phạm ATTP"), c.admin)
        assertEquals(200, r.statusCode()); assertEquals("BLOCKED", json(r)["status"].asString()); assertEquals("Hộp vi phạm ATTP", json(r)["statusReason"].asString())
        for (a in listOf("PUBLISH", "RESUME", "END")) assertEquals(409, action(c, id, a).statusCode())
        assertEquals(409, call("/api/v1/admin/sale-batches/$id/block", "POST", mapOf("reason" to "x"), c.admin).statusCode())
        assertEquals(404, call("/api/v1/admin/sale-batches/${UUID.randomUUID()}/block", "POST", mapOf("reason" to "x"), c.admin).statusCode())
    }

    @Test fun `AC-09 access`() {
        val c = setup(); val id = json(create(c))["id"].asString()
        val packer = staffWith(c.store, c.owner, "packer@example.test", listOf("BOX_PACK"))
        val viewer = staffWith(c.store, c.owner, "viewer@example.test", listOf("MENU_VIEW"))
        val customer = user("customer@example.test")
        assertEquals(200, call(base(c.store), token = packer).statusCode()); assertEquals(200, call("${base(c.store)}/$id/box-counts", token = c.admin).statusCode())
        assertEquals(403, call(base(c.store), "POST", batchBody(c), packer).statusCode()); assertEquals(403, action(c, id, "PUBLISH", packer).statusCode())
        for (t in listOf(viewer, customer)) assertEquals(403, call(base(c.store), token = t).statusCode())
        assertEquals(401, call(base(c.store)).statusCode())
    }
}
