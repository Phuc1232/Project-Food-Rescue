// AC-01..10 (T4.1): MenuTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class MenuTests : StoreTestFixtures() {
    private fun menu(storeId: String) = "/api/v1/stores/$storeId/menu-items"
    private fun item(name: String = "Bánh mì thịt", price: Any? = 25000, extra: Map<String, Any?> = emptyMap()) = mapOf(
        "name" to name, "categoryCode" to "BAKERY", "listedPrice" to price, "ingredients" to "Bánh mì, thịt, pate",
        "safetyWarnings" to "Có gluten") + extra
    private fun create(storeId: String, token: String, body: Map<String, Any?> = item()) = call(menu(storeId), "POST", body, token)
    private fun setup(): Triple<String, String, String> {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test"); return Triple(owner, admin, store(owner, admin))
    }
    @Test fun `AC-01 owner creates an item with its initial price version`() {
        val (owner, _, id) = setup()
        val r = create(id, owner); assertEquals(201, r.statusCode(), r.body())
        val d = json(r)
        assertEquals("ACTIVE", d["item"]["status"].asString()); assertEquals(25000, d["item"]["listedPrice"].asInt())
        assertEquals("BAKERY", d["item"]["categoryCode"].asString()); assertEquals(1, d["priceHistory"].size())
        assertEquals("Initial price", d["priceHistory"][0]["reason"].asString())
        val draft = create(id, owner, item("Bánh bao", 15000, mapOf("active" to false)))
        assertEquals("DISCONTINUED", json(draft)["item"]["status"].asString())
        assertEquals(2, count("select count(*) from audit_log where action='MENU_ITEM_CREATED'"))
    }

    @Test fun `AC-02 invalid items are rejected`() {
        val (owner, _, id) = setup()
        val cases = listOf(item(price = 0) to "listedPrice", item(price = -5) to "listedPrice", item(price = 1500.5) to "listedPrice",
            item(price = 100000001) to "listedPrice", item(name = " ") to "name", item(extra = mapOf("ingredients" to "")) to "ingredients",
            item(extra = mapOf("safetyWarnings" to null)) to "safetyWarnings", item(extra = mapOf("categoryCode" to "PIZZA")) to "categoryCode")
        for ((body, field) in cases) assertField(create(id, owner, body), field)
        assertEquals(400, create(id, owner, item(extra = mapOf("status" to "ACTIVE"))).statusCode())
        assertEquals(0, count("select count(*) from menu_item"))
    }

    @Test fun `AC-03 AC-05 price changes need a reason and history is append-only`() {
        val (owner, _, id) = setup()
        val d = json(create(id, owner)); val itemId = d["item"]["id"].asString(); val v = d["item"]["version"].asLong()
        assertField(call("${menu(id)}/$itemId", "PATCH", mapOf("version" to v, "listedPrice" to 30000), owner), "priceChangeReason")
        for (bad in listOf(1500.5, 0, -1, 100000001))
            assertField(call("${menu(id)}/$itemId", "PATCH", mapOf("version" to v, "listedPrice" to bad, "priceChangeReason" to "x"), owner), "listedPrice")
        val r = call("${menu(id)}/$itemId", "PATCH", mapOf("version" to v, "listedPrice" to 30000, "priceChangeReason" to "Giá nguyên liệu tăng"), owner)
        assertEquals(200, r.statusCode(), r.body())
        val h = json(r)["priceHistory"]; assertEquals(2, h.size())
        assertEquals(25000, h[0]["previousPrice"].asInt()); assertEquals(30000, h[0]["newPrice"].asInt()); assertEquals("Giá nguyên liệu tăng", h[0]["reason"].asString())
        assertEquals(accountId("owner@example.test").toString(), h[0]["changedById"].asString())
        val v2 = json(r)["item"]["version"].asLong()
        val renamed = call("${menu(id)}/$itemId", "PATCH", mapOf("version" to v2, "name" to "Bánh mì đặc biệt", "listedPrice" to 30000), owner)
        assertEquals(200, renamed.statusCode()); assertEquals(2, json(renamed)["priceHistory"].size())
        for (sql in listOf("update menu_price_history set new_price=1", "delete from menu_price_history"))
            assertThrows(Exception::class.java) { jdbc.update(sql) }
        assertEquals(2, count("select count(*) from menu_price_history"))
    }

    @Test fun `AC-04 stale versions and concurrent edits`() {
        val (owner, _, id) = setup()
        val d = json(create(id, owner)); val itemId = d["item"]["id"].asString(); val v = d["item"]["version"].asLong()
        val edit = { p: Int -> call("${menu(id)}/$itemId", "PATCH", mapOf("version" to v, "listedPrice" to p, "priceChangeReason" to "x"), owner) }
        assertEquals(listOf(200, 409), concurrently({ edit(26000) }, { edit(27000) }))
        assertEquals(2, count("select count(*) from menu_price_history"))
        assertEquals(409, edit(28000).statusCode())
    }

    @Test fun `AC-06 discontinue and reopen with a reason`() {
        val (owner, _, id) = setup()
        val d = json(create(id, owner)); val itemId = d["item"]["id"].asString()
        fun st(status: String, reason: String? = "Hết nguyên liệu") =
            call("${menu(id)}/$itemId/status", "POST", mapOf("version" to json(call("${menu(id)}/$itemId", token = owner))["item"]["version"].asLong(), "status" to status, "reason" to reason), owner)
        assertField(st("DISCONTINUED", " "), "reason")
        assertEquals("DISCONTINUED", json(st("DISCONTINUED"))["item"]["status"].asString())
        assertEquals(409, st("DISCONTINUED").statusCode())
        jdbc.update("update food_category set active=false where code='BAKERY'")
        try { assertField(st("ACTIVE", "Mở lại"), "status") } finally { jdbc.update("update food_category set active=true where code='BAKERY'") }
        assertEquals("ACTIVE", json(st("ACTIVE", "Mở lại"))["item"]["status"].asString())
        assertEquals(2, count("select count(*) from audit_log where action='MENU_ITEM_STATUS_CHANGED'"))
        assertEquals(1, json(call("${menu(id)}?status=ACTIVE", token = owner)).size())
    }

    @Test fun `AC-07 staff read with MENU_VIEW only, customers and others are denied`() {
        val (owner, admin, id) = setup()
        val itemId = json(create(id, owner))["item"]["id"].asString()
        val viewer = staffWith(id, owner, "viewer@example.test", listOf("MENU_VIEW"))
        val packer = staffWith(id, owner, "packer@example.test", listOf("BOX_PACK"))
        val customer = user("customer@example.test"); val other = user("other@example.test"); store(other, admin, "Khác")
        assertEquals(200, call(menu(id), token = viewer).statusCode()); assertEquals(200, call("${menu(id)}/$itemId", token = viewer).statusCode())
        assertEquals(403, create(id, viewer).statusCode())
        assertEquals(403, call("${menu(id)}/$itemId", "PATCH", mapOf("version" to 0, "listedPrice" to 1000, "priceChangeReason" to "x"), viewer).statusCode())
        assertEquals(403, call("${menu(id)}/$itemId/status", "POST", mapOf("version" to 0, "status" to "DISCONTINUED", "reason" to "x"), viewer).statusCode())
        for (t in listOf(packer, customer, other)) assertEquals(403, call(menu(id), token = t).statusCode())
        assertEquals(401, call(menu(id)).statusCode())
    }

    @Test fun `AC-08 AC-09 admins see everything, documents are versioned`() {
        val (owner, admin, id) = setup(); val stranger = user("stranger@example.test")
        val itemId = json(create(id, owner))["item"]["id"].asString()
        val docs = "/api/v1/stores/$id/price-documents"
        val f1 = fileId(owner, "bang-gia.pdf")
        val first = call(docs, "POST", mapOf("fileId" to f1, "description" to "Bảng giá tại quầy", "appliesFrom" to "2026-11-01"), owner)
        assertEquals(201, first.statusCode(), first.body()); val firstId = json(first)["id"].asString()
        val second = call(docs, "POST", mapOf("fileId" to fileId(owner, "bang-gia-2.pdf"), "menuItemId" to itemId, "description" to "Ảnh menu mới",
            "appliesFrom" to "2026-11-15", "replacesDocumentId" to firstId), owner)
        assertEquals(201, second.statusCode(), second.body())
        val list = json(call(docs, token = owner)); assertEquals(2, list.size())
        assertEquals(json(second)["id"].asString(), items(list).first { it["id"].asString() == firstId }["replacedById"].asString())
        assertEquals(409, call(docs, "POST", mapOf("fileId" to fileId(owner), "description" to "x", "appliesFrom" to "2026-11-15", "replacesDocumentId" to firstId), owner).statusCode())
        assertField(call(docs, "POST", mapOf("fileId" to fileId(stranger), "description" to "x", "appliesFrom" to "2026-11-15"), owner), "fileId")
        // admin view (UC-75)
        assertEquals(1, json(call(menu(id), token = admin)).size())
        val d = json(call("${menu(id)}/$itemId", token = admin)); assertEquals(1, d["priceHistory"].size()); assertEquals(2, d["documents"].size())
        assertEquals(200, download("/api/v1/files/$f1/content", admin).statusCode())
        assertEquals(404, download("/api/v1/files/$f1/content", stranger).statusCode())
        assertEquals(403, call(docs, token = stranger).statusCode())
    }

    @Test fun `AC-10 categories are seeded`() {
        val token = user("u@example.test")
        val c = json(call("/api/v1/food-categories", token = token))
        assertEquals(9, c.size()); assertTrue(items(c).any { it["code"].asString() == "BAKERY" && it["name"].asString() == "Bánh mì, bánh ngọt" })
        assertEquals(401, call("/api/v1/food-categories").statusCode())
    }
}
