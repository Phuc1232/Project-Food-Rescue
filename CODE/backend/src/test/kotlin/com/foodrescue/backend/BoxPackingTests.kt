// AC-01..09 (T4.4): BoxPackingTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class BoxPackingTests : StoreTestFixtures() {
    private data class Ctx(val owner: String, val admin: String, val store: String, val batch: String, val bread: String, val cake: String, val packer: String)
    private fun menuItem(store: String, owner: String, name: String, price: Int, warnings: String) = json(call("/api/v1/stores/$store/menu-items", "POST",
        mapOf("name" to name, "categoryCode" to "BAKERY", "listedPrice" to price, "ingredients" to "Bột mì", "safetyWarnings" to warnings), owner))["item"]["id"].asString()
    private fun setup(): Ctx {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test"); val store = store(owner, admin); openAllWeek(store, owner)
        val t = json(call("/api/v1/stores/$store/box-templates", "POST", mapOf("name" to "Hộp bánh", "categoryCode" to "BAKERY", "description" to "Bánh",
            "safetyWarnings" to "Có gluten", "defaultSellingPrice" to 30000, "defaultMinimumValue" to 60000), owner))["id"].asString()
        val batch = json(call("/api/v1/stores/$store/sale-batches", "POST", mapOf("templateId" to t, "orderDeadline" to tomorrowAt(9),
            "pickupStartsAt" to tomorrowAt(10), "pickupEndsAt" to tomorrowAt(12)), owner))["id"].asString()
        val bread = menuItem(store, owner, "Bánh mì", 25000, "Có gluten"); val cake = menuItem(store, owner, "Bánh kem", 40000, "Có sữa, trứng")
        return Ctx(owner, admin, store, batch, bread, cake, staffWith(store, owner, "packer@example.test", listOf("BOX_PACK")))
    }
    private val later: String get() = Instant.parse(tomorrowAt(20)).toString()
    private fun line(id: String, qty: Int = 1, expires: String = later, expected: Int? = null) =
        mapOf("menuItemId" to id, "quantity" to qty, "expiresAt" to expires, "expectedUnitPrice" to expected)
    private fun pack(c: Ctx, items: List<Map<String, Any?>>, token: String = c.packer, key: String = UUID.randomUUID().toString(), passed: Boolean = true,
                     notes: String? = null, batch: String = c.batch) = call("/api/v1/stores/${c.store}/sale-batches/$batch/boxes", "POST",
        mapOf("requestKey" to key, "items" to items, "storageInstructions" to "Ngăn mát 4°C", "inspectionPassed" to passed, "inspectionNotes" to notes), token)
    private fun available(c: Ctx) = json(call("/api/v1/stores/${c.store}/sale-batches/${c.batch}/box-counts", token = c.owner))["available"].asInt()

    @Test fun `AC-01 packing records a priced, inspected, available box`() {
        val c = setup()
        val r = pack(c, listOf(line(c.bread, 2), line(c.cake, 1, tomorrowAt(18)))); assertEquals(201, r.statusCode(), r.body())
        val d = json(r)
        assertEquals("AVAILABLE", d["box"]["status"].asString()); assertEquals(90000, d["box"]["totalListedValue"].asInt())
        assertTrue(d["box"]["boxCode"].asString().matches(Regex("FR-[A-Z2-9]{10}"))); assertTrue(d["box"]["meetsBatchTerms"].asBoolean())
        assertEquals(Instant.parse(tomorrowAt(18)), Instant.parse(d["box"]["safeUntil"].asString()))
        assertEquals(2, d["items"].size()); assertEquals("PASSED", d["inspections"][0]["result"].asString())
        assertEquals(accountId("packer@example.test").toString(), d["inspections"][0]["inspectorId"].asString())
        assertTrue(d["safetyWarnings"].asString().contains("Có sữa, trứng"))
        val second = json(pack(c, listOf(line(c.bread, 3))))["box"]["boxCode"].asString(); assertNotEquals(d["box"]["boxCode"].asString(), second)
        assertEquals(2, available(c))
        assertEquals(200, call("/api/v1/stores/${c.store}/sale-batches/${c.batch}/status", "POST", mapOf("action" to "PUBLISH"), c.owner).statusCode())
        assertEquals(2, count("select count(*) from audit_log where action='BOX_PACKED'"))
    }

    @Test fun `AC-02 below value, unsafe or invalid lines create nothing`() {
        val c = setup()
        assertField(pack(c, listOf(line(c.bread, 2))), "items")                                   // 50 000 < 60 000
        assertField(pack(c, listOf(line(c.bread, 3, tomorrowAt(11)))), "items[0].expiresAt")       // before pickup end (BR-18)
        assertField(pack(c, listOf(line(c.bread, 3, Instant.now().minusSeconds(60).toString()))), "items[0].expiresAt")
        assertField(pack(c, listOf(line(UUID.randomUUID().toString(), 3))), "items[0].menuItemId")
        assertField(pack(c, listOf(line(c.bread, 3), line(c.bread, 1))), "items[1].menuItemId")
        assertField(pack(c, listOf(line(c.bread, 0))), "items[0].quantity")
        val v = json(call("/api/v1/stores/${c.store}/menu-items/${c.cake}", token = c.owner))["item"]["version"].asLong()
        call("/api/v1/stores/${c.store}/menu-items/${c.cake}/status", "POST", mapOf("version" to v, "status" to "DISCONTINUED", "reason" to "Hết"), c.owner)
        assertField(pack(c, listOf(line(c.cake, 2))), "items[0].menuItemId")
        assertField(pack(c, listOf(line(c.bread, 3)), passed = false), "inspectionNotes")
        assertEquals(0, count("select count(*) from physical_box"))
    }

    @Test fun `AC-03 changed price must be reviewed`() {
        val c = setup()
        val r = pack(c, listOf(line(c.bread, 3, expected = 20000))); assertEquals(409, r.statusCode())
        assertEquals("PRICE_CHANGED", json(r)["code"].asString()); assertEquals("25000", json(r)["fieldErrors"][0]["message"].asString())
        assertEquals(0, count("select count(*) from physical_box"))
        assertEquals(201, pack(c, listOf(line(c.bread, 3, expected = 25000))).statusCode())
    }

    @Test fun `AC-04 the same request key never duplicates stock`() {
        val c = setup(); val key = UUID.randomUUID().toString()
        val first = pack(c, listOf(line(c.bread, 3)), key = key); assertEquals(201, first.statusCode())
        val again = pack(c, listOf(line(c.cake, 5)), key = key); assertEquals(200, again.statusCode())
        assertEquals(json(first)["box"]["id"].asString(), json(again)["box"]["id"].asString())
        val key2 = UUID.randomUUID().toString()
        assertEquals(listOf(200, 201), concurrently({ pack(c, listOf(line(c.bread, 3)), key = key2) }, { pack(c, listOf(line(c.bread, 3)), key = key2) }))
        assertEquals(2, count("select count(*) from physical_box"))
    }

    @Test fun `AC-05 later price changes do not touch packed boxes`() {
        val c = setup()
        val boxId = json(pack(c, listOf(line(c.bread, 3))))["box"]["id"].asString()
        val v = json(call("/api/v1/stores/${c.store}/menu-items/${c.bread}", token = c.owner))["item"]["version"].asLong()
        assertEquals(200, call("/api/v1/stores/${c.store}/menu-items/${c.bread}", "PATCH", mapOf("version" to v, "listedPrice" to 10000, "priceChangeReason" to "Giảm giá"), c.owner).statusCode())
        val d = json(call("/api/v1/stores/${c.store}/boxes/$boxId", token = c.owner))
        assertEquals(75000, d["box"]["totalListedValue"].asInt()); assertEquals(25000, d["items"][0]["unitPrice"].asInt())
    }

    @Test fun `AC-06 failed inspection keeps the box out of stock`() {
        val c = setup()
        val r = pack(c, listOf(line(c.bread, 3)), passed = false, notes = "Bánh bị móp")
        assertEquals(201, r.statusCode()); assertEquals("BLOCKED", json(r)["box"]["status"].asString())
        assertEquals("FAILED", json(r)["inspections"][0]["result"].asString()); assertEquals(0, available(c))
    }

    @Test fun `AC-07 quick fill copies items with current prices`() {
        val c = setup()
        val boxId = json(pack(c, listOf(line(c.bread, 2), line(c.cake, 1))))["box"]["id"].asString()
        val v = json(call("/api/v1/stores/${c.store}/menu-items/${c.cake}", token = c.owner))["item"]["version"].asLong()
        call("/api/v1/stores/${c.store}/menu-items/${c.cake}", "PATCH", mapOf("version" to v, "listedPrice" to 45000, "priceChangeReason" to "Tăng"), c.owner)
        call("/api/v1/stores/${c.store}/menu-items/${c.bread}/status", "POST", mapOf("version" to json(call("/api/v1/stores/${c.store}/menu-items/${c.bread}", token = c.owner))["item"]["version"].asLong(),
            "status" to "DISCONTINUED", "reason" to "Hết"), c.owner)
        val p = items(json(call("/api/v1/stores/${c.store}/boxes/$boxId/prefill", token = c.packer)))
        val cake = p.first { it["menuItemId"].asString() == c.cake }; val bread = p.first { it["menuItemId"].asString() == c.bread }
        assertEquals(45000, cake["currentUnitPrice"].asInt()); assertEquals(1, cake["quantity"].asInt()); assertEquals("DISCONTINUED", bread["status"].asString())
        val other = user("other@example.test"); val otherStore = store(other, c.admin, "Khác")
        assertEquals(404, call("/api/v1/stores/$otherStore/boxes/$boxId/prefill", token = other).statusCode())
        assertEquals(404, call("/api/v1/stores/${c.store}/boxes/${UUID.randomUUID()}/prefill", token = c.packer).statusCode())
    }

    @Test fun `AC-08 list and detail`() {
        val c = setup()
        val ok = json(pack(c, listOf(line(c.bread, 3))))["box"]["id"].asString(); pack(c, listOf(line(c.bread, 3)), passed = false, notes = "Hỏng")
        val base = "/api/v1/stores/${c.store}/sale-batches/${c.batch}/boxes"
        assertEquals(2, json(call(base, token = c.owner)).size()); assertEquals(1, json(call("$base?status=BLOCKED", token = c.packer)).size())
        val d = json(call("/api/v1/stores/${c.store}/boxes/$ok", token = c.admin))
        assertEquals("Bánh mì", d["items"][0]["name"].asString()); assertEquals(3, d["items"][0]["quantity"].asInt())
        assertEquals("Ngăn mát 4°C", d["storageInstructions"].asString()); assertTrue(d["allocatedOrderId"].isNull)
    }

    @Test fun `AC-09 access and batch state`() {
        val c = setup()
        val viewer = staffWith(c.store, c.owner, "viewer@example.test", listOf("MENU_VIEW")); val customer = user("customer@example.test")
        for (t in listOf(viewer, customer)) assertEquals(403, pack(c, listOf(line(c.bread, 3)), token = t).statusCode())
        assertEquals(201, pack(c, listOf(line(c.bread, 3)), token = c.owner).statusCode())   // owner may pack (D7)
        assertEquals(403, call("/api/v1/stores/${c.store}/sale-batches/${c.batch}/boxes", token = viewer).statusCode())
        assertEquals(401, call("/api/v1/stores/${c.store}/sale-batches/${c.batch}/boxes").statusCode())
        call("/api/v1/stores/${c.store}/sale-batches/${c.batch}/status", "POST", mapOf("action" to "END"), c.owner)
        assertEquals(409, pack(c, listOf(line(c.bread, 3))).statusCode())
    }
}
