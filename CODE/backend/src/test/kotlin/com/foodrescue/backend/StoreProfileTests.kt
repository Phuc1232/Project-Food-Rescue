// AC-01,02,03,04,08,11,13 (T3.3): StoreProfileTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class StoreProfileTests : StoreTestFixtures() {
    private val hours = listOf(mapOf("dayOfWeek" to 1, "opensAt" to "06:00", "closesAt" to "10:00"),
        mapOf("dayOfWeek" to 1, "opensAt" to "16:00", "closesAt" to "21:00"),
        mapOf("dayOfWeek" to 6, "opensAt" to "18:00", "closesAt" to "02:00"))

    @Test fun `AC-01 owner sees the store profile`() {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test")
        val id = store(owner, admin)
        val r = profile(id, owner); assertEquals(200, r.statusCode(), r.body())
        val p = json(r)
        assertEquals("Bánh mì Cô Ba", p["name"].asString()); assertEquals("Bánh mì, bánh ngọt", p["description"].asString())
        assertEquals("12 Lê Lợi, Quận 1, TP.HCM", p["address"].asString()); assertEquals(10.7769, p["latitude"].asDouble())
        assertEquals("Asia/Ho_Chi_Minh", p["timezone"].asString()); assertEquals("ACTIVE", p["status"].asString())
        assertTrue(p["statusReason"].isNull); assertTrue(p["pendingChangeRequest"].isNull); assertTrue(p.has("version"))
        assertEquals("06:00", p["openingHours"][0]["opensAt"].asString()); assertFalse(p["openingHours"][0]["closesNextDay"].asBoolean())
        assertEquals(0, count("select count(*) from audit_log where entity_type='STORE'"))
    }

    @Test fun `AC-02 owner updates description and hours immediately with history`() {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test")
        val id = store(owner, admin); val v0 = version(id, owner)
        val r = patch(id, owner, mapOf("version" to v0, "description" to "  Bánh mì nóng  ", "openingHours" to hours))
        assertEquals(200, r.statusCode(), r.body())
        val p = json(r)
        assertEquals("Bánh mì nóng", p["description"].asString()); assertEquals(3, p["openingHours"].size())
        assertEquals(listOf(1, 2), items(p["openingHours"]).filter { it["dayOfWeek"].asInt() == 1 }.map { it["shiftNumber"].asInt() })
        assertTrue(p["openingHours"][2]["closesNextDay"].asBoolean())
        assertTrue(p["version"].asLong() > v0)
        assertEquals(listOf("06:00:00-10:00:00", "16:00:00-21:00:00", "18:00:00-02:00:00"), jdbc.queryForList(
            "select opens_at::text||'-'||closes_at::text from store_opening_hour order by day_of_week, shift_number", String::class.java))
        val log = jdbc.queryForMap("select before_state, after_state, correlation_id from audit_log where action='STORE_PROFILE_UPDATED'")
        val before = mapper.readTree(log["before_state"] as String); val after = mapper.readTree(log["after_state"] as String)
        assertEquals("Bánh mì, bánh ngọt", before["description"].asString()); assertEquals("Bánh mì nóng", after["description"].asString())
        assertEquals(1, before["openingHours"].size()); assertEquals(3, after["openingHours"].size()); assertNotNull(log["correlation_id"])

        // same data: no new history, version unchanged
        val v1 = p["version"].asLong()
        val same = patch(id, owner, mapOf("version" to v1, "description" to "Bánh mì nóng", "openingHours" to hours))
        assertEquals(200, same.statusCode()); assertEquals(v1, json(same)["version"].asLong())
        assertEquals(1, count("select count(*) from audit_log where action='STORE_PROFILE_UPDATED'"))

        // hours only: version still moves (T3.3 P1); description null clears it
        val hoursOnly = patch(id, owner, mapOf("version" to v1, "openingHours" to hours.take(1)))
        assertEquals(200, hoursOnly.statusCode(), hoursOnly.body()); assertTrue(json(hoursOnly)["version"].asLong() > v1)
        val cleared = patch(id, owner, mapOf("version" to json(hoursOnly)["version"].asLong(), "description" to null))
        assertEquals(200, cleared.statusCode()); assertTrue(json(cleared)["description"].isNull)
        assertEquals(1, json(cleared)["openingHours"].size())
        assertEquals(3, count("select count(*) from audit_log where action='STORE_PROFILE_UPDATED'"))
    }

    @Test fun `AC-03 AC-11 invalid updates name the field and change nothing`() {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test")
        val id = store(owner, admin); val v = version(id, owner)
        fun shift(day: Any?, opens: Any?, closes: Any?) = mapOf("dayOfWeek" to day, "opensAt" to opens, "closesAt" to closes)
        val cases = listOf<Pair<Map<String, Any?>, String>>(
            mapOf("version" to v, "openingHours" to emptyList<Any>()) to "openingHours",
            mapOf("version" to v, "openingHours" to listOf(shift(1, "08:00", "12:00"), shift(1, "11:00", "13:00"))) to "openingHours[1]",
            mapOf("version" to v, "openingHours" to listOf(shift(7, "22:00", "03:00"), shift(1, "02:00", "05:00"))) to "openingHours[1]",
            mapOf("version" to v, "openingHours" to listOf(shift(1, "08:00", "08:00"))) to "openingHours[0]",
            mapOf("version" to v, "openingHours" to listOf(shift(8, "08:00", "12:00"))) to "openingHours[0]",
            mapOf("version" to v, "openingHours" to listOf(shift(1, "8h", "12:00"))) to "openingHours[0]",
            mapOf("version" to v, "openingHours" to listOf(shift(1, "24:00", "12:00"))) to "openingHours[0]",
            mapOf("version" to v, "openingHours" to (1..4).map { shift(2, "0$it:00", "0$it:30") }) to "openingHours[3]",
            mapOf("version" to v, "description" to "x".repeat(2001)) to "description",
            mapOf("description" to "x") to "version",
            mapOf("version" to "1", "description" to "x") to "version",
            mapOf("version" to v, "name" to "Tên mới") to "name",
            mapOf("version" to v, "latitude" to 10.0) to "latitude",
            mapOf("version" to v, "status" to "ACTIVE") to "status",
            mapOf("version" to v, "timezone" to "Asia/Bangkok") to "timezone",
            mapOf("version" to v, "ownerId" to UUID.randomUUID().toString()) to "ownerId")
        for ((body, field) in cases) assertField(patch(id, owner, body), field)
        assertEquals(400, call("/api/v1/stores/$id/profile", "PATCH", "[1]", owner).statusCode())
        assertEquals(v, version(id, owner)); assertEquals(1, count("select count(*) from store_opening_hour"))
        assertEquals(0, count("select count(*) from audit_log where entity_type='STORE'"))
    }

    @Test fun `AC-04 stale version is rejected and concurrent edits have one winner`() {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test")
        val id = store(owner, admin); val v = version(id, owner)
        assertEquals(200, patch(id, owner, mapOf("version" to v, "description" to "A")).statusCode())
        val stale = patch(id, owner, mapOf("version" to v, "description" to "B"))
        assertEquals(409, stale.statusCode()); assertEquals("CONFLICT", json(stale)["code"].asString())
        assertEquals("A", json(profile(id, owner))["description"].asString())

        val v1 = version(id, owner)
        val results = concurrently({ patch(id, owner, mapOf("version" to v1, "openingHours" to hours.take(1))) },
            { patch(id, owner, mapOf("version" to v1, "openingHours" to hours.drop(1))) })
        assertEquals(listOf(200, 409), results)
        assertEquals(2, count("select count(*) from audit_log where action='STORE_PROFILE_UPDATED'"))
    }

    @Test fun `AC-08 only the owner of an approved store manages it`() {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test")
        val other = user("other@example.test"); val staff = user("staff@example.test")
        val id = store(owner, admin); store(other, admin, "Cửa hàng khác")
        jdbc.update("insert into staff_assignment(id,created_at,version,updated_at,account_id,store_id,status,assigned_at) values (?,now(),0,now(),?,?,'ACTIVE',now())",
            UUID.randomUUID(), accountId("staff@example.test"), UUID.fromString(id))
        val paths = listOf("/profile", "/change-requests", "/history").map { "/api/v1/stores/$id$it" }
        for (p in paths) {
            assertEquals(401, call(p).statusCode())
            for (t in listOf(other, staff, admin)) assertEquals(403, call(p, token = t).statusCode(), p)
        }
        assertEquals(403, patch(id, other, mapOf("version" to 0, "description" to "x")).statusCode())
        assertEquals(403, call("/api/v1/stores/$id/change-requests", "POST", mapOf("name" to "x"), staff).statusCode())
        assertEquals(403, profile(UUID.randomUUID().toString(), owner).statusCode())
        // store awaiting approval: no owner context yet (T3.2 Q1)
        val pendingOwner = user("pending@example.test"); val pending = store(pendingOwner, null, "Chờ duyệt")
        assertEquals(403, profile(pending, pendingOwner).statusCode())
    }

    @Test fun `AC-13 history shows who did what without exposing admin ids to owners`() {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test")
        val id = store(owner, admin)
        assertEquals(200, patch(id, owner, mapOf("version" to version(id, owner), "description" to "Mới")).statusCode())
        assertEquals(200, setStatus(id, admin, "SUSPEND").statusCode())
        val r = call("/api/v1/stores/$id/history?size=10", token = owner); assertEquals(200, r.statusCode(), r.body())
        val h = items(json(r)["content"])
        assertEquals(listOf("STORE_SUSPENDED", "STORE_PROFILE_UPDATED"), h.map { it["action"].asString() })
        assertEquals(listOf("ADMIN", "OWNER"), h.map { it["actorRole"].asString() })
        assertEquals("Vi phạm ATTP", h[0]["after"]["reason"].asString()); assertEquals("ACTIVE", h[0]["before"]["status"].asString())
        assertFalse(r.body().contains("actorId")); assertFalse(r.body().contains(accountId("admin@example.test").toString()))
        assertEquals(400, call("/api/v1/stores/$id/history?size=500", token = owner).statusCode())
        val detail = json(call("/api/v1/admin/stores/$id", token = admin))["recentHistory"]
        assertEquals(accountId("admin@example.test").toString(), detail[0]["actorId"].asString())
    }
}
