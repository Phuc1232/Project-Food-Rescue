// AC-01..07 (T5.1): SearchTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

class SearchTests : StoreTestFixtures() {
    private lateinit var owner: String; private lateinit var admin: String; private lateinit var customer: String
    private fun shop(name: String, email: String, lat: Double, lng: Double): String {
        val o = if (email == "owner@example.test") owner else user(email)
        val id = store(o, admin, name); openAllWeek(id, o)
        jdbc.update("update store set latitude=?, longitude=? where id=?", lat, lng, UUID.fromString(id)); return id
    }
    private fun ownerToken(storeId: String) = login(jdbc.queryForObject("select a.login_identifier from store s join account a on a.id=s.owner_id where s.id=?",
        String::class.java, UUID.fromString(storeId))!!)["accessToken"].asString()
    /** OPEN batch with [boxes] sellable boxes; returns batch id. */
    private fun offer(storeId: String, price: Int = 30000, category: String = "BAKERY", boxes: Int = 1, startHour: Int = 10, publish: Boolean = true, cover: Boolean = false): String {
        val o = ownerToken(storeId)
        val t = json(call("/api/v1/stores/$storeId/box-templates", "POST", mapOf("name" to "Hộp $category $price", "categoryCode" to category,
            "description" to "Mô tả dự kiến", "safetyWarnings" to "Có gluten", "defaultSellingPrice" to price, "defaultMinimumValue" to 60000) +
            (if (cover) mapOf("coverFileId" to imageId(o)) else emptyMap()), o))["id"].asString()
        val b = json(call("/api/v1/stores/$storeId/sale-batches", "POST", mapOf("templateId" to t, "orderDeadline" to tomorrowAt(startHour - 1),
            "pickupStartsAt" to tomorrowAt(startHour), "pickupEndsAt" to tomorrowAt(startHour + 2)), o))["id"].asString()
        val email = jdbc.queryForObject("select a.login_identifier from store s join account a on a.id=s.owner_id where s.id=?", String::class.java, UUID.fromString(storeId))!!
        repeat(boxes) { box(storeId, UUID.fromString(b), packerEmail = email) }
        if (publish) assertEquals(200, call("/api/v1/stores/$storeId/sale-batches/$b/status", "POST", mapOf("action" to "PUBLISH"), o).statusCode())
        return b
    }
    private fun search(q: String) = call("/api/v1/surprise-boxes?$q", token = customer)
    private fun ids(q: String) = items(json(search(q))["content"]).map { it["batchId"].asString() }
    private fun setup() { owner = user("owner@example.test"); admin = makeAdmin("admin@example.test"); customer = user("customer@example.test") }
    private val nearQ = "lat=10.7770&lng=106.7010&radiusKm=3"

    @Test fun `AC-01 AC-02 only open, sellable offers of active stores nearby`() {
        setup()
        val near = shop("Gần", "owner@example.test", 10.7769, 106.7009); val far = shop("Xa", "far@example.test", 21.0285, 105.8542)
        val ok = offer(near, boxes = 2); val farOffer = offer(far)
        val paused = offer(near); call("/api/v1/stores/$near/sale-batches/$paused/status", "POST", mapOf("action" to "PAUSE"), owner)
        val draft = offer(near, publish = false)
        val soldOut = offer(near); jdbc.update("update physical_box set status='RESERVED' where current_batch_id=?", UUID.fromString(soldOut))
        val expired = offer(near); jdbc.update("update sale_batch set order_deadline=now()-interval '1 minute' where id=?", UUID.fromString(expired))
        val r = json(search(nearQ)); assertEquals(1, r["totalElements"].asInt())
        val o = r["content"][0]; assertEquals(ok, o["batchId"].asString()); assertEquals(2, o["available"].asInt()); assertEquals(30000, o["sellingPrice"].asInt())
        assertTrue(o["distanceKm"].asDouble() < 0.1)
        assertEquals(setOf(ok, farOffer), ids("size=50").toSet())
        assertEquals(200, setStatus(near, admin, "SUSPEND").statusCode())
        assertEquals(listOf(farOffer), ids("size=50"))
        assertFalse(listOf(paused, draft, soldOut, expired).any { it in ids("size=50") })
    }

    @Test fun `AC-03 filters and sorting`() {
        setup()
        val s = shop("Gần", "owner@example.test", 10.7769, 106.7009); val s2 = shop("Bên cạnh", "next@example.test", 10.79, 106.71)
        val cheap = offer(s, price = 20000); val dear = offer(s2, price = 50000); val meal = offer(s, price = 35000, category = "COOKED_MEAL", startHour = 16)
        assertEquals(listOf(cheap, meal, dear), ids("sort=price"))
        assertEquals(listOf(meal), ids("categoryCode=COOKED_MEAL"))
        assertEquals(listOf(cheap, meal), ids("maxPrice=40000&sort=price"))
        assertEquals(listOf(dear), ids("minPrice=40000"))
        val from = URLEncoder.encode(tomorrowAt(15), "UTF-8"); val to = URLEncoder.encode(tomorrowAt(19), "UTF-8")
        assertEquals(listOf(meal), ids("pickupFrom=$from&pickupTo=$to"))
        val byDistance = ids("$nearQ&sort=distance"); assertEquals(dear, byDistance.last()); assertEquals(setOf(cheap, meal), byDistance.take(2).toSet())
    }

