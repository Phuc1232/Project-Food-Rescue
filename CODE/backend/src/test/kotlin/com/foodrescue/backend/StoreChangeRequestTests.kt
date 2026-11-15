// AC-05,06,07,08 (T3.3): StoreChangeRequestTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class StoreChangeRequestTests : StoreTestFixtures() {
    private val adminBase = "/api/v1/admin/store-change-requests"
    private fun requests(storeId: String) = "/api/v1/stores/$storeId/change-requests"
    private fun request(storeId: String, token: String, body: Map<String, Any?>) = call(requests(storeId), "POST", body, token)
    private fun decide(token: String, id: String, decision: String, reason: String? = null) =
        call("$adminBase/$id/decision", "POST", mapOf("decision" to decision, "reason" to reason), token)
    private fun storeRow(id: String) = jdbc.queryForMap("select name, address, latitude::text as lat, longitude::text as lng, status from store where id=?", UUID.fromString(id))

    @Test fun `AC-05 change request records the proposal without touching the store`() {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test")
        val id = store(owner, admin); val doc = fileId(owner)
        val r = request(id, owner, mapOf("name" to " Bánh mì Cô Ba 2 ", "address" to "12 Lê Lợi, Quận 1, TP.HCM", "latitude" to 10.78,
            "longitude" to 106.7, "note" to "Chuyển địa điểm", "documents" to listOf(mapOf("type" to "FOOD_SAFETY", "fileId" to doc))))
        assertEquals(201, r.statusCode(), r.body())
        val c = json(r)
        assertEquals("PENDING", c["status"].asString()); assertEquals("Bánh mì Cô Ba 2", c["proposed"]["name"].asString())
        assertTrue(c["proposed"]["address"].isNull, "unchanged address is not part of the request")
        assertEquals(10.78, c["proposed"]["latitude"].asDouble()); assertEquals(10.7769, c["current"]["latitude"].asDouble())
        assertEquals("Bánh mì Cô Ba", c["current"]["name"].asString()); assertEquals("FOOD_SAFETY", c["documents"][0]["type"].asString())
        assertEquals(mapOf<String, Any?>("name" to "Bánh mì Cô Ba", "address" to "12 Lê Lợi, Quận 1, TP.HCM", "lat" to "10.7769000",
            "lng" to "106.7009000", "status" to "ACTIVE"), storeRow(id))
        assertEquals(c["id"].asString(), json(profile(id, owner))["pendingChangeRequest"]["id"].asString())
        assertEquals(1, count("select count(*) from attachment where store_change_request_id is not null"))
        assertEquals(1, count("select count(*) from audit_log where action='STORE_CHANGE_REQUESTED' and entity_type='STORE_CHANGE_REQUEST'"))

        val other = user("other@example.test")
        val cases = listOf<Pair<Map<String, Any?>, String>>(
            mapOf("name" to "Cửa hàng 2", "address" to " 12 Lê Lợi, Quận 1, TP.HCM ") to "changes",
            emptyMap<String, Any?>() to "changes",
            mapOf("name" to "  ") to "name",
            mapOf("latitude" to 10.8) to "longitude",
            mapOf("latitude" to 91, "longitude" to 106.7) to "latitude",
            mapOf("address" to "x".repeat(1001)) to "address",
            mapOf("name" to "Mới", "documents" to listOf(mapOf("type" to "PASSPORT", "fileId" to fileId(owner)))) to "documents[0].type",
            mapOf("name" to "Mới", "documents" to listOf(mapOf("type" to "OTHER", "fileId" to fileId(other)))) to "documents[0].fileId")
        val second = user("second@example.test"); val id2 = store(second, admin, "Cửa hàng 2")
        for ((body, field) in cases) assertField(request(id2, second, body), field)
        assertEquals(400, request(id2, second, mapOf("status" to "ACTIVE")).statusCode())
        assertEquals(0, count("select count(*) from store_change_request where store_id=?", UUID.fromString(id2)))
    }

    @Test fun `AC-06 one pending request per store, owner can cancel`() {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test")
        val id = store(owner, admin)
        val first = request(id, owner, mapOf("name" to "Tên A")); assertEquals(201, first.statusCode())
        val dup = request(id, owner, mapOf("name" to "Tên B")); assertEquals(409, dup.statusCode()); assertEquals("CONFLICT", json(dup)["code"].asString())
        val firstId = json(first)["id"].asString()
        assertEquals(404, call("${requests(id)}/${UUID.randomUUID()}/cancel", "POST", token = owner).statusCode())
        val cancelled = call("${requests(id)}/$firstId/cancel", "POST", token = owner)
        assertEquals(200, cancelled.statusCode()); assertEquals("CANCELLED", json(cancelled)["status"].asString())
        assertNotNull(json(cancelled)["resolvedAt"]); assertEquals(409, call("${requests(id)}/$firstId/cancel", "POST", token = owner).statusCode())
        assertTrue(json(profile(id, owner))["pendingChangeRequest"].isNull)

        assertEquals(listOf(201, 409), concurrently({ request(id, owner, mapOf("name" to "Tên C")) }, { request(id, owner, mapOf("address" to "Địa chỉ D")) }))
        val list = json(call(requests(id), token = owner)); assertEquals(2, list.size())
        assertEquals("PENDING", list[0]["status"].asString()); assertEquals("CANCELLED", list[1]["status"].asString())
        assertEquals(1, count("select count(*) from audit_log where action='STORE_CHANGE_CANCELLED'"))
    }

    @Test fun `AC-07 admin approves or rejects and races have one winner`() {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test"); val stranger = user("stranger@example.test")
        val id = store(owner, admin); val doc = fileId(owner)
        val req = json(request(id, owner, mapOf("address" to "99 Hai Bà Trưng", "latitude" to 10.78, "longitude" to 106.71,
            "documents" to listOf(mapOf("type" to "BUSINESS_REGISTRATION", "fileId" to doc)))))["id"].asString()
        // list + detail + documents
        val list = call("$adminBase?status=PENDING", token = admin); assertEquals(200, list.statusCode(), list.body())
        assertEquals(1, json(list)["totalElements"].asInt()); assertEquals(id, json(list)["content"][0]["store"]["id"].asString())
        val detail = json(call("$adminBase/$req", token = admin))
        assertEquals("owner@example.test", detail["requestedBy"]["email"].asString()); assertEquals("99 Hai Bà Trưng", detail["request"]["proposed"]["address"].asString())
        assertEquals(200, download("/api/v1/files/$doc/content", admin).statusCode())
        assertEquals(200, download("/api/v1/files/$doc/content", owner).statusCode())
        assertEquals(404, download("/api/v1/files/$doc/content", stranger).statusCode())
        for (t in listOf(owner, stranger)) { assertEquals(403, call(adminBase, token = t).statusCode()); assertEquals(403, decide(t, req, "APPROVE").statusCode()) }
        assertEquals(401, call("$adminBase/$req").statusCode())

        val noReason = decide(admin, req, "REJECT"); assertField(noReason, "reason")
        val approved = decide(admin, req, "APPROVE"); assertEquals(200, approved.statusCode(), approved.body())
        assertEquals("APPROVED", json(approved)["request"]["status"].asString()); assertEquals(accountId("admin@example.test").toString(), json(approved)["resolvedBy"]["id"].asString())
        assertEquals("99 Hai Bà Trưng", storeRow(id)["address"]); assertEquals("10.7800000", storeRow(id)["lat"]); assertEquals("ACTIVE", storeRow(id)["status"])
        val log = jdbc.queryForMap("select before_state, after_state from audit_log where action='STORE_CHANGE_APPROVED'")
        assertEquals("12 Lê Lợi, Quận 1, TP.HCM", mapper.readTree(log["before_state"] as String)["address"].asString())
        assertEquals("99 Hai Bà Trưng", mapper.readTree(log["after_state"] as String)["address"].asString())
        assertEquals(409, decide(admin, req, "REJECT", "muộn").statusCode())

        val rejectedId = json(request(id, owner, mapOf("name" to "Tên bị từ chối")))["id"].asString()
        val rejected = decide(admin, rejectedId, "REJECT", "Thiếu giấy ĐKKD mới"); assertEquals(200, rejected.statusCode())
        assertEquals("Bánh mì Cô Ba", storeRow(id)["name"]); assertEquals("Thiếu giấy ĐKKD mới", json(call(requests(id), token = owner))[0]["resolutionReason"].asString())

        // approve vs owner cancel, approve vs reject: exactly one wins
        val race = json(request(id, owner, mapOf("name" to "Tên đua")))["id"].asString()
        assertEquals(listOf(200, 409), concurrently({ decide(admin, race, "APPROVE") }, { call("${requests(id)}/$race/cancel", "POST", token = owner) }))
        val admin2 = makeAdmin("admin2@example.test")
        val race2 = json(request(id, owner, mapOf("address" to "Đua 2")))["id"].asString()
        assertEquals(listOf(200, 409), concurrently({ decide(admin, race2, "APPROVE") }, { decide(admin2, race2, "REJECT", "Không") }))
        assertEquals(1, count("select count(*) from audit_log where entity_id=? and action in ('STORE_CHANGE_APPROVED','STORE_CHANGE_REJECTED')", UUID.fromString(race2)))

        // an admin who owns the store cannot decide its requests
        val adminOwner = makeAdmin("admin-owner@example.test"); val own = store(adminOwner, admin, "Của admin")
        val ownReq = json(request(own, adminOwner, mapOf("name" to "Đổi tên")))["id"].asString()
        assertEquals(403, decide(adminOwner, ownReq, "APPROVE").statusCode())
        assertEquals(404, decide(admin, UUID.randomUUID().toString(), "APPROVE").statusCode())
    }
}
