// AC-03,04,05 (T6.1): PayOsPaymentGatewayTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.payment.gateway.*
import com.foodrescue.backend.payment.gateway.PaymentGatewayException.Kind
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Duration
import java.time.Instant

/** payOS adapter against a local fake HTTP server — never the real provider (CLAUDE.md §6). */
class PayOsPaymentGatewayTests {
    private val mapper = JsonMapper.builder().build()
    private var server: HttpServer? = null
    private val seen = mutableListOf<Triple<String, String, Map<String, String>>>() // method, path, headers
    private var lastBody = ""

    private fun serve(status: Int, response: String, delayMs: Long = 0): PayOsProperties {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            seen += Triple(ex.requestMethod, ex.requestURI.path, mapOf("x-client-id" to ex.requestHeaders.getFirst("x-client-id"), "x-api-key" to ex.requestHeaders.getFirst("x-api-key")))
            lastBody = ex.requestBody.readAllBytes().toString(Charsets.UTF_8)
            if (delayMs > 0) Thread.sleep(delayMs)
            val bytes = response.toByteArray(); ex.sendResponseHeaders(status, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
        }
        s.start(); server = s
        return props("http://127.0.0.1:${s.address.port}")
    }
    private fun props(url: String) = PayOsProperties().apply { clientId = "client-123"; apiKey = "api-SECRET-456"; checksumKey = "checksum-SECRET-789"
        baseUrl = url; readTimeout = Duration.ofMillis(800); connectTimeout = Duration.ofMillis(800) }
    private fun gw(p: PayOsProperties) = PayOsPaymentGateway(p, mapper)
    private val req = PaymentLinkRequest(4521, 30000, "FR4521", "https://app.test/r", "https://app.test/c", Instant.ofEpochSecond(1_800_000_000))
    private fun noSecrets(e: Exception) { for (s in listOf("SECRET", "client-123")) assertFalse(e.toString().contains(s), e.toString()) }
    @AfterEach fun stop() { server?.stop(0) }

    @Test fun `AC-03 create, get and cancel speak the documented protocol`() {
        val p = serve(200, """{"code":"00","desc":"success","data":{"bin":"970422","accountNumber":"1","accountName":"A","amount":30000,"description":"FR4521",
            "orderCode":4521,"currency":"VND","paymentLinkId":"abc123","status":"PENDING","checkoutUrl":"https://pay.payos.vn/web/abc123","qrCode":"000201..."},"signature":"x"}""")
        val link = gw(p).createLink(req)
        assertEquals("abc123", link.paymentLinkId); assertEquals("https://pay.payos.vn/web/abc123", link.checkoutUrl); assertEquals(PaymentLinkStatus.PENDING, link.status)
        assertEquals(Triple("POST", "/v2/payment-requests", mapOf("x-client-id" to "client-123", "x-api-key" to "api-SECRET-456")), seen.last())
        val sent = mapper.readTree(lastBody)
        assertTrue(sent["orderCode"].isNumber); assertEquals(4521, sent["orderCode"].asInt()); assertEquals(1_800_000_000, sent["expiredAt"].asLong())
        assertEquals(PayOsSignature("checksum-SECRET-789", mapper).forPaymentRequest(30000, "https://app.test/c", "FR4521", 4521, "https://app.test/r"), sent["signature"].asString())
        server!!.stop(0)
        val p2 = serve(200, """{"code":"00","desc":"success","data":{"id":"abc123","orderCode":4521,"amount":30000,"amountPaid":30000,"amountRemaining":0,"status":"PAID"}}""")
        val info = gw(p2).getLink(4521); assertEquals(PaymentLinkStatus.PAID, info.status); assertEquals(30000, info.amountPaid)
        assertEquals("/v2/payment-requests/4521", seen.last().second)
        gw(p2).cancelLink(4521, "Order expired")
        assertEquals(Triple("POST", "/v2/payment-requests/4521/cancel", seen.last().third), seen.last()); assertEquals("Order expired", mapper.readTree(lastBody)["cancellationReason"].asString())
        assertEquals(PaymentLinkStatus.UNKNOWN, PaymentLinkStatus.parse("WEIRD"))
    }

    @Test fun `AC-04 failures are typed and never leak credentials`() {
        fun kind(p: PayOsProperties) = assertThrows(PaymentGatewayException::class.java) { gw(p).createLink(req) }.also(::noSecrets).kind
        assertEquals(Kind.REJECTED, kind(serve(200, """{"code":"231","desc":"Đơn thanh toán đã tồn tại","data":null}"""))); stop()
        assertEquals(Kind.REJECTED, kind(serve(401, """{"code":"401","desc":"Missing API Key & Client Key"}"""))); stop()
        assertEquals(Kind.UNAVAILABLE, kind(serve(503, "oops"))); stop()
        assertEquals(Kind.INVALID_RESPONSE, kind(serve(200, "<html>"))); stop()
        assertEquals(Kind.INVALID_RESPONSE, kind(serve(200, """{"code":"00","desc":"success","data":{"orderCode":4521}}"""))); stop()
        assertEquals(Kind.TIMEOUT, kind(serve(200, "{}", delayMs = 2000))); stop()
        val closed = ServerSocket(0).use { it.localPort }
        assertEquals(Kind.NETWORK, kind(props("http://127.0.0.1:$closed")))
    }

    @Test fun `AC-05 missing credentials`() {
        val p = PayOsProperties()
        val e = assertThrows(PaymentGatewayException::class.java) { gw(p).getLink(1) }; assertEquals(Kind.NOT_CONFIGURED, e.kind)
        assertFalse(p.toString().contains("key", ignoreCase = false) && p.toString().contains("SECRET"))
        assertThrows(IllegalArgumentException::class.java) { PaymentGatewayConfiguration().payOsPaymentGateway(p, mapper) }
        assertFalse(props("http://x").toString().contains("SECRET"))
    }
}