    @Test fun `AC-04 invalid parameters`() {
        setup()
        val from = URLEncoder.encode(tomorrowAt(15), "UTF-8"); val to = URLEncoder.encode(tomorrowAt(12), "UTF-8")
        for ((q, f) in listOf("minPrice=50000&maxPrice=1000" to "maxPrice", "pickupFrom=$from&pickupTo=$to" to "pickupTo", "lat=10.7" to "lng",
            "lat=10.7&lng=106.7&radiusKm=80" to "radiusKm", "size=51" to "size", "sort=distance" to "sort", "minPrice=-1" to "minPrice"))
            assertField(search(q), f)
    }

    @Test fun `AC-05 detail shows safety, never items`() {
        setup()
        val s = shop("Gần", "owner@example.test", 10.7769, 106.7009)
        val b = offer(s)
        val item = json(call("/api/v1/stores/$s/menu-items", "POST", mapOf("name" to "Bánh đậu phộng bí mật", "categoryCode" to "BAKERY", "listedPrice" to 80000,
            "ingredients" to "x", "safetyWarnings" to "Có đậu phộng"), owner))["item"]["id"].asString()
        val boxId = jdbc.queryForObject("select id from physical_box where current_batch_id=?", UUID::class.java, UUID.fromString(b))!!
        jdbc.update("""insert into box_item(id,created_at,store_id,box_id,menu_item_id,quantity,item_name_snapshot,unit_price_snapshot,safety_warnings_snapshot,expires_at)
            values (?,now(),?,?,?,1,'Bánh đậu phộng bí mật',80000,'Có đậu phộng',now()+interval '3 day')""", UUID.randomUUID(), UUID.fromString(s), boxId, UUID.fromString(item))
        val r = call("/api/v1/surprise-boxes/$b", token = customer); assertEquals(200, r.statusCode(), r.body())
        val d = json(r)
        assertTrue(d["orderable"].asBoolean()); assertEquals(listOf("Có gluten", "Có đậu phộng"), items(d["safetyWarnings"]).map { it.asString() })
        assertEquals("Mô tả dự kiến", d["description"].asString()); assertTrue(d["directionsUrl"].asString().startsWith("https://www.google.com/maps/dir/?api=1&destination=10.7769,106.7009"))
        for (secret in listOf("Bánh đậu phộng bí mật", "80000", "BX-", "boxCode", "items")) assertFalse(r.body().contains(secret), secret)
        call("/api/v1/stores/$s/sale-batches/$b/status", "POST", mapOf("action" to "PAUSE"), owner)
        assertEquals("PAUSED", json(call("/api/v1/surprise-boxes/$b", token = customer))["unavailableReason"].asString())
        call("/api/v1/stores/$s/sale-batches/$b/status", "POST", mapOf("action" to "RESUME"), owner)
        jdbc.update("update physical_box set status='ALLOCATED' where current_batch_id=?", UUID.fromString(b))
        assertEquals("SOLD_OUT", json(call("/api/v1/surprise-boxes/$b", token = customer))["unavailableReason"].asString())
        val draft = offer(s, publish = false)
        assertEquals(404, call("/api/v1/surprise-boxes/$draft", token = customer).statusCode())
        assertEquals(404, call("/api/v1/surprise-boxes/${UUID.randomUUID()}", token = customer).statusCode())
        setStatus(s, admin, "SUSPEND"); assertEquals(404, call("/api/v1/surprise-boxes/$b", token = customer).statusCode())
    }

    @Test fun `AC-06 store search and map`() {
        setup()
        val a = shop("Bánh mì Cô Ba", "owner@example.test", 10.7769, 106.7009); val b = shop("Cơm tấm", "b@example.test", 10.78, 106.70)
        val far = shop("Bánh cuốn Hà Nội", "far@example.test", 21.0285, 105.8542)
        offer(a); store(user("pending@example.test"), null, "Bánh chờ duyệt")
        val byName = items(json(call("/api/v1/store-search?q=b%C3%A1nh", token = customer))["content"])
        assertEquals(setOf(a, far), byName.map { it["id"].asString() }.toSet())
        assertEquals(1, byName.first { it["id"].asString() == a }["openOffers"].asInt())
        val near = items(json(call("/api/v1/store-search?$nearQ", token = customer))["content"]).map { it["id"].asString() }
        assertEquals(listOf(a, b), near)
        val pins = items(json(call("/api/v1/store-map?minLat=10.7&maxLat=10.8&minLng=106.6&maxLng=106.8", token = customer)))
        assertEquals(setOf(a, b), pins.map { it["id"].asString() }.toSet()); assertTrue(pins.first { it["id"].asString() == a }["hasOpenOffers"].asBoolean())
        assertField(call("/api/v1/store-map?minLat=10&maxLat=13&minLng=106&maxLng=107", token = customer), "bounds")
        assertEquals(400, call("/api/v1/store-map?minLat=10", token = customer).statusCode())
    }

    @Test fun `AC-07 cover and authentication`() {
        setup()
        val s = shop("Gần", "owner@example.test", 10.7769, 106.7009)
        val b = offer(s, cover = true); val draft = offer(s, publish = false, cover = true)
        val img = download("/api/v1/surprise-boxes/$b/cover", customer); assertEquals(200, img.statusCode()); assertEquals("image/png", img.headers().firstValue("Content-Type").get())
        assertEquals(404, download("/api/v1/surprise-boxes/$draft/cover", customer).statusCode())
        assertTrue(json(search("size=5"))["content"][0]["hasCover"].asBoolean())
        for (p in listOf("/api/v1/surprise-boxes", "/api/v1/surprise-boxes/$b", "/api/v1/store-search", "/api/v1/store-map?minLat=10&maxLat=11&minLng=106&maxLng=107"))
            assertEquals(401, call(p).statusCode(), p)
    }
}
