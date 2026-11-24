// AC-01..15 (T3.3): StoreTestFixtures responsibility.
package com.foodrescue.backend.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import tools.jackson.databind.JsonNode
import java.net.http.HttpResponse
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * T3.3 helpers: stores are created through the real T3.2 API (submit + approve); sale batches and orders do not have
 * APIs yet (T4.3, T5.2), so they are inserted with SQL.
 */
abstract class StoreTestFixtures : AuthTestFixtures() {
    val pdf = "%PDF-1.7\n".toByteArray() + ByteArray(256)

    fun user(email: String): String { register(email); return login(email)["accessToken"].asString() }
    fun makeAdmin(email: String): String {
        val token = user(email)
        jdbc.update("insert into account_role(id,created_at,updated_at,version,account_id,role) select ?,now(),now(),0,id,'ADMIN' from account where login_identifier=?", UUID.randomUUID(), email)
        return token
    }
    fun accountId(email: String): UUID = jdbc.queryForObject("select id from account where login_identifier=?", UUID::class.java, email)!!
    fun fileId(token: String, name: String = "doc.pdf"): String {
        val r = upload(pdf, token, name); assertEquals(201, r.statusCode(), r.body()); return json(r)["id"].asString()
    }

    val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(256)
    fun imageId(token: String): String { val r = upload(png, token, "cover.png"); assertEquals(201, r.statusCode(), r.body()); return json(r)["id"].asString() }

    /** Owner + store-scoped staff account that accepted an invitation with [permissions]. */
    fun staffWith(storeId: String, owner: String, email: String, permissions: List<String>): String {
        val token = user(email)
        val a = json(call("/api/v1/stores/$storeId/staff", "POST", mapOf("email" to email, "permissions" to permissions), owner))["assignmentId"].asString()
        assertEquals(200, call("/api/v1/staff/assignments/$a/accept", "POST", token = token).statusCode()); return token
    }

    /** Submits a partner application; approve=true lets [adminToken] approve it (store ACTIVE). Returns the store id. */
    fun store(ownerToken: String, adminToken: String?, name: String = "Bánh mì Cô Ba"): String {
        val r = call("/api/v1/partner-applications", "POST", mapOf(
            "storeName" to name, "address" to "12 Lê Lợi, Quận 1, TP.HCM", "latitude" to 10.7769, "longitude" to 106.7009,
            "timezone" to "Asia/Ho_Chi_Minh", "contactPhone" to "0901234567", "businessDescription" to "Bánh mì, bánh ngọt",
            "openingHours" to listOf(mapOf("dayOfWeek" to 1, "opensAt" to "06:00", "closesAt" to "21:00")),
            "documents" to listOf(mapOf("type" to "BUSINESS_REGISTRATION", "fileId" to fileId(ownerToken)),
                mapOf("type" to "FOOD_SAFETY", "fileId" to fileId(ownerToken)))), ownerToken)
        assertEquals(201, r.statusCode(), r.body())
        if (adminToken != null) {
            val d = call("/api/v1/admin/partner-applications/${json(r)["current"]["id"].asString()}/decision", "POST", mapOf("decision" to "APPROVE"), adminToken)
            assertEquals(200, d.statusCode(), d.body())
        }
        return json(r)["store"]["id"].asString()
    }

    fun profile(storeId: String, token: String) = call("/api/v1/stores/$storeId/profile", token = token)
    fun patch(storeId: String, token: String, body: Any) = call("/api/v1/stores/$storeId/profile", "PATCH", body, token)
    fun version(storeId: String, token: String): Long = json(profile(storeId, token))["version"].asLong()
    fun setStatus(storeId: String, token: String, action: String, reason: String? = "Vi phạm ATTP") =
        call("/api/v1/admin/stores/$storeId/status", "POST", mapOf("action" to action, "reason" to reason), token)

    fun count(sql: String, vararg args: Any): Int {
        val res = jdbc.queryForObject(sql, Int::class.java, *args)!!
        TestEvidenceCollector.recordDb(sql, res)
        return res
    }
    fun fields(r: HttpResponse<String>): List<String> {
        assertEquals(400, r.statusCode(), r.body()); assertEquals("VALIDATION_FAILED", json(r)["code"].asString())
        val n = json(r)["fieldErrors"]; return (0 until n.size()).map { n[it]["field"].asString() }
    }
    fun items(node: JsonNode): List<JsonNode> = (0 until node.size()).map { node[it] }

