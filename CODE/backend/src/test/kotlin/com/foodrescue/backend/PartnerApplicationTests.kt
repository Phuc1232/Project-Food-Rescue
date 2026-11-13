// AC-01..14: PartnerApplicationTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.support.AuthTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PartnerApplicationTests : AuthTestFixtures() {
    @Autowired lateinit var storeAuthorization: StoreAuthorizationService

    private val base = "/api/v1/partner-applications"
    private val admin = "/api/v1/admin/partner-applications"
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(256)
    private val pdf = "%PDF-1.7\n".toByteArray() + ByteArray(256)

    private fun user(email: String = "user@example.test"): String { register(email); return login(email)["accessToken"].asString() }
    private fun makeAdmin(email: String): String {
        val token = user(email)
        jdbc.update("insert into account_role(id,created_at,updated_at,version,account_id,role) select ?,now(),now(),0,id,'ADMIN' from account where login_identifier=?", UUID.randomUUID(), email)
        return token
    }
    private fun fileId(token: String, content: ByteArray = pdf, name: String = "doc.pdf"): String {
        val r = upload(content, token, name); assertEquals(201, r.statusCode(), r.body()); return json(r)["id"].asString()
    }
    private fun body(token: String, name: String = "Bánh mì Cô Ba", extra: Map<String, Any?> = emptyMap()): Map<String, Any?> = mapOf(
        "storeName" to name, "address" to "12 Lê Lợi, Quận 1, TP.HCM", "latitude" to 10.7769, "longitude" to 106.7009,
        "timezone" to "Asia/Ho_Chi_Minh", "contactPhone" to "0901234567", "businessDescription" to "Bánh mì, bánh ngọt",
        "openingHours" to listOf(mapOf("dayOfWeek" to 1, "opensAt" to "06:00", "closesAt" to "21:00"),
            mapOf("dayOfWeek" to 5, "opensAt" to "18:00", "closesAt" to "01:00")),
        "documents" to listOf(mapOf("type" to "BUSINESS_REGISTRATION", "fileId" to fileId(token)),
            mapOf("type" to "FOOD_SAFETY", "fileId" to fileId(token)), mapOf("type" to "STOREFRONT_PHOTO", "fileId" to fileId(token, png, "front.png")))
    ) + extra
    private fun submit(token: String, payload: Any) = call(base, "POST", payload, token)
    private fun decide(token: String, id: String, decision: String, reason: String? = null) =
        call("$admin/$id/decision", "POST", mapOf("decision" to decision, "reason" to reason), token)
    private fun current(r: HttpResponse<String>): JsonNode = json(r)["current"]
    private fun count(sql: String) = jdbc.queryForObject(sql, Int::class.java)!!
    private fun fields(r: HttpResponse<String>): List<String> {
        assertEquals(400, r.statusCode(), r.body()); assertEquals("VALIDATION_FAILED", json(r)["code"].asString())
        val n = json(r)["fieldErrors"]; return (0 until n.size()).map { n[it]["field"].asString() }
    }
    private fun accountId(email: String) = jdbc.queryForObject("select id from account where login_identifier=?", UUID::class.java, email)!!

    @Test fun `AC-01 AC-05 AC-13 valid submission creates pending store without owner rights`() {
        val token = user()
        val r = submit(token, body(token)); assertEquals(201, r.statusCode(), r.body())
        val app = current(r)
        assertEquals(1, app["revision"].asInt()); assertEquals("SUBMITTED", app["status"].asString())
        assertEquals("PENDING_APPROVAL", json(r)["store"]["status"].asString())
        assertEquals("0901234567", app["submission"]["contactPhone"].asString())
        assertEquals(3, app["documents"].size()); assertEquals("BUSINESS_REGISTRATION", app["documents"][0]["type"].asString())
        assertEquals(2, app["submission"]["openingHours"].size()); assertTrue(app["submission"]["openingHours"][1]["closesNextDay"].asBoolean())
        assertEquals(1, count("select count(*) from store where status='PENDING_APPROVAL' and name='Bánh mì Cô Ba' and timezone='Asia/Ho_Chi_Minh'"))
        assertEquals(2, count("select count(*) from store_opening_hour"))
        // local opening times are stored as-is, independent of the JVM/JDBC time zone
        assertEquals(listOf("06:00:00-21:00:00-false", "18:00:00-01:00:00-true"), jdbc.queryForList(
            "select opens_at::text||'-'||closes_at::text||'-'||closes_next_day from store_opening_hour order by day_of_week", String::class.java))
        assertEquals(3, count("select count(*) from attachment where partner_application_id is not null"))
        assertEquals(1, count("select count(*) from audit_log where action='PARTNER_APPLICATION_SUBMITTED' and store_id is not null"))
        // AC-05: no store context while pending
        assertEquals(0, json(call("/api/v1/accounts/me", token = token))["stores"].size())
        val storeId = UUID.fromString(json(r)["store"]["id"].asString())
        assertEquals(403, assertThrows(ApiException::class.java) { storeAuthorization.requirePermission(accountId("user@example.test"), storeId, "ANY") }.status)
        // AC-13: my view, no reviewer exposed
        val mine = call("$base/me", token = token); assertEquals(200, mine.statusCode())
        assertEquals(app["id"].asString(), current(mine)["id"].asString()); assertEquals(1, json(mine)["history"].size())
        assertFalse(mine.body().contains("reviewer"))
    }

    @Test fun `AC-02 pending application is returned unchanged and concurrent first submissions create one store`() {
        val token = user()
        val first = submit(token, body(token)); assertEquals(201, first.statusCode())
        val again = submit(token, body(token, name = "Tên khác"))
        assertEquals(200, again.statusCode()); assertEquals(current(first)["id"].asString(), current(again)["id"].asString())
        assertEquals("Bánh mì Cô Ba", current(again)["submission"]["storeName"].asString())
        assertEquals(1, count("select count(*) from partner_application")); assertEquals(1, count("select count(*) from store"))

        val other = user("other@example.test"); val payload = body(other)
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map { pool.submit<Int> { start.await(); submit(other, payload).statusCode() } }
            start.countDown()
            assertEquals(listOf(200, 201), results.map { it.get(30, TimeUnit.SECONDS) }.sorted())
        } finally { pool.shutdownNow() }
        assertEquals(2, count("select count(*) from store")); assertEquals(2, count("select count(*) from partner_application"))
    }

    @Test fun `AC-03 invalid submissions name the field and create nothing`() {
        val token = user(); val other = user("other@example.test"); val foreign = fileId(other)
        val valid = body(token)
        val cases = listOf<Pair<Map<String, Any?>, String>>(
            valid + mapOf("storeName" to " ") to "storeName",
            valid + mapOf("address" to "") to "address",
            valid + mapOf("latitude" to 91) to "latitude",
            valid + mapOf("longitude" to 106.123456789) to "longitude",
            valid + mapOf("timezone" to "Mars/Olympus") to "timezone",
            valid + mapOf("timezone" to "+07:00") to "timezone",
            valid + mapOf("contactPhone" to "12345") to "contactPhone",
            valid + mapOf("openingHours" to emptyList<Any>()) to "openingHours",
            valid + mapOf("openingHours" to listOf(mapOf("dayOfWeek" to 1, "opensAt" to "08:00", "closesAt" to "12:00"),
                mapOf("dayOfWeek" to 1, "opensAt" to "11:00", "closesAt" to "13:00"))) to "openingHours[1]",
            valid + mapOf("openingHours" to listOf(mapOf("dayOfWeek" to 8, "opensAt" to "08:00", "closesAt" to "12:00"))) to "openingHours[0].dayOfWeek",
            valid + mapOf("openingHours" to listOf(mapOf("dayOfWeek" to 1, "opensAt" to "8h", "closesAt" to "12:00"))) to "openingHours[0].opensAt",
            valid + mapOf("documents" to listOf(mapOf("type" to "BUSINESS_REGISTRATION", "fileId" to fileId(token)))) to "documents",
            valid + mapOf("documents" to (valid["documents"] as List<*>) + mapOf("type" to "PASSPORT", "fileId" to fileId(token))) to "documents[3].type",
            valid + mapOf("documents" to (valid["documents"] as List<*>) + mapOf("type" to "OTHER", "fileId" to foreign)) to "documents[3].fileId",
            valid + mapOf("documents" to (valid["documents"] as List<*>) + mapOf("type" to "OTHER", "fileId" to UUID.randomUUID().toString())) to "documents[3].fileId",
            valid + mapOf("documents" to (valid["documents"] as List<*>) + (valid["documents"] as List<*>)[0]) to "documents[3].fileId")
        for ((payload, field) in cases) assertTrue(field in fields(submit(token, payload)), "$field in ${submit(token, payload).body()}")
        assertEquals(400, submit(token, valid + mapOf("role" to "OWNER")).statusCode())
        assertEquals(0, count("select count(*) from store")); assertEquals(0, count("select count(*) from partner_application"))
    }

    @Test fun `AC-04 AC-10 approval activates the store and blocks further applications`() {
        val token = user(); val adminToken = makeAdmin("admin@example.test")
        val id = current(submit(token, body(token)))["id"].asString()
        val approved = decide(adminToken, id, "APPROVE"); assertEquals(200, approved.statusCode(), approved.body())
        assertEquals("APPROVED", json(approved)["status"].asString()); assertEquals("ACTIVE", json(approved)["store"]["status"].asString())
        assertNotNull(json(approved)["reviewedAt"]); assertEquals("admin@example.test".let { accountId(it).toString() }, json(approved)["reviewer"]["id"].asString())
        val me = json(call("/api/v1/accounts/me", token = token))["stores"]
        assertEquals(1, me.size()); assertTrue(me[0]["owner"].asBoolean())
        assertEquals(0, count("select count(*) from sale_batch"))
        val blocked = submit(token, body(token)); assertEquals(409, blocked.statusCode()); assertEquals("CONFLICT", json(blocked)["code"].asString())
        assertEquals(1, count("select count(*) from partner_application"))
    }

    @Test fun `AC-06 only admins review and strangers cannot see applications or documents`() {
        val token = user(); val stranger = user("stranger@example.test")
        val r = submit(token, body(token)); val id = current(r)["id"].asString(); val doc = current(r)["documents"][0]["fileId"].asString()
        for (path in listOf(admin, "$admin/$id")) { assertEquals(403, call(path, token = token).statusCode()); assertEquals(401, call(path).statusCode()) }
        assertEquals(403, decide(token, id, "APPROVE").statusCode())
        assertEquals(404, call("$base/me", token = stranger).statusCode())
        assertEquals(404, download("/api/v1/files/$doc/content", stranger).statusCode())
        assertEquals(200, download("/api/v1/files/$doc/content", token).statusCode())
        assertEquals(401, call(base, "POST", body(token)).statusCode())
    }

    @Test fun `AC-07 admin lists filters and inspects applications with documents`() {
        val a = user(); val b = user("b@example.test"); val adminToken = makeAdmin("admin@example.test")
        val idA = current(submit(a, body(a, "Store A")))["id"].asString()
        val idB = current(submit(b, body(b, "Store B")))["id"].asString()
        decide(adminToken, idA, "REJECT", "Thiếu giấy ATTP hợp lệ")
        val pending = json(call("$admin?status=SUBMITTED", token = adminToken))
        assertEquals(1, pending["totalElements"].asInt()); assertEquals(idB, pending["content"][0]["id"].asString())
        assertEquals("b@example.test", pending["content"][0]["applicant"]["email"].asString())
        assertEquals(2, json(call(admin, token = adminToken))["totalElements"].asInt())
        assertEquals(400, call("$admin?status=NOPE", token = adminToken).statusCode())
        assertEquals(400, call("$admin?size=500", token = adminToken).statusCode())
        val detail = json(call("$admin/$idB", token = adminToken))
        assertEquals("Store B", detail["store"]["name"].asString()); assertEquals(2, detail["store"]["openingHours"].size())
        assertEquals(3, detail["documents"].size()); assertEquals(1, detail["history"].size())
        assertEquals(200, download("/api/v1/files/${detail["documents"][0]["fileId"].asString()}/content", adminToken).statusCode())
        assertEquals(404, call("$admin/${UUID.randomUUID()}", token = adminToken).statusCode())
    }

    @Test fun `AC-08 request info then resubmission creates a new revision that can be approved`() {
        val token = user(); val adminToken = makeAdmin("admin@example.test")
        val id1 = current(submit(token, body(token)))["id"].asString()
        assertTrue("reason" in fields(decide(adminToken, id1, "REQUEST_INFO", "  ")))
        val info = decide(adminToken, id1, "REQUEST_INFO", "Bổ sung ảnh giấy ATTP còn hạn")
        assertEquals("NEEDS_INFO", json(info)["status"].asString()); assertEquals("PENDING_APPROVAL", json(info)["store"]["status"].asString())
        val mine = current(call("$base/me", token = token))
        assertEquals("NEEDS_INFO", mine["status"].asString()); assertEquals("Bổ sung ảnh giấy ATTP còn hạn", mine["reviewReason"].asString())
        val resubmitted = submit(token, body(token, "Bánh mì Cô Ba 2")); assertEquals(201, resubmitted.statusCode(), resubmitted.body())
        val id2 = current(resubmitted)["id"].asString()
        assertEquals(2, current(resubmitted)["revision"].asInt()); assertEquals(2, json(resubmitted)["history"].size())
        assertEquals("Bánh mì Cô Ba 2", jdbc.queryForObject("select name from store", String::class.java))
        assertEquals("Bánh mì Cô Ba", json(call("$admin/$id1", token = adminToken))["submission"]["storeName"].asString())
        assertEquals("NEEDS_INFO", jdbc.queryForObject("select status from partner_application where revision=1", String::class.java))
        assertEquals(200, decide(adminToken, id2, "APPROVE").statusCode())
        assertEquals("ACTIVE", jdbc.queryForObject("select status from store", String::class.java))
    }

    @Test fun `AC-09 rejection requires a reason and the applicant may resubmit`() {
        val token = user(); val adminToken = makeAdmin("admin@example.test")
        val id = current(submit(token, body(token)))["id"].asString()
        assertTrue("reason" in fields(decide(adminToken, id, "REJECT")))
        assertEquals(400, call("$admin/$id/decision", "POST", mapOf("decision" to "MAYBE"), adminToken).statusCode())
        val rejected = decide(adminToken, id, "REJECT", "Địa chỉ không khớp giấy phép")
        assertEquals("REJECTED", json(rejected)["status"].asString())
        assertEquals("Địa chỉ không khớp giấy phép", current(call("$base/me", token = token))["reviewReason"].asString())
        val again = submit(token, body(token)); assertEquals(201, again.statusCode())
        assertEquals(2, current(again)["revision"].asInt()); assertEquals("SUBMITTED", current(again)["status"].asString())
    }

    @Test fun `AC-11 concurrent decisions have exactly one winner`() {
        val token = user(); val first = makeAdmin("admin1@example.test"); val second = makeAdmin("admin2@example.test")
        val id = current(submit(token, body(token)))["id"].asString()
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val tasks = listOf(first to "APPROVE", second to "REJECT").map { (t, d) ->
                pool.submit<HttpResponse<String>> { start.await(); decide(t, id, d, "Lý do") } }
            start.countDown()
            val results = tasks.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), results.map { it.statusCode() }.sorted())
            val winner = json(results.first { it.statusCode() == 200 })["status"].asString()
            assertEquals(winner, jdbc.queryForObject("select status from partner_application", String::class.java))
            assertEquals(if (winner == "APPROVED") "ACTIVE" else "PENDING_APPROVAL", jdbc.queryForObject("select status from store", String::class.java))
        } finally { pool.shutdownNow() }
        assertEquals(1, count("select count(*) from audit_log where action in ('PARTNER_APPLICATION_APPROVED','PARTNER_APPLICATION_REJECTED')"))
        val late = decide(first, id, "APPROVE"); assertEquals(409, late.statusCode()); assertEquals("CONFLICT", json(late)["code"].asString())
    }

    @Test fun `AC-12 approval rechecks the store and admins cannot review their own application`() {
        val adminToken = makeAdmin("admin@example.test"); val other = makeAdmin("admin2@example.test")
        val own = current(submit(adminToken, body(adminToken)))["id"].asString()
        assertEquals(403, decide(adminToken, own, "APPROVE").statusCode())
        assertEquals("SUBMITTED", jdbc.queryForObject("select status from partner_application", String::class.java))
        jdbc.update("update store set status='SUSPENDED'")
        val changed = decide(other, own, "APPROVE"); assertEquals(409, changed.statusCode())
        assertEquals("SUBMITTED", jdbc.queryForObject("select status from partner_application", String::class.java))
        assertEquals("SUSPENDED", jdbc.queryForObject("select status from store", String::class.java))
    }

    @Test fun `AC-13 AC-14 applicant without application gets 404 and decisions are audited`() {
        val token = user(); val adminToken = makeAdmin("admin@example.test")
        assertEquals(404, call("$base/me", token = token).statusCode())
        val id = current(submit(token, body(token)))["id"].asString()
        decide(adminToken, id, "REQUEST_INFO", "Thêm ảnh")
        val id2 = current(submit(token, body(token)))["id"].asString(); decide(adminToken, id2, "REJECT", "Không đạt")
        val id3 = current(submit(token, body(token)))["id"].asString()
        val r = decide(adminToken, id3, "APPROVE")
        val rows = jdbc.queryForList("select action, store_id, correlation_id from audit_log where entity_type='PARTNER_APPLICATION' order by occurred_at, action")
        assertEquals(setOf("PARTNER_APPLICATION_SUBMITTED", "PARTNER_APPLICATION_INFO_REQUESTED", "PARTNER_APPLICATION_REJECTED", "PARTNER_APPLICATION_APPROVED"),
            rows.map { it["action"] }.toSet())
        assertEquals(6, rows.size); assertTrue(rows.all { it["store_id"] != null && it["correlation_id"] != null })
        assertTrue(rows.any { it["correlation_id"] == r.headers().firstValue("X-Request-ID").get() })
        assertEquals(3, json(call("$base/me", token = token))["history"].size())
    }
}
