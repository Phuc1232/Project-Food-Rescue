// AC-01..07 (T4.2): BoxTemplateTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class BoxTemplateTests : StoreTestFixtures() {
    private fun base(storeId: String) = "/api/v1/stores/$storeId/box-templates"
    private fun body(extra: Map<String, Any?> = emptyMap()) = mapOf("name" to "Hộp bánh cuối ngày", "categoryCode" to "BAKERY",
        "description" to "Các loại bánh mì, bánh ngọt trong ngày", "safetyWarnings" to "Có gluten, trứng, sữa",
        "defaultSellingPrice" to 30000, "defaultMinimumValue" to 60000) + extra
    private fun setup(): Triple<String, String, String> { val o = user("owner@example.test"); val a = makeAdmin("admin@example.test"); return Triple(o, a, store(o, a)) }

    @Test fun `AC-01 creating a template creates no stock`() {
        val (owner, _, id) = setup()
        val r = call(base(id), "POST", body(mapOf("coverFileId" to imageId(owner))), owner); assertEquals(201, r.statusCode(), r.body())
        val t = json(r); assertTrue(t["active"].asBoolean()); assertEquals(30000, t["defaultSellingPrice"].asInt()); assertFalse(t["coverFileId"].isNull)
        assertEquals(0, count("select count(*) from sale_batch")); assertEquals(0, count("select count(*) from physical_box"))
        assertEquals(1, count("select count(*) from audit_log where action='BOX_TEMPLATE_CREATED'"))
    }

    @Test fun `AC-02 invalid templates`() {
        val (owner, _, id) = setup(); val other = user("other@example.test")
        val cases = listOf(body(mapOf("name" to "")) to "name", body(mapOf("description" to " ")) to "description",
            body(mapOf("safetyWarnings" to null)) to "safetyWarnings", body(mapOf("categoryCode" to "PIZZA")) to "categoryCode",
            body(mapOf("defaultSellingPrice" to 0)) to "defaultSellingPrice", body(mapOf("defaultMinimumValue" to 60000.5)) to "defaultMinimumValue",
            body(mapOf("defaultMinimumValue" to 20000)) to "defaultMinimumValue", body(mapOf("coverFileId" to fileId(owner))) to "coverFileId",
            body(mapOf("coverFileId" to imageId(other))) to "coverFileId")
        for ((b, f) in cases) assertField(call(base(id), "POST", b, owner), f)
        assertEquals(0, count("select count(*) from box_template"))
    }

    @Test fun `AC-03 AC-04 edits keep versions and never touch existing batches`() {
        val (owner, _, id) = setup()
        val t = json(call(base(id), "POST", body(), owner)); val tid = t["id"].asString(); val v = t["version"].asLong()
        // a batch created earlier from this template keeps its snapshot
        val batch = UUID.randomUUID()
        jdbc.update("""insert into sale_batch(id,created_at,version,updated_at,store_id,template_id,sale_date,selling_price,minimum_value,order_deadline,
            pickup_starts_at,pickup_ends_at,safety_warnings,status) values (?,now(),0,now(),?,?,date '2026-11-16',30000,60000,now()+interval '1 hour',
            now()+interval '2 hour',now()+interval '3 hour','Có gluten, trứng, sữa','DRAFT')""", batch, UUID.fromString(id), UUID.fromString(tid))
        assertField(call("${base(id)}/$tid", "PATCH", mapOf("version" to v, "safetyWarnings" to " "), owner), "safetyWarnings")
        assertField(call("${base(id)}/$tid", "PATCH", mapOf("version" to v, "defaultMinimumValue" to 10000), owner), "defaultMinimumValue")
        val r = call("${base(id)}/$tid", "PATCH", mapOf("version" to v, "safetyWarnings" to "Có gluten, trứng, sữa, đậu phộng", "defaultSellingPrice" to 35000), owner)
        assertEquals(200, r.statusCode(), r.body()); assertTrue(json(r)["version"].asLong() > v)
        val log = jdbc.queryForMap("select before_state, after_state from audit_log where action='BOX_TEMPLATE_UPDATED'")
        assertEquals(30000, mapper.readTree(log["before_state"] as String)["defaultSellingPrice"].asInt())
        assertFalse(mapper.readTree(log["after_state"] as String).has("name"))
        assertEquals(mapOf<String, Any?>("safety_warnings" to "Có gluten, trứng, sữa", "selling_price" to 30000), jdbc.queryForMap(
            "select safety_warnings, selling_price::int as selling_price from sale_batch where id=?", batch))
        assertEquals(409, call("${base(id)}/$tid", "PATCH", mapOf("version" to v, "name" to "x"), owner).statusCode())
        val v2 = json(r)["version"].asLong()
        assertEquals(listOf(200, 409), concurrently({ call("${base(id)}/$tid", "PATCH", mapOf("version" to v2, "name" to "A"), owner) },
            { call("${base(id)}/$tid", "PATCH", mapOf("version" to v2, "name" to "B"), owner) }))
    }

    @Test fun `AC-05 deactivate and reactivate, no hard delete`() {
        val (owner, _, id) = setup()
        val t = json(call(base(id), "POST", body(), owner)); val tid = t["id"].asString()
        fun st(active: Boolean) = call("${base(id)}/$tid/status", "POST", mapOf("version" to json(call("${base(id)}/$tid", token = owner))["version"].asLong(),
            "active" to active, "reason" to "Tạm ngừng"), owner)
        assertFalse(json(st(false))["active"].asBoolean()); assertEquals(409, st(false).statusCode())
        assertEquals(1, json(call("${base(id)}?active=false", token = owner)).size())
        assertTrue(json(st(true))["active"].asBoolean())
        assertTrue(call("${base(id)}/$tid", "DELETE", token = owner).statusCode() in setOf(404, 405))
        assertEquals(1, count("select count(*) from box_template"))
    }

    @Test fun `AC-06 access`() {
        val (owner, admin, id) = setup()
        val tid = json(call(base(id), "POST", body(), owner))["id"].asString()
        val packer = staffWith(id, owner, "packer@example.test", listOf("BOX_PACK"))
        val viewer = staffWith(id, owner, "viewer@example.test", listOf("MENU_VIEW"))
        val customer = user("customer@example.test"); val other = user("other@example.test"); store(other, admin, "Khác")
        assertEquals(200, call(base(id), token = packer).statusCode()); assertEquals(200, call("${base(id)}/$tid", token = admin).statusCode())
        assertEquals(403, call(base(id), "POST", body(), packer).statusCode())
        assertEquals(403, call("${base(id)}/$tid", "PATCH", mapOf("version" to 0, "name" to "x"), packer).statusCode())
        for (t in listOf(viewer, customer, other)) assertEquals(403, call(base(id), token = t).statusCode())
        assertEquals(403, call(base(id), "POST", body(), admin).statusCode())
        assertEquals(401, call(base(id)).statusCode())
    }
}