    /** Runs the calls at the same moment and returns their status codes, sorted. */
    fun concurrently(vararg calls: () -> HttpResponse<String>): List<Int> {
        val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(calls.size)
        try {
            val futures = calls.map { c -> pool.submit<Int> { start.await(); c().statusCode() } }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }.sorted()
        } finally { pool.shutdownNow() }
    }

    // ---- SQL data for tables without an API yet (T4.3 sale batches, T5.2 orders) ----
    private val now: Instant = Instant.parse("2026-11-15T03:00:00Z")

    fun saleBatch(storeId: String, status: String): UUID {
        val store = UUID.fromString(storeId)
        val category = jdbc.queryForList("select id from food_category where code='BAKERY'", UUID::class.java).firstOrNull() ?: UUID.randomUUID().also {
            jdbc.update("insert into food_category(id,created_at,version,updated_at,code,name,active) values (?,now(),0,now(),'BAKERY','Bánh',true)", it) }
        val template = UUID.randomUUID()
        jdbc.update("""insert into box_template(id,created_at,version,updated_at,store_id,category_id,name,description,safety_warnings,
            default_selling_price,default_minimum_value,active) values (?,now(),0,now(),?,?,'Hộp bánh','Bánh trong ngày','Có gluten',30000,60000,true)""",
            template, store, category)
        val id = UUID.randomUUID()
        jdbc.update("""insert into sale_batch(id,created_at,version,updated_at,store_id,template_id,sale_date,selling_price,minimum_value,
            order_deadline,pickup_starts_at,pickup_ends_at,safety_warnings,status) values (?,now(),0,now(),?,?,date '2026-11-16',30000,60000,?,?,?,'Có gluten',?)""",
            id, store, template, Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now.plusSeconds(7200)), Timestamp.from(now.plusSeconds(10800)), status)
        return id
    }

    /** A CONFIRMED order with pickup snapshot on [batchId]. */
    fun order(storeId: String, batchId: UUID, customerEmail: String): UUID {
        val customer = accountId(customerEmail)
        val policy = jdbc.queryForList("select id from financial_policy where policy_version=1", UUID::class.java).firstOrNull() ?: UUID.randomUUID().also {
            jdbc.update("""insert into financial_policy(id,created_at,policy_version,effective_from,commission_rate,no_show_retention_rate,
                dispute_window_hours,cancellation_terms,refund_terms,created_by_id) values (?,now(),1,?,0.1,0.5,48,'terms','terms',?)""",
                it, Timestamp.from(now.minusSeconds(86400)), customer) }
        val id = UUID.randomUUID()
        jdbc.update("""insert into customer_order(id,created_at,version,updated_at,customer_id,store_id,sale_batch_id,order_code,quantity,status,
            hold_expires_at,unit_price,total_amount,minimum_value,currency,safety_warnings_snapshot,pickup_address_snapshot,pickup_starts_at,
            pickup_ends_at,policy_id,commission_rate,no_show_retention_rate,dispute_window_hours,cancellation_terms_snapshot,refund_terms_snapshot,
            accepted_at) values (?,now(),0,now(),?,?,?,?,1,'CONFIRMED',?,30000,30000,60000,'VND','Có gluten','12 Lê Lợi, Quận 1, TP.HCM',?,?,?,0.1,0.5,48,'terms','terms',?)""",
            id, customer, UUID.fromString(storeId), batchId, "FR-" + id.toString().take(8), Timestamp.from(now.plusSeconds(600)),
            Timestamp.from(now.plusSeconds(7200)), Timestamp.from(now.plusSeconds(10800)), policy, Timestamp.from(now))
        return id
    }

    /** T4.3+: opens every day 06:00-21:00 (store timezone Asia/Ho_Chi_Minh). */
    fun openAllWeek(storeId: String, owner: String) {
        val r = patch(storeId, owner, mapOf("version" to version(storeId, owner),
            "openingHours" to (1..7).map { mapOf("dayOfWeek" to it, "opensAt" to "06:00", "closesAt" to "21:00") }))
        assertEquals(200, r.statusCode(), r.body())
    }

    /** Tomorrow at [hour]:[minute] in Asia/Ho_Chi_Minh, as an ISO instant string. */
    fun tomorrowAt(hour: Int, minute: Int = 0): String {
        val zone = java.time.ZoneId.of("Asia/Ho_Chi_Minh")
        return java.time.LocalDate.now(zone).plusDays(1).atTime(hour, minute).atZone(zone).toInstant().toString()
    }

    /** A packed box on [batchId], inserted with SQL (box packing API is T4.4). */
    fun box(storeId: String, batchId: UUID, value: Int = 70000, safeUntil: Instant = Instant.now().plusSeconds(3 * 86400),
            status: String = "AVAILABLE", packerEmail: String = "owner@example.test"): UUID {
        val id = UUID.randomUUID()
        jdbc.update("""insert into physical_box(id,created_at,version,updated_at,store_id,current_batch_id,box_code,packed_by_id,packed_at,
            total_listed_value,safe_until,storage_instructions,safety_warnings,status) values (?,now(),0,now(),?,?,?,?,now(),?,?,'Ngăn mát','Có gluten',?)""",
            id, UUID.fromString(storeId), batchId, "BX-" + id.toString().take(8), accountId(packerEmail), value, Timestamp.from(safeUntil), status)
        return id
    }

    /** T5.2+: an OPEN batch (tomorrow 10:00-12:00) of a new template with [boxes] sellable boxes packed by [ownerEmail]. */
    fun openOffer(storeId: String, owner: String, ownerEmail: String = "owner@example.test", boxes: Int = 3, price: Int = 30000): UUID {
        val t = json(call("/api/v1/stores/$storeId/box-templates", "POST", mapOf("name" to "Hộp bánh", "categoryCode" to "BAKERY",
            "description" to "Bánh trong ngày", "safetyWarnings" to "Có gluten", "defaultSellingPrice" to price, "defaultMinimumValue" to 60000), owner))["id"].asString()
        val b = json(call("/api/v1/stores/$storeId/sale-batches", "POST", mapOf("templateId" to t, "orderDeadline" to tomorrowAt(9),
            "pickupStartsAt" to tomorrowAt(10), "pickupEndsAt" to tomorrowAt(12)), owner))["id"].asString()
        repeat(boxes) { box(storeId, UUID.fromString(b), packerEmail = ownerEmail) }
        assertEquals(200, call("/api/v1/stores/$storeId/sale-batches/$b/status", "POST", mapOf("action" to "PUBLISH"), owner).statusCode())
        return UUID.fromString(b)
    }

    /** T5.2+: places an order with a fresh Idempotency-Key. */
    fun placeOrder(token: String, batchId: UUID, quantity: Int = 1, key: String = UUID.randomUUID().toString(), policyVersion: Int = 1): HttpResponse<String> =
        callWithHeader("/api/v1/orders", "POST", mapOf("batchId" to batchId, "quantity" to quantity, "acceptedPolicyVersion" to policyVersion), token, "Idempotency-Key", key)

    fun callWithHeader(path: String, method: String, body: Any?, token: String?, header: String, value: String): HttpResponse<String> {
        val b = java.net.http.HttpRequest.newBuilder(java.net.URI("http://localhost:$port$contextPath$path")).header("Content-Type", "application/json").header(header, value)
        token?.takeIf { it.isNotEmpty() }?.let { b.header("Authorization", "Bearer $it") }
        val reqPayload = if (body == null) null else mapper.writeValueAsString(body)
        b.method(method, if (reqPayload == null) java.net.http.HttpRequest.BodyPublishers.noBody() else java.net.http.HttpRequest.BodyPublishers.ofString(reqPayload))
        val t0 = System.nanoTime()
        val resp = java.net.http.HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString())
        val elapsed = (System.nanoTime() - t0) / 1_000_000L
        TestEvidenceCollector.recordHttp(method, path, !token.isNullOrEmpty(), reqPayload, resp.statusCode(), resp.body(), elapsed, resp.headers().firstValue("Content-Type").orElse(null))
        return resp
    }

    fun batchStatus(id: UUID): String {
        val st = jdbc.queryForObject("select status from sale_batch where id=?", String::class.java, id)!!
        TestEvidenceCollector.recordDb("select status from sale_batch where id=$id", st)
        return st
    }
    fun assertField(r: HttpResponse<String>, field: String) = assertTrue(field in fields(r), "$field in ${r.body()}")
}
