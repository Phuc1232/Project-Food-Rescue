// AC-01..14 (T7.2): HTTP handover integration tests with PostgreSQL.
package com.foodrescue.backend

import com.foodrescue.backend.payment.gateway.FakePaymentGateway
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.support.StoreTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import java.time.Instant
import java.util.UUID

@Import(PickupClockConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PickupHandoverTests : StoreTestFixtures() {
    @Autowired lateinit var clock: PickupTestClock
    @Autowired lateinit var gateway: PaymentGateway
    private lateinit var owner: String
    private lateinit var customer: String
    private lateinit var store: String
    private lateinit var orderId: UUID
    private lateinit var startsAt: Instant
    private lateinit var endsAt: Instant
    private val key = "handover-key"
    @BeforeEach fun setup() {
        clock.time = Instant.now(); (gateway as FakePaymentGateway).reset()
        owner = user("owner@example.test"); val admin = makeAdmin("admin@example.test")
        store = store(owner, admin); openAllWeek(store, owner)
        val batch = openOffer(store, owner, boxes = 4)
        customer = user("customer@example.test")
        orderId = UUID.fromString(json(placeOrder(customer, batch, 2))["id"].asString())
        val code = json(call("/api/v1/orders/$orderId/payment", "POST", token = customer))["providerOrderCode"].asLong()
        val paid = call("/api/v1/payments/webhooks/payos", "POST", (gateway as FakePaymentGateway).webhookFor(code, 60000, "handover-payment"))
        assertEquals(200, paid.statusCode(), paid.body())
        startsAt = jdbc.queryForObject("select pickup_starts_at from customer_order where id=?", java.sql.Timestamp::class.java, orderId)!!.toInstant()
        endsAt = jdbc.queryForObject("select pickup_ends_at from customer_order where id=?", java.sql.Timestamp::class.java, orderId)!!.toInstant()
        clock.time = startsAt
        owner = login("owner@example.test")["accessToken"].asString()
        customer = login("customer@example.test")["accessToken"].asString()
    }
    private fun start(k: String = key, token: String? = owner, shop: String = store) = callWithHeader(
        "/api/v1/stores/$shop/orders/$orderId/pickup-sessions/start", "POST", null, token, "Idempotency-Key", k)
    private fun complete(session: String, k: String = key, token: String? = owner) = callWithHeader(
        "/api/v1/stores/$store/pickup-sessions/$session/complete", "POST", null, token, "Idempotency-Key", k)
    private fun incident(body: Any = mapOf("reason" to "Damaged food", "customerArrived" to true), token: String? = owner) =
        call("/api/v1/stores/$store/orders/$orderId/pickup-incidents", "POST", body, token)
    private fun state() = jdbc.queryForObject("select status from customer_order where id=?", String::class.java, orderId)
    private fun error(code: String, response: java.net.http.HttpResponse<String>) {
        assertEquals(409, response.statusCode(), response.body()); assertEquals(code, json(response)["code"].asString())
    }
    @Test fun `AC-01 list is read only with money boxes date status and active session`() {
        val path = "/api/v1/stores/$store/pickup-orders"
        val version = jdbc.queryForObject("select version from customer_order where id=?", Long::class.java, orderId)
        val r = call(path, token = owner); assertEquals(200, r.statusCode(), r.body())
        val row = json(r)[0]; assertEquals(2, row["boxes"].size()); assertEquals(60000, row["money"]["verifiedPaid"].asInt())
        assertEquals(orderId.toString(), row["orderId"].asString()); assertTrue(row["activeSessionId"].isNull)
        assertEquals(version, jdbc.queryForObject("select version from customer_order where id=?", Long::class.java, orderId))
        assertEquals(0, count("select count(*) from pickup_session"))
        assertEquals(0, json(call("$path?date=2000-01-01", token = owner)).size())
        assertEquals(400, call("$path?status=COMPLETED", token = owner).statusCode())
        val id = json(start())["id"].asString()
        assertEquals(id, json(call("$path?status=HANDOVER_IN_PROGRESS", token = owner))[0]["activeSessionId"].asString())
        assertEquals(0, json(call("$path?status=CONFIRMED", token = owner)).size())
    }
    @Test fun `AC-02 all endpoints enforce authentication and pickup permission`() {
        val viewer = staffWith(store, owner, "viewer@example.test", listOf("ORDER_VIEW"))
        val session = json(start())["id"].asString()
        for (token in listOf(null, customer, viewer)) {
            val expected = if (token == null) 401 else 403
            assertEquals(expected, call("/api/v1/stores/$store/pickup-orders", token = token).statusCode())
            assertEquals(expected, start(token = token).statusCode())
            assertEquals(expected, complete(session, token = token).statusCode())
            assertEquals(expected, incident(token = token).statusCode())
        }
        val other = user("other@example.test"); val shop = store(other, login("admin@example.test")["accessToken"].asString(), "Other")
        assertEquals(403, start(shop = shop).statusCode())
        assertEquals(404, start(token = other, shop = shop).statusCode())
        val staff = staffWith(store, owner, "staff@example.test", listOf("PICKUP_HANDLE"))
        assertEquals(200, complete(session, token = staff).statusCode())
    }
    @Test fun `AC-03,08,09,10 start complete and replay update all records exactly once`() {
        val r = start(); assertEquals(201, r.statusCode(), r.body()); val session = json(r)["id"].asString()
        assertEquals("OPEN", json(r)["status"].asString()); assertEquals("HANDOVER_IN_PROGRESS", state())
        assertEquals(session, json(start())["id"].asString())
        assertEquals(1, count("select count(*) from pickup_session"))
        error("SESSION_ALREADY_OPEN", start("other"))
        val done = complete(session); assertEquals(200, done.statusCode(), done.body())
        assertEquals("COMPLETED", state()); assertEquals("COMPLETED", json(done)["status"].asString())
        assertFalse(json(done)["completedAt"].isNull)
        assertEquals(2, count("select count(*) from box_allocation where order_id=? and status='DELIVERED'", orderId))
        assertEquals(2, count("select count(*) from physical_box where status='DELIVERED'"))
        assertEquals(200, complete(session).statusCode()); error("SESSION_ALREADY_COMPLETED", complete(session, "other"))
        assertEquals(1, count("select count(*) from financial_event where order_id=? and type='ORDER_COMPLETED'", orderId))
        assertEquals(1, count("select count(*) from audit_log where action='HANDOVER_COMPLETED'"))
    }
    @Test fun `AC-04 pickup window is inclusive at start exclusive at end`() {
        for (time in listOf(startsAt.minusSeconds(1), endsAt, endsAt.plusSeconds(1))) {
            clock.time = time; owner = login("owner@example.test")["accessToken"].asString(); error("OUTSIDE_PICKUP_WINDOW", start()); assertEquals("CONFIRMED", state())
        }
        clock.time = endsAt.minusNanos(1); owner = login("owner@example.test")["accessToken"].asString(); assertEquals(201, start().statusCode())
    }
    @Test fun `AC-05 insufficient verified payment blocks start`() {
        jdbc.update("update payment set status='REJECTED' where order_id=?", orderId)
        error("PAYMENT_INCOMPLETE", start()); assertEquals(0, count("select count(*) from pickup_session"))
    }
    @Test fun `AC-06 invalid order states and invalid keys are rejected`() {
        for (s in listOf("CANCELLED", "EXPIRED", "COMPLETED", "NO_SHOW", "PENDING_NO_SHOW")) {
            jdbc.update("update customer_order set status=? where id=?", s, orderId); error("INVALID_ORDER_STATUS", start())
        }
        assertEquals(400, start(" ").statusCode())
        assertEquals(400, call("/api/v1/stores/$store/orders/$orderId/pickup-sessions/start", "POST", token = owner).statusCode())
    }
    @Test fun `AC-07 concurrent starts serialize and concurrent completions replay`() {
        assertEquals(listOf(201, 409), concurrently({ start() }, { start("other") }))
        val row = jdbc.queryForMap("select id,idempotency_key from pickup_session")
        assertEquals(listOf(200, 200), concurrently({ complete(row["id"].toString(), row["idempotency_key"].toString()) },
            { complete(row["id"].toString(), row["idempotency_key"].toString()) }))
        assertEquals(1, count("select count(*) from financial_event where type='ORDER_COMPLETED'"))
    }
    @Test fun `AC-11 incident cancels session blocks affected box and prevents completion`() {
        val session = json(start())["id"].asString()
        val box = jdbc.queryForObject("select box_id from box_allocation where order_id=? limit 1", UUID::class.java, orderId)!!
        val r = incident(mapOf("reason" to "Unsafe food", "customerArrived" to true, "affectedBoxIds" to listOf(box)))
        assertEquals(201, r.statusCode(), r.body()); assertEquals(session, json(r)["sessionId"].asString())
        assertEquals("PENDING_REVIEW", json(r)["resolution"].asString())
        assertEquals(1, count("select count(*) from pickup_session where status='CANCELLED'"))
        assertEquals(1, count("select count(*) from physical_box where id=? and status='BLOCKED'", box))
        error("INVALID_ORDER_STATUS", complete(session)); error("INVALID_ORDER_STATUS", start())
        assertEquals(0, count("select count(*) from financial_event where type='ORDER_COMPLETED'"))
    }
    @Test fun `AC-11 incident validates body ownership and blocks restart without an open session`() {
        assertEquals(400, incident(mapOf("reason" to " ", "customerArrived" to true)).statusCode())
        assertEquals(400, incident(mapOf("reason" to "Missing arrival")).statusCode())
        error("INVALID_AFFECTED_BOX", incident(mapOf("reason" to "Wrong box", "customerArrived" to false, "affectedBoxIds" to listOf(UUID.randomUUID()))))
        assertEquals(0, count("select count(*) from pickup_incident"))
        assertEquals(201, incident().statusCode()); error("PICKUP_INCIDENT_PENDING", start())
    }
    @Test fun `AC-12 completed orders use the complaint flow`() {
        val session = json(start())["id"].asString(); assertEquals(200, complete(session).statusCode())
        error("ORDER_ALREADY_COMPLETED", incident())
    }
    @Test fun `AC-13 current customer cancellation cannot change an open handover`() {
        start(); assertEquals(409, call("/api/v1/orders/$orderId/cancel", "POST", mapOf("reason" to "Cancel"), customer).statusCode())
        assertEquals("HANDOVER_IN_PROGRESS", state()); assertEquals(1, count("select count(*) from pickup_session where status='OPEN'"))
    }
    @Test fun `AC-09 all boxes required and event failure rolls back the full transaction`() {
        val session = json(start())["id"].asString()
        jdbc.update("update physical_box set status='BLOCKED' where id=(select box_id from box_allocation where order_id=? limit 1)", orderId)
        error("BOXES_NOT_READY", complete(session)); assertEquals("HANDOVER_IN_PROGRESS", state())
        jdbc.update("update physical_box set status='ALLOCATED' where status='BLOCKED'")
        jdbc.execute("alter table financial_event add constraint test_reject_handover check (type <> 'ORDER_COMPLETED')")
        try {
            assertEquals(409, complete(session).statusCode()); assertEquals("HANDOVER_IN_PROGRESS", state())
            assertEquals(2, count("select count(*) from box_allocation where order_id=? and status='CONFIRMED'", orderId))
            assertEquals(1, count("select count(*) from pickup_session where status='OPEN'"))
        } finally { jdbc.execute("alter table financial_event drop constraint test_reject_handover") }
        assertEquals(200, complete(session).statusCode())
    }
    @Test fun `AC-03,09 missing allocation or blocked box prevents starting and completing`() {
        jdbc.update("update physical_box set status='BLOCKED' where id=(select box_id from box_allocation where order_id=? limit 1)", orderId)
        error("BOXES_NOT_READY", start()); assertEquals("CONFIRMED", state())
        jdbc.update("update physical_box set status='ALLOCATED' where status='BLOCKED'")
        jdbc.update("update box_allocation set status='RELEASED',released_at=now(),release_reason='Test missing box' where id=(select id from box_allocation where order_id=? limit 1)", orderId)
        error("BOXES_NOT_READY", start()); assertEquals(0, count("select count(*) from pickup_session"))
    }
    @Test fun `AC-14 docs expose handover operations and schemas`() {
        val docs = json(call("/openapi.yaml"))
        for (path in listOf("/api/v1/stores/{storeId}/pickup-orders", "/api/v1/stores/{storeId}/orders/{orderId}/pickup-sessions/start",
            "/api/v1/stores/{storeId}/pickup-sessions/{sessionId}/complete", "/api/v1/stores/{storeId}/orders/{orderId}/pickup-incidents")) assertTrue(docs["paths"].has(path))
        assertTrue(docs["components"]["schemas"].has("PickupIncidentCreateRequest"))
    }
}
