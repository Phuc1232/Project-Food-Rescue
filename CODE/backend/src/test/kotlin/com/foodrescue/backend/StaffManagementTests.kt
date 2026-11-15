// AC-01..10 (T3.4): StaffManagementTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.auth.TokenPurpose
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class StaffManagementTests : StoreTestFixtures() {
    @Autowired lateinit var authorization: StoreAuthorizationService

    private fun staff(storeId: String) = "/api/v1/stores/$storeId/staff"
    private fun invite(storeId: String, token: String, email: String, permissions: List<String> = listOf("BOX_PACK"), name: String? = "Nhân viên A") =
        call(staff(storeId), "POST", mapOf("email" to email, "displayName" to name, "permissions" to permissions), token)
    private fun meStores(token: String) = items(json(call("/api/v1/accounts/me", token = token))["stores"])
    private fun setPassword(email: String): String {
        val r = call("/api/v1/auth/reset-password", "POST", mapOf("token" to mail.token(email, TokenPurpose.SET_PASSWORD), "newPassword" to password))
        assertEquals(204, r.statusCode(), r.body()); return login(email)["accessToken"].asString()
    }
    private fun owned(): Triple<String, String, String> {
        val owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test"); return Triple(owner, admin, store(owner, admin))
    }

    @Test fun `AC-02 new email creates account with setup link`() {
        val (owner, _, id) = owned()
        val r = invite(id, owner, " New.Staff@Example.test ", listOf("BOX_PACK", "PICKUP_HANDLE"))
        assertEquals(201, r.statusCode(), r.body())
        val m = json(r)
        assertTrue(m["accountCreated"].asBoolean()); assertEquals("ACTIVE", m["status"].asString())
        assertEquals("new.staff@example.test", m["account"]["email"].asString()); assertEquals(listOf("BOX_PACK", "PICKUP_HANDLE"), items(m["permissions"]).map { it.asString() })
        assertEquals("PENDING_VERIFICATION", jdbc.queryForObject("select status from account where login_identifier='new.staff@example.test'", String::class.java))
        assertEquals(1, mail.messages.count { it.email == "new.staff@example.test" && it.purpose == TokenPurpose.SET_PASSWORD })
        assertEquals(401, call("/api/v1/auth/login", "POST", mapOf("email" to "new.staff@example.test", "password" to password)).statusCode())
        val token = setPassword("new.staff@example.test")
        val stores = meStores(token); assertEquals(1, stores.size); assertFalse(stores[0]["owner"].asBoolean())
        assertEquals(listOf("BOX_PACK", "PICKUP_HANDLE"), items(stores[0]["permissions"]).map { it.asString() })
        assertEquals(1, count("select count(*) from audit_log where action='STAFF_ACCOUNT_CREATED' and store_id is not null"))
        // the link works once
        assertEquals(400, call("/api/v1/auth/reset-password", "POST", mapOf("token" to mail.token("new.staff@example.test", TokenPurpose.SET_PASSWORD), "newPassword" to password)).statusCode())
    }

    @Test fun `AC-03 existing account must accept the invitation`() {
        val (owner, _, id) = owned(); val worker = user("worker@example.test"); val other = user("other@example.test")
        val r = invite(id, owner, "worker@example.test", name = null); assertEquals(201, r.statusCode(), r.body())
        assertEquals("INVITED", json(r)["status"].asString()); assertFalse(json(r)["accountCreated"].asBoolean())
        assertEquals(0, meStores(worker).size)
        val mine = json(call("/api/v1/staff/assignments", token = worker)); assertEquals("INVITED", mine[0]["status"].asString())
        val assignment = mine[0]["assignmentId"].asString()
        assertEquals(404, call("/api/v1/staff/assignments/$assignment/accept", "POST", token = other).statusCode())
        val accepted = call("/api/v1/staff/assignments/$assignment/accept", "POST", token = worker)
        assertEquals(200, accepted.statusCode()); assertEquals("ACTIVE", json(accepted)["status"].asString())
        assertEquals(1, meStores(worker).size)
        assertEquals(409, call("/api/v1/staff/assignments/$assignment/decline", "POST", token = worker).statusCode())
        // declining
        val second = user("second@example.test")
        val inv = json(invite(id, owner, "second@example.test", name = null))["assignmentId"].asString()
        val declined = call("/api/v1/staff/assignments/$inv/decline", "POST", token = second)
        assertEquals("DECLINED", json(declined)["status"].asString()); assertEquals(0, meStores(second).size)
        assertEquals(201, invite(id, owner, "second@example.test", name = null).statusCode(), "declined invitations can be re-sent")
    }

    @Test fun `AC-04 invalid invitations are rejected without side effects`() {
        val (owner, _, id) = owned(); user("worker@example.test")
        register("pending@example.test", verify = false)
        for (code in listOf("OWNER", "ADMIN", "PRICE_EDIT", "COMMISSION_EDIT", "STORE_PROFILE_MANAGE", "STAFF_MANAGE"))
            assertField(invite(id, owner, "x@example.test", listOf(code)), "permissions[0]")
        assertField(invite(id, owner, "x@example.test", listOf("BOX_PACK", "BOX_PACK")), "permissions[1]")
        assertField(invite(id, owner, "not-an-email"), "email")
        assertField(invite(id, owner, "fresh@example.test", name = null), "displayName")
        assertField(invite(id, owner, "OWNER@example.test"), "email")
        assertEquals(409, invite(id, owner, "pending@example.test", name = null).statusCode())
        jdbc.update("update account set status='LOCKED' where login_identifier='worker@example.test'")
        assertEquals(409, invite(id, owner, "worker@example.test", name = null).statusCode())
        assertEquals(0, count("select count(*) from staff_assignment")); assertEquals(0, count("select count(*) from account where login_identifier in ('x@example.test','fresh@example.test')"))
        jdbc.update("update account set status='ACTIVE' where login_identifier='worker@example.test'")
        assertEquals(201, invite(id, owner, "worker@example.test", name = null).statusCode())
        assertEquals(409, invite(id, owner, "worker@example.test", name = null).statusCode())
    }

    @Test fun `AC-05 concurrent invitations never duplicate accounts`() {
        val (owner, admin, id) = owned()
        assertEquals(listOf(201, 409), concurrently({ invite(id, owner, "race@example.test") }, { invite(id, owner, "race@example.test") }))
        val owner2 = user("owner2@example.test"); val id2 = store(owner2, admin, "Cửa hàng 2")
        val results = concurrently({ invite(id, owner, "race2@example.test") }, { invite(id2, owner2, "race2@example.test") })
        assertTrue(results.contains(201))
        assertEquals(1, count("select count(*) from account where login_identifier='race@example.test'"))
        assertEquals(1, count("select count(*) from account where login_identifier='race2@example.test'"))
    }

    @Test fun `AC-06 permission changes apply on the next request`() {
        val (owner, _, id) = owned()
        val a = json(invite(id, owner, "s@example.test", listOf("BOX_PACK")))["assignmentId"].asString()
        val token = setPassword("s@example.test"); val staffId = accountId("s@example.test"); val store = UUID.fromString(id)
        authorization.requirePermission(staffId, store, "BOX_PACK")
        assertThrows(ApiException::class.java) { authorization.requirePermission(staffId, store, "PICKUP_HANDLE") }
        val r = call("${staff(id)}/$a/permissions", "PUT", mapOf("permissions" to listOf("PICKUP_HANDLE", "ORDER_VIEW")), owner)
        assertEquals(200, r.statusCode(), r.body())
        assertEquals(listOf("ORDER_VIEW", "PICKUP_HANDLE"), items(meStores(token)[0]["permissions"]).map { it.asString() })
        authorization.requirePermission(staffId, store, "PICKUP_HANDLE")
        assertEquals(403, assertThrows(ApiException::class.java) { authorization.requirePermission(staffId, store, "BOX_PACK") }.status)
        val log = jdbc.queryForMap("select before_state, after_state from audit_log where action='STAFF_PERMISSIONS_CHANGED'")
        assertEquals("BOX_PACK", mapper.readTree(log["before_state"] as String)["permissions"][0].asString())
        assertField(call("${staff(id)}/$a/permissions", "PUT", mapOf("permissions" to listOf("PRICE_EDIT")), owner), "permissions[0]")
        assertEquals(404, call("${staff(id)}/${UUID.randomUUID()}/permissions", "PUT", mapOf("permissions" to emptyList<String>()), owner).statusCode())
        assertEquals(200, call("${staff(id)}/$a/revoke", "POST", mapOf("reason" to "Nghỉ việc"), owner).statusCode())
        assertEquals(409, call("${staff(id)}/$a/permissions", "PUT", mapOf("permissions" to listOf("BOX_PACK")), owner).statusCode())
    }

    @Test fun `AC-07 revocation is immediate and does not lock the account`() {
        val (owner, _, id) = owned()
        val a = json(invite(id, owner, "s@example.test", listOf("BOX_PACK")))["assignmentId"].asString()
        val token = setPassword("s@example.test"); assertEquals(1, meStores(token).size)
        assertField(call("${staff(id)}/$a/revoke", "POST", mapOf("reason" to " "), owner), "reason")
        val r = call("${staff(id)}/$a/revoke", "POST", mapOf("reason" to "Nghỉ việc"), owner)
        assertEquals(200, r.statusCode()); assertEquals("REVOKED", json(r)["status"].asString()); assertEquals(0, json(r)["permissions"].size())
        // same (old) token, next request: no store context; account still usable
        assertEquals(0, meStores(token).size)
        assertEquals(403, assertThrows(ApiException::class.java) { authorization.requirePermission(accountId("s@example.test"), UUID.fromString(id), "BOX_PACK") }.status)
        assertEquals("ACTIVE", jdbc.queryForObject("select status from account where login_identifier='s@example.test'", String::class.java))
        assertEquals(200, call("/api/v1/accounts/me", token = token).statusCode())
        assertEquals(200, call("${staff(id)}/$a/revoke", "POST", mapOf("reason" to "Lặp"), owner).statusCode())
        assertEquals(1, count("select count(*) from audit_log where action='STAFF_REVOKED'"))
        assertEquals("Nghỉ việc", mapper.readTree(jdbc.queryForObject("select after_state from audit_log where action='STAFF_REVOKED'", String::class.java))["reason"].asString())
        val again = invite(id, owner, "s@example.test", name = null); assertEquals(201, again.statusCode()); assertEquals("INVITED", json(again)["status"].asString())
        assertEquals(a, json(again)["assignmentId"].asString())
    }

    @Test fun `AC-08 only the owner manages staff`() {
        val (owner, admin, id) = owned(); val other = user("other@example.test"); store(other, admin, "Khác")
        val a = json(invite(id, owner, "s@example.test"))["assignmentId"].asString(); val staffToken = setPassword("s@example.test")
        assertEquals(401, call(staff(id)).statusCode())
        for (t in listOf(other, staffToken, admin)) {
            assertEquals(403, call(staff(id), token = t).statusCode())
            assertEquals(403, invite(id, t, "y@example.test").statusCode())
            assertEquals(403, call("${staff(id)}/$a/revoke", "POST", mapOf("reason" to "x"), t).statusCode())
        }
        val pendingOwner = user("pending@example.test"); val pending = store(pendingOwner, null, "Chờ")
        assertEquals(403, invite(pending, pendingOwner, "z@example.test").statusCode())
        assertEquals(0, count("select count(*) from account where login_identifier in ('y@example.test','z@example.test')"))
    }

    @Test fun `AC-01 AC-09 owner lists staff and staff sees own stores`() {
        val (owner, _, id) = owned()
        assertEquals(0, json(call(staff(id), token = owner)).size())
        invite(id, owner, "a@example.test"); user("b@example.test"); invite(id, owner, "b@example.test", name = null)
        val list = json(call(staff(id), token = owner)); assertEquals(2, list.size())
        assertEquals(setOf("ACTIVE", "INVITED"), items(list).map { it["status"].asString() }.toSet())
        assertEquals(1, json(call("${staff(id)}?status=INVITED", token = owner)).size())
        val token = setPassword("a@example.test")
        val mine = json(call("/api/v1/staff/assignments", token = token))
        assertEquals(1, mine.size()); assertEquals("Bánh mì Cô Ba", mine[0]["store"]["name"].asString()); assertEquals("BOX_PACK", mine[0]["permissions"][0].asString())
        val history = json(call("/api/v1/stores/$id/history", token = owner))["content"]
        assertTrue(items(history).any { it["action"].asString() == "STAFF_INVITED" })
    }

    @Test fun `AC-10 seeded catalogue`() {
        assertEquals(listOf("BOX_MANAGE", "BOX_PACK", "MENU_VIEW", "NO_SHOW_CONFIRM", "ORDER_VIEW", "PICKUP_HANDLE"),
            jdbc.queryForList("select code from permission order by code", String::class.java))
    }
}
