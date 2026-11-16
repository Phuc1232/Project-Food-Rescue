// AC-01..07 (T4.5): FinancialPolicyTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.finance.service.FinancialPolicyResolver
import com.foodrescue.backend.finance.service.MoneyRounding
import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class FinancialPolicyTests : StoreTestFixtures() {
    @Autowired lateinit var resolver: FinancialPolicyResolver
    private val admin = "/api/v1/admin/financial-policies"
    private fun body(based: Int = 1, extra: Map<String, Any?> = emptyMap()) = mapOf("basedOnVersion" to based, "commissionRate" to 0.12,
        "noShowRetentionRate" to 0.4, "disputeWindowHours" to 48, "cancellationTerms" to "Hủy trước giờ nhận được hoàn toàn bộ",
        "refundTerms" to "Hoàn theo giao dịch gốc", "reason" to "Điều chỉnh theo thị trường") + extra
    private fun create(token: String, b: Map<String, Any?> = body()) = call(admin, "POST", b, token)

    @Test fun `AC-01 seeded default policy is visible to everyone`() {
        val a = makeAdmin("admin@example.test"); val u = user("u@example.test")
        val c = json(call("/api/v1/financial-policies/current", token = u)); assertEquals(1, c["version"].asInt())
        assertEquals("ACTIVE", c["state"].asString()); assertEquals(0.1, c["commissionRate"].asDouble()); assertEquals(0.5, c["noShowRetentionRate"].asDouble())
        assertEquals(48, c["disputeWindowHours"].asInt()); assertTrue(c["createdById"].isNull); assertTrue(c["rounding"].asString().contains("HALF_UP"))
        assertEquals(1, json(call(admin, token = a)).size())
        assertEquals(BigDecimal("15000"), MoneyRounding.applyRate(BigDecimal("30000"), BigDecimal("0.5")))
        assertEquals(BigDecimal("3750"), MoneyRounding.applyRate(BigDecimal("29999"), BigDecimal("0.125")))   // 3749.875 -> 3750
    }

    @Test fun `AC-02 AC-07 new version applies to new orders only`() {
        val a = makeAdmin("admin@example.test"); val owner = user("owner@example.test"); user("customer@example.test")
        val store = store(owner, a); val order = order(store, saleBatch(store, "OPEN"), "customer@example.test")
        val r = create(a); assertEquals(201, r.statusCode(), r.body())
        assertEquals(2, json(r)["version"].asInt()); assertEquals("ACTIVE", json(r)["state"].asString())
        val list = items(json(call(admin, token = a))); assertEquals(listOf("ACTIVE", "SUPERSEDED"), list.map { it["state"].asString() })
        assertEquals(0.1, list[1]["commissionRate"].asDouble())
        assertEquals(0.12, json(call("/api/v1/financial-policies/current", token = owner))["commissionRate"].asDouble())
        assertEquals("0.100000", jdbc.queryForObject("select commission_rate::text from customer_order where id=?", String::class.java, order))
        assertEquals("Điều chỉnh theo thị trường", mapper.readTree(jdbc.queryForObject("select after_state from audit_log where action='FINANCIAL_POLICY_CREATED'", String::class.java))["reason"].asString())
    }

    @Test fun `AC-03 future versions are scheduled`() {
        val a = makeAdmin("admin@example.test")
        val from = Instant.now().plusSeconds(3600)
        val r = create(a, body(extra = mapOf("effectiveFrom" to from.toString()))); assertEquals(201, r.statusCode(), r.body())
        assertEquals("SCHEDULED", json(r)["state"].asString())
        assertEquals(1, json(call("/api/v1/financial-policies/current", token = a))["version"].asInt())
        assertEquals(2, resolver.at(from.plusSeconds(1)).policyVersion); assertEquals(1, resolver.at(from.minusSeconds(1)).policyVersion)
    }

    @Test fun `AC-04 invalid values`() {
        val a = makeAdmin("admin@example.test")
        val cases = listOf(mapOf("commissionRate" to -0.1) to "commissionRate", mapOf("commissionRate" to 1.5) to "commissionRate",
            mapOf("noShowRetentionRate" to 0.12345) to "noShowRetentionRate", mapOf("disputeWindowHours" to 24) to "disputeWindowHours",
            mapOf("cancellationTerms" to " ") to "cancellationTerms", mapOf("reason" to "") to "reason")
        for ((extra, field) in cases) assertField(create(a, body(extra = extra)), field)
        assertEquals(1, count("select count(*) from financial_policy"))
    }

    @Test fun `AC-05 overlapping, retroactive and concurrent versions`() {
        val a = makeAdmin("admin@example.test")
        assertEquals(409, create(a, body(extra = mapOf("effectiveFrom" to Instant.now().minusSeconds(3600).toString()))).statusCode())
        val future = Instant.now().plusSeconds(7200)
        assertEquals(201, create(a, body(extra = mapOf("effectiveFrom" to future.toString()))).statusCode())
        assertEquals(409, create(a, body(based = 2, extra = mapOf("effectiveFrom" to future.minusSeconds(60).toString()))).statusCode())
        assertEquals(409, create(a, body(based = 2, extra = mapOf("effectiveFrom" to future.toString()))).statusCode())
        assertEquals(409, create(a, body(based = 1, extra = mapOf("effectiveFrom" to future.plusSeconds(60).toString()))).statusCode())
        val later = future.plusSeconds(3600).toString()
        assertEquals(listOf(201, 409), concurrently({ create(a, body(based = 2, extra = mapOf("effectiveFrom" to later))) },
            { create(a, body(based = 2, extra = mapOf("effectiveFrom" to later))) }))
        assertEquals(3, count("select count(*) from financial_policy"))
    }

    @Test fun `AC-06 only admins, history is immutable`() {
        val owner = user("owner@example.test")
        assertEquals(403, create(owner).statusCode()); assertEquals(403, call(admin, token = owner).statusCode())
        assertEquals(401, call("/api/v1/financial-policies/current").statusCode())
        for (sql in listOf("update financial_policy set commission_rate=0.9", "delete from financial_policy")) assertThrows(Exception::class.java) { jdbc.update(sql) }
        assertNotNull(UUID.randomUUID())
    }
}
