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

    fun count(sql: String, vararg args: Any): Int = jdbc.queryForObject(sql, Int::class.java, *args)!!
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

    fun batchStatus(id: UUID): String = jdbc.queryForObject("select status from sale_batch where id=?", String::class.java, id)!!
    fun assertField(r: HttpResponse<String>, field: String) = assertTrue(field in fields(r), "$field in ${r.body()}")
}
