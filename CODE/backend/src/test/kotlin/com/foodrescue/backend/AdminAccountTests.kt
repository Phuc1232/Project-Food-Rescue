// AC-01..08 (T3.5): AdminAccountTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class AdminAccountTests : StoreTestFixtures() {
    private val base = "/api/v1/admin/accounts"
    private fun status(admin: String, id: UUID, action: String, reason: String? = "Gian lận", plan: String? = null) =
        call("$base/$id/status", "POST", mapOf("action" to action, "reason" to reason, "outstandingPlan" to plan), admin)

    @Test fun `AC-01 search by email, role and status`() {
        val admin = makeAdmin("admin@example.test"); user("alice@example.test"); user("bob@example.test"); register("carol@example.test", verify = false)
        val all = json(call(base, token = admin)); assertEquals(4, all["totalElements"].asInt())
        assertFalse(call(base, token = admin).body().contains("password", ignoreCase = true))
        assertEquals(listOf("alice@example.test"), items(json(call("$base?q=ALI", token = admin))["content"]).map { it["email"].asString() })
        assertEquals(1, json(call("$base?role=ADMIN", token = admin))["totalElements"].asInt())
        assertEquals("carol@example.test", json(call("$base?status=PENDING_VERIFICATION", token = admin))["content"][0]["email"].asString())
        assertEquals(1, json(call("$base?q=example&role=CUSTOMER&status=ACTIVE&size=1&page=1", token = admin))["content"].size())
        for (bad in listOf("role=OWNER", "status=NOPE", "size=0", "page=-1")) assertEquals(400, call("$base?$bad", token = admin).statusCode(), bad)
    }

    @Test fun `AC-02 detail shows roles, stores, staff links and history without credentials`() {
        val admin = makeAdmin("admin@example.test"); val owner = user("owner@example.test"); val id = store(owner, admin)
        call("/api/v1/stores/$id/staff", "POST", mapOf("email" to "admin@example.test", "permissions" to listOf("BOX_PACK")), owner)
        val d = call("$base/${accountId("owner@example.test")}", token = admin); assertEquals(200, d.statusCode(), d.body())
        val j = json(d)
        assertEquals(listOf("CUSTOMER"), items(j["account"]["roles"]).map { it.asString() }); assertTrue(j["account"]["emailVerified"].asBoolean())
        assertEquals("OWNER", j["stores"][0]["relation"].asString()); assertEquals(0, j["outstandingCustomerOrders"].asInt())
        assertTrue(items(j["history"]).any { it["action"].asString() == "REGISTERED" })
        for (secret in listOf("password", "hash", "token")) assertFalse(d.body().contains(secret, ignoreCase = true), secret)
        val staffLink = json(call("$base/${accountId("admin@example.test")}", token = admin))["stores"][0]
        assertEquals("STAFF", staffLink["relation"].asString()); assertEquals("INVITED", staffLink["assignmentStatus"].asString())
        assertEquals("BOX_PACK", staffLink["permissions"][0].asString())
        assertEquals(404, call("$base/${UUID.randomUUID()}", token = admin).statusCode())
    }

    @Test fun `AC-03 AC-04 lock is immediate, unlock restores only the account`() {
        val admin = makeAdmin("admin@example.test"); val owner = user("owner@example.test"); val id = store(owner, admin)
        val ownerId = accountId("owner@example.test")
        assertEquals(200, setStatus(id, admin, "SUSPEND").statusCode())
        val locked = status(admin, ownerId, "LOCK", "Gian lận nhiều lần"); assertEquals(200, locked.statusCode(), locked.body())
        assertEquals("LOCKED", json(locked)["account"]["status"].asString()); assertEquals("Gian lận nhiều lần", json(locked)["statusReason"].asString())
        assertEquals(401, call("/api/v1/accounts/me", token = owner).statusCode())
        assertEquals(401, call("/api/v1/auth/login", "POST", mapOf("email" to "owner@example.test", "password" to password)).statusCode())
        val log = jdbc.queryForMap("select actor_id, after_state, occurred_at from audit_log where action='ACCOUNT_LOCKED'")
        assertEquals(accountId("admin@example.test"), log["actor_id"]); assertEquals("Gian lận nhiều lần", mapper.readTree(log["after_state"] as String)["reason"].asString())
        assertEquals(0, count("select count(*) from login_session where account_id=? and revoked_at is null", ownerId))

        val unlocked = status(admin, ownerId, "UNLOCK", "Đã xác minh"); assertEquals(200, unlocked.statusCode())
        assertEquals("ACTIVE", json(unlocked)["account"]["status"].asString()); assertTrue(json(unlocked)["statusReason"].isNull)
        val again = login("owner@example.test")["accessToken"].asString()
        assertEquals(200, call("/api/v1/accounts/me", token = again).statusCode())
        assertEquals("SUSPENDED", jdbc.queryForObject("select status from store where id=?", String::class.java, UUID.fromString(id)))
        assertEquals(listOf("CUSTOMER"), jdbc.queryForList("select role from account_role where account_id=?", String::class.java, ownerId))
    }

    @Test fun `AC-05 invalid transitions`() {
        val admin = makeAdmin("admin@example.test"); user("u@example.test"); register("p@example.test", verify = false)
        val u = accountId("u@example.test")
        assertField(status(admin, u, "LOCK", " "), "reason")
        assertEquals(400, status(admin, u, "DELETE").statusCode())
        assertEquals(409, status(admin, u, "UNLOCK").statusCode())
        assertEquals(409, status(admin, accountId("p@example.test"), "LOCK").statusCode())
        assertEquals(403, status(admin, accountId("admin@example.test"), "LOCK").statusCode())
        assertEquals(200, status(admin, u, "LOCK").statusCode()); assertEquals(409, status(admin, u, "LOCK").statusCode())
        assertEquals(1, count("select count(*) from audit_log where action='ACCOUNT_LOCKED'"))
    }

    @Test fun `AC-06 unfinished orders need a handling plan`() {
        val admin = makeAdmin("admin@example.test"); val owner = user("owner@example.test"); user("customer@example.test")
        val id = store(owner, admin); val order = order(id, saleBatch(id, "OPEN"), "customer@example.test")
        for ((email, field) in listOf("owner@example.test" to "storeOrders", "customer@example.test" to "customerOrders")) {
            val r = status(admin, accountId(email), "LOCK")
            assertEquals(409, r.statusCode()); assertEquals("OUTSTANDING_WORK", json(r)["code"].asString())
            assertEquals("1", items(json(r)["fieldErrors"]).first { it["field"].asString() == field }["message"].asString())
        }
        assertEquals(0, count("select count(*) from account where status='LOCKED'"))
        val ok = status(admin, accountId("owner@example.test"), "LOCK", "Vi phạm", "Admin liên hệ khách, hủy/hoàn theo T7.5")
        assertEquals(200, ok.statusCode(), ok.body()); assertEquals(1, json(ok)["outstandingStoreOrders"].asInt())
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from customer_order where id=?", String::class.java, order))
        assertEquals("Admin liên hệ khách, hủy/hoàn theo T7.5", mapper.readTree(jdbc.queryForObject(
            "select after_state from audit_log where action='ACCOUNT_LOCKED'", String::class.java))["outstandingPlan"].asString())
    }

    @Test fun `AC-07 only admins, concurrent locks have one winner`() {
        val admin = makeAdmin("admin@example.test"); val admin2 = makeAdmin("admin2@example.test"); val u = user("u@example.test")
        val target = accountId("u@example.test")
        for (p in listOf(base, "$base/$target")) { assertEquals(403, call(p, token = u).statusCode()); assertEquals(401, call(p).statusCode()) }
        assertEquals(403, status(u, accountId("admin@example.test"), "LOCK").statusCode())
        assertEquals(listOf(200, 409), concurrently({ status(admin, target, "LOCK") }, { status(admin2, target, "LOCK") }))
        assertEquals(1, count("select count(*) from audit_log where action='ACCOUNT_LOCKED'"))
        // an admin may lock another admin, which then loses admin access immediately
        assertEquals(200, status(admin, accountId("admin2@example.test"), "LOCK").statusCode())
        assertEquals(401, call(base, token = admin2).statusCode())
    }
}
