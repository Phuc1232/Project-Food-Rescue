// AC-01,09,10,11,12,14,15 (T3.3): StoreStatusTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class StoreStatusTests : StoreTestFixtures() {
    private val admin = "/api/v1/admin/stores"

    @Test fun `AC-09 suspension blocks batches without orders and keeps orders`() {
        val owner = user("owner@example.test"); val adminToken = makeAdmin("admin@example.test"); user("customer@example.test")
        val id = store(owner, adminToken)
        val draft = saleBatch(id, "DRAFT"); val open = saleBatch(id, "OPEN"); val paused = saleBatch(id, "PAUSED")
        val withOrder = saleBatch(id, "OPEN"); val ended = saleBatch(id, "ENDED")
        val order = order(id, withOrder, "customer@example.test")

        assertField(setStatus(id, adminToken, "SUSPEND", " "), "reason")
        assertEquals(400, setStatus(id, adminToken, "CLOSE").statusCode())
        val r = setStatus(id, adminToken, "SUSPEND", "Vi phạm ATTP"); assertEquals(200, r.statusCode(), r.body())
        val p = json(r)["profile"]
        assertEquals("SUSPENDED", p["status"].asString()); assertEquals("Vi phạm ATTP", p["statusReason"].asString()); assertFalse(p["statusChangedAt"].isNull)
        assertEquals(listOf("BLOCKED", "BLOCKED", "BLOCKED", "OPEN", "ENDED"), listOf(draft, open, paused, withOrder, ended).map(::batchStatus))
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from customer_order where id=?", String::class.java, order))
        assertEquals(3, json(r)["saleBatchCounts"]["BLOCKED"].asInt()); assertEquals(1, json(r)["orderCounts"]["CONFIRMED"].asInt())
        val log = jdbc.queryForMap("select after_state, store_id from audit_log where action='STORE_SUSPENDED'")
        assertEquals(3, mapper.readTree(log["after_state"] as String)["blockedSaleBatches"].asInt()); assertEquals(UUID.fromString(id), log["store_id"])
        assertEquals(409, setStatus(id, adminToken, "SUSPEND").statusCode())

        // AC-01: owner keeps read access and sees why
        val mine = json(profile(id, owner)); assertEquals("SUSPENDED", mine["status"].asString()); assertEquals("Vi phạm ATTP", mine["statusReason"].asString())
    }

    @Test fun `AC-10 reactivation restores ACTIVE but not blocked batches, transitions are serialized`() {
        val owner = user("owner@example.test"); val adminToken = makeAdmin("admin@example.test")
        val id = store(owner, adminToken); val open = saleBatch(id, "OPEN")
        assertEquals(409, setStatus(id, adminToken, "REACTIVATE", "Không bị khóa").statusCode())
        assertEquals(listOf(200, 409), concurrently({ setStatus(id, adminToken, "SUSPEND") }, { setStatus(id, adminToken, "SUSPEND") }))
        assertEquals(1, count("select count(*) from audit_log where action='STORE_SUSPENDED'"))
        val r = setStatus(id, adminToken, "REACTIVATE", "Đã khắc phục"); assertEquals(200, r.statusCode(), r.body())
        assertEquals("ACTIVE", json(r)["profile"]["status"].asString()); assertTrue(json(r)["profile"]["statusReason"].isNull)
        assertEquals("BLOCKED", batchStatus(open))
        assertEquals("Đã khắc phục", mapper.readTree(jdbc.queryForObject("select after_state from audit_log where action='STORE_REACTIVATED'", String::class.java))["reason"].asString())
        val admin2 = makeAdmin("admin2@example.test")
        val results = concurrently({ setStatus(id, adminToken, "SUSPEND") }, { setStatus(id, admin2, "REACTIVATE", "x") })
        val final = jdbc.queryForObject("select status from store where id=?", String::class.java, UUID.fromString(id))
        // either suspend ran alone (reactivate 409) or suspend then reactivate both succeeded
        if (results == listOf(200, 409)) assertEquals("SUSPENDED", final) else { assertEquals(listOf(200, 200), results); assertEquals("ACTIVE", final) }
        assertEquals(count("select count(*) from audit_log where action in ('STORE_SUSPENDED','STORE_REACTIVATED')"), 2 + results.count { it == 200 })
    }

    @Test fun `AC-11 owners cannot change status, admins cannot judge their own store`() {
        val owner = user("owner@example.test"); val adminToken = makeAdmin("admin@example.test")
        val id = store(owner, adminToken)
        assertEquals(200, setStatus(id, adminToken, "SUSPEND").statusCode())
        assertEquals(403, setStatus(id, owner, "REACTIVATE", "Tôi đã sửa").statusCode())
        assertEquals(401, call("$admin/$id/status", "POST", mapOf("action" to "REACTIVATE", "reason" to "x")).statusCode())
        assertField(patch(id, owner, mapOf("version" to version(id, owner), "status" to "ACTIVE")), "status")
        assertEquals("SUSPENDED", json(profile(id, owner))["status"].asString())
        val adminOwner = makeAdmin("admin-owner@example.test"); val own = store(adminOwner, adminToken, "Của admin")
        assertEquals(403, setStatus(own, adminOwner, "SUSPEND").statusCode())
        assertEquals(404, setStatus(UUID.randomUUID().toString(), adminToken, "SUSPEND").statusCode())
        // pending / closed stores have no transition
        val pendingOwner = user("pending@example.test"); val pending = store(pendingOwner, null, "Chờ duyệt")
        assertEquals(409, setStatus(pending, adminToken, "SUSPEND").statusCode())
    }

    @Test fun `AC-12 only active stores are visible to customers`() {
        val owner = user("owner@example.test"); val adminToken = makeAdmin("admin@example.test"); val customer = user("customer@example.test")
        val id = store(owner, adminToken)
        val r = call("/api/v1/stores/$id", token = customer); assertEquals(200, r.statusCode(), r.body())
        assertEquals("Bánh mì Cô Ba", json(r)["name"].asString()); assertEquals(1, json(r)["openingHours"].size())
        for (hidden in listOf("owner", "version", "statusReason", "status")) assertFalse(json(r).has(hidden), hidden)
        assertEquals(401, call("/api/v1/stores/$id").statusCode())
        assertEquals(200, setStatus(id, adminToken, "SUSPEND").statusCode())
        assertEquals(404, call("/api/v1/stores/$id", token = customer).statusCode())
        val pendingOwner = user("pending@example.test"); val pending = store(pendingOwner, null, "Chờ duyệt")
        assertEquals(404, call("/api/v1/stores/$pending", token = customer).statusCode())
        assertEquals(404, call("/api/v1/stores/${UUID.randomUUID()}", token = customer).statusCode())
        jdbc.update("update store set status='CLOSED' where id=?", UUID.fromString(pending))
        assertEquals(404, call("/api/v1/stores/$pending", token = customer).statusCode())
    }

    @Test fun `AC-14 store changes never rewrite confirmed order commitments`() {
        val owner = user("owner@example.test"); val adminToken = makeAdmin("admin@example.test"); user("customer@example.test")
        val id = store(owner, adminToken); val batch = saleBatch(id, "OPEN"); val order = order(id, batch, "customer@example.test")
        val snapshot = { jdbc.queryForMap("select status, pickup_address_snapshot, pickup_starts_at, pickup_ends_at, version from customer_order where id=?", order) }
        val before = snapshot()
        assertEquals(200, patch(id, owner, mapOf("version" to version(id, owner), "openingHours" to listOf(mapOf("dayOfWeek" to 2, "opensAt" to "07:00", "closesAt" to "08:00")))).statusCode())
        val req = json(call("/api/v1/stores/$id/change-requests", "POST", mapOf("address" to "1 Nguyễn Huệ"), owner))["id"].asString()
        assertEquals(200, call("/api/v1/admin/store-change-requests/$req/decision", "POST", mapOf("decision" to "APPROVE"), adminToken).statusCode())
        assertEquals(200, setStatus(id, adminToken, "SUSPEND").statusCode())
        assertEquals(before, snapshot())
        assertEquals("1 Nguyễn Huệ", jdbc.queryForObject("select address from store where id=?", String::class.java, UUID.fromString(id)))
    }

    @Test fun `AC-15 admins list and inspect stores`() {
        val adminToken = makeAdmin("admin@example.test")
        val a = store(user("a@example.test"), adminToken, "Bánh mì Cô Ba"); val b = store(user("b@example.test"), adminToken, "Cơm tấm Sài Gòn")
        store(user("c@example.test"), null, "Bánh cuốn chờ duyệt")
        saleBatch(a, "OPEN"); saleBatch(a, "DRAFT")
        assertEquals(200, setStatus(b, adminToken, "SUSPEND").statusCode())
        val all = json(call(admin, token = adminToken)); assertEquals(3, all["totalElements"].asInt())
        val bakeries = json(call("$admin?q=b%C3%A1nh", token = adminToken)); assertEquals(2, bakeries["totalElements"].asInt())
        val suspended = json(call("$admin?status=SUSPENDED", token = adminToken))
        assertEquals(1, suspended["totalElements"].asInt()); assertEquals(b, suspended["content"][0]["id"].asString())
        assertEquals("b@example.test", suspended["content"][0]["owner"]["email"].asString())
        assertEquals(1, json(call("$admin?status=ACTIVE&q=c%C3%B4%20ba", token = adminToken))["totalElements"].asInt())
        assertEquals(400, call("$admin?status=NOPE", token = adminToken).statusCode())
        assertEquals(400, call("$admin?size=0", token = adminToken).statusCode())
        val d = json(call("$admin/$a", token = adminToken))
        assertEquals(1, d["saleBatchCounts"]["OPEN"].asInt()); assertEquals(1, d["saleBatchCounts"]["DRAFT"].asInt()); assertEquals(0, d["orderCounts"].size())
        assertEquals("a@example.test", d["owner"]["email"].asString()); assertEquals("ACTIVE", d["profile"]["status"].asString())
        assertEquals(404, call("$admin/${UUID.randomUUID()}", token = adminToken).statusCode())
        val owner = login("a@example.test")["accessToken"].asString()
        assertEquals(403, call(admin, token = owner).statusCode()); assertEquals(403, call("$admin/$a", token = owner).statusCode())
        assertEquals(401, call(admin).statusCode())
    }
}
