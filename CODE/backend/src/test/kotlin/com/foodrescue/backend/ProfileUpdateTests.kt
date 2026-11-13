// AC-01..09: ProfileUpdateTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.http.HttpResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProfileUpdateTests : AuthTestFixtures() {
    private val me = "/api/v1/accounts/me"

    private fun access(): String { register(); return login()["accessToken"].asString() }
    private fun version(token: String): Long = json(call(me, token = token))["version"].asLong()
    private fun patch(token: String?, body: Any?) = call(me, "PATCH", body, token)
    private fun storedName(): String = jdbc.queryForObject("select display_name from account", String::class.java)!!
    private fun storedVersion(): Long = jdbc.queryForObject("select version from account", Long::class.java)!!
    private fun strings(node: tools.jackson.databind.JsonNode): List<String> = (0 until node.size()).map { node[it].asString() }
    private fun fieldNames(r: HttpResponse<String>): List<String> = json(r)["fieldErrors"].let { n -> (0 until n.size()).map { n[it]["field"].asString() } }
    private fun profileAudits(): Int = jdbc.queryForObject("select count(*) from audit_log where action='PROFILE_UPDATED'", Int::class.java)!!

    @Test fun `AC-01 AC-09 get exposes version and patch trims saves and bumps version`() {
        val token = access()
        val before = json(call(me, token = token)); assertTrue(before.has("version"))
        val r = patch(token, mapOf("displayName" to "  Nguyễn Văn A  ", "version" to before["version"].asLong()))
        assertEquals(200, r.statusCode(), r.body())
        val body = json(r)
        assertEquals("Nguyễn Văn A", body["displayName"].asString())
        assertEquals(before["version"].asLong() + 1, body["version"].asLong())
        assertEquals("user@example.test", body["email"].asString()); assertEquals(listOf("CUSTOMER"), strings(body["roles"]))
        assertEquals("Nguyễn Văn A", json(call(me, token = token))["displayName"].asString())
        assertEquals("Nguyễn Văn A", storedName())
    }

    @Test fun `AC-02 non editable fields are rejected with field names and nothing changes`() {
        val token = access(); val v = version(token)
        val hashBefore = jdbc.queryForObject("select password_hash from account", String::class.java)
        val r = patch(token, mapOf("displayName" to "Hacker", "version" to v, "roles" to listOf("ADMIN"),
            "stores" to listOf(mapOf("storeId" to java.util.UUID.randomUUID().toString(), "owner" to true)),
            "email" to "admin@example.test", "password" to "Another-password-456!", "status" to "ACTIVE", "phone" to "0900000000"))
        assertEquals(400, r.statusCode())
        val body = json(r)
        assertEquals("VALIDATION_FAILED", body["code"].asString()); assertTrue(body["requestId"].asString().isNotBlank())
        assertEquals(listOf("email", "password", "phone", "roles", "status", "stores"), fieldNames(r))
        assertEquals("User", storedName()); assertEquals(v, storedVersion())
        assertEquals(listOf("CUSTOMER"), jdbc.queryForList("select role from account_role", String::class.java))
        assertEquals("user@example.test", jdbc.queryForObject("select login_identifier from account", String::class.java))
        assertEquals(hashBefore, jdbc.queryForObject("select password_hash from account", String::class.java))
        assertEquals(0, jdbc.queryForObject("select count(*) from store", Int::class.java))
        login() // old password still valid
        assertEquals(0, profileAudits())
    }

    @Test fun `AC-03 invalid data names the field and malformed json is an invalid request`() {
        val token = access(); val v = version(token)
        val cases = listOf<Pair<Any, String>>(
            mapOf("displayName" to "", "version" to v) to "displayName",
            mapOf("displayName" to "   ", "version" to v) to "displayName",
            mapOf("displayName" to "a".repeat(256), "version" to v) to "displayName",
            mapOf("displayName" to "two\nlines", "version" to v) to "displayName",
            mapOf("displayName" to 42, "version" to v) to "displayName",
            mapOf("version" to v) to "displayName",
            mapOf("displayName" to "Ok") to "version",
            mapOf("displayName" to "Ok", "version" to "1") to "version",
            mapOf("displayName" to "Ok", "version" to 1.5) to "version")
        for ((body, field) in cases) {
            val r = patch(token, body)
            assertEquals(400, r.statusCode(), body.toString())
            assertEquals("VALIDATION_FAILED", json(r)["code"].asString())
            assertEquals(listOf(field), fieldNames(r), body.toString())
        }
        for (raw in listOf("{", "[]", "null", "\"x\"")) {
            val r = patch(token, raw); assertEquals(400, r.statusCode(), raw); assertEquals("INVALID_REQUEST", json(r)["code"].asString(), raw)
        }
        assertEquals("User", storedName()); assertEquals(v, storedVersion())
    }

    @Test fun `AC-04 stale version returns 409 and keeps stored data`() {
        val token = access(); val v = version(token)
        assertEquals(200, patch(token, mapOf("displayName" to "First", "version" to v)).statusCode())
        val stale = patch(token, mapOf("displayName" to "Second", "version" to v))
        assertEquals(409, stale.statusCode()); assertEquals("CONFLICT", json(stale)["code"].asString())
        val ahead = patch(token, mapOf("displayName" to "Second", "version" to v + 5))
        assertEquals(409, ahead.statusCode())
        assertEquals("First", storedName()); assertEquals(v + 1, storedVersion())
    }

    @Test fun `AC-05 concurrent updates with the same version let exactly one win`() {
        val token = access(); val v = version(token)
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val tasks = listOf("Alpha", "Beta").map { name ->
                pool.submit<Pair<String, HttpResponse<String>>> { start.await(); name to patch(token, mapOf("displayName" to name, "version" to v)) }
            }
            start.countDown()
            val results = tasks.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), results.map { it.second.statusCode() }.sorted())
            val winner = results.first { it.second.statusCode() == 200 }.first
            assertEquals(winner, storedName()); assertEquals(v + 1, storedVersion()); assertEquals(1, profileAudits())
        } finally { pool.shutdownNow() }
    }

    @Test fun `AC-06 unauthenticated or logged out callers cannot update`() {
        val token = access(); val v = version(token)
        assertEquals(401, patch(null, mapOf("displayName" to "Anon", "version" to v)).statusCode())
        assertEquals(401, patch("not-a-jwt", mapOf("displayName" to "Anon", "version" to v)).statusCode())
        assertEquals(204, call("/api/v1/auth/logout", "POST", token = token).statusCode())
        assertEquals(401, patch(token, mapOf("displayName" to "Anon", "version" to v)).statusCode())
        assertEquals("User", storedName())
    }

    @Test fun `AC-06 a user only ever updates their own account`() {
        val token = access()
        register("other@example.test")
        val v = version(token)
        assertEquals(200, patch(token, mapOf("displayName" to "Mine", "version" to v)).statusCode())
        assertEquals("User", jdbc.queryForObject("select display_name from account where login_identifier='other@example.test'", String::class.java))
        assertEquals("Mine", jdbc.queryForObject("select display_name from account where login_identifier='user@example.test'", String::class.java))
    }

    @Test fun `AC-07 unchanged name returns 200 without version bump or audit`() {
        val token = access(); val v = version(token)
        val r = patch(token, mapOf("displayName" to "  User ", "version" to v))
        assertEquals(200, r.statusCode()); assertEquals(v, json(r)["version"].asLong())
        assertEquals(v, storedVersion()); assertEquals(0, profileAudits())
    }

    @Test fun `AC-08 successful change is audited without values or secrets`() {
        val token = access(); val v = version(token)
        val r = patch(token, mapOf("displayName" to "Audited Name", "version" to v))
        assertEquals(200, r.statusCode())
        assertFalse(r.body().contains("password", ignoreCase = true)); assertFalse(r.body().contains(token))
        val row = jdbc.queryForMap("""select l.actor_id, l.entity_id, l.correlation_id, l.before_state, l.after_state, a.id as account_id
            from audit_log l join account a on a.id=l.actor_id where l.action='PROFILE_UPDATED'""")
        assertEquals(row["account_id"], row["actor_id"]); assertEquals(row["account_id"], row["entity_id"])
        assertEquals(r.headers().firstValue("X-Request-ID").get(), row["correlation_id"])
        assertNull(row["before_state"]); assertNull(row["after_state"])
        assertEquals(1, profileAudits())
    }
}
