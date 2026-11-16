// AC-03,04,05 (T6.1): PayOsPaymentGateway responsibility.
package com.foodrescue.backend.payment.gateway

import com.foodrescue.backend.payment.gateway.PaymentGatewayException.Kind
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException

/** payOS adapter (https://payos.vn/docs/api/). Network only — callers must not hold a database transaction (CLAUDE.md §5). */
class PayOsPaymentGateway(private val properties: PayOsProperties, private val mapper: ObjectMapper) : PaymentGateway {
    override val provider = "PAYOS"
    private val log = LoggerFactory.getLogger(PayOsPaymentGateway::class.java)
    private val signature = PayOsSignature(properties.checksumKey, mapper)
    private val http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build()

    override fun createLink(request: PaymentLinkRequest): PaymentLink {
        val body = linkedMapOf<String, Any>("orderCode" to request.orderCode, "amount" to request.amount, "description" to request.description,
            "cancelUrl" to request.cancelUrl, "returnUrl" to request.returnUrl,
            "signature" to signature.forPaymentRequest(request.amount, request.cancelUrl, request.description, request.orderCode, request.returnUrl))
        request.expiresAt?.let { body["expiredAt"] = it.epochSecond }
        val d = call("POST", "/v2/payment-requests", body)
        return PaymentLink(d.long("orderCode"), d.text("paymentLinkId"), d.long("amount"), d.text("checkoutUrl"), d.text("qrCode"),
            PaymentLinkStatus.parse(d["status"]?.asString()))
    }

    override fun getLink(orderCode: Long): PaymentLinkInfo = info(call("GET", "/v2/payment-requests/$orderCode", null))

    override fun cancelLink(orderCode: Long, reason: String): PaymentLinkInfo =
        info(call("POST", "/v2/payment-requests/$orderCode/cancel", mapOf("cancellationReason" to reason)))

    override fun verifyWebhook(rawBody: String): WebhookPayment = verifyWith(signature, mapper, rawBody)

    private fun info(d: JsonNode) = PaymentLinkInfo(d.long("orderCode"), d.text("id"), d.long("amount"), d["amountPaid"]?.asLong() ?: 0,
        PaymentLinkStatus.parse(d["status"]?.asString()))

    /** Returns `data` of a `code = "00"` response; everything else becomes a typed exception without secrets. */
    private fun call(method: String, path: String, body: Any?): JsonNode {
        if (!properties.configured()) throw PaymentGatewayException(Kind.NOT_CONFIGURED, "payOS credentials are not configured")
        val request = HttpRequest.newBuilder(URI.create(properties.baseUrl.trimEnd('/') + path)).timeout(properties.readTimeout)
            .header("x-client-id", properties.clientId).header("x-api-key", properties.apiKey).header("Content-Type", "application/json")
            .method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build()
        val response = try { http.send(request, HttpResponse.BodyHandlers.ofString()) }
            catch (e: HttpTimeoutException) { throw PaymentGatewayException(Kind.TIMEOUT, "payOS did not answer in time ($method $path)", e) }
            catch (e: IOException) { throw PaymentGatewayException(Kind.NETWORK, "payOS unreachable ($method $path): ${e.javaClass.simpleName}", e) }
        if (response.statusCode() >= 500) throw PaymentGatewayException(Kind.UNAVAILABLE, "payOS error HTTP ${response.statusCode()} ($method $path)")
        val root = try { mapper.readTree(response.body()) } catch (e: Exception) {
            throw PaymentGatewayException(Kind.INVALID_RESPONSE, "payOS returned an unreadable body (HTTP ${response.statusCode()})", e) }
        val code = root["code"]?.asString()
        if (response.statusCode() >= 400 || code != "00") {
            log.warn("payOS rejected {} {}: HTTP {} code {}", method, path, response.statusCode(), code)
            throw PaymentGatewayException(Kind.REJECTED, "payOS rejected the request: code=$code desc=${root["desc"]?.asString()}")
        }
        return root["data"]?.takeIf { it.isObject } ?: throw PaymentGatewayException(Kind.INVALID_RESPONSE, "payOS response has no data")
    }

    companion object {
        /** Shared by the fake: signature first, then parse (BR-T61-01). */
        fun verifyWith(signature: PayOsSignature, mapper: ObjectMapper, rawBody: String): WebhookPayment {
            val root = try { mapper.readTree(rawBody) } catch (_: Exception) { throw InvalidSignatureException() }
            val data = root["data"]
            if (data == null || !data.isObject || !signature.matches(data, root["signature"]?.asString())) throw InvalidSignatureException()
            fun req(f: String) = data[f]?.takeIf { !it.isNull }?.asString() ?: throw InvalidSignatureException()
            return WebhookPayment(req("orderCode").toLongOrNull() ?: throw InvalidSignatureException(), req("amount").toLongOrNull() ?: throw InvalidSignatureException(),
                req("reference"), data["paymentLinkId"]?.takeIf { !it.isNull }?.asString(), data["code"]?.asString() ?: "",
                data["transactionDateTime"]?.asString(), data["currency"]?.asString() ?: "VND", root["success"]?.asBoolean() ?: false)
        }
    }

    private fun JsonNode.long(f: String): Long = this[f]?.takeIf { it.isNumber || it.isString }?.asString()?.toLongOrNull()
        ?: throw PaymentGatewayException(Kind.INVALID_RESPONSE, "payOS response is missing $f")
    private fun JsonNode.text(f: String): String = this[f]?.takeIf { !it.isNull }?.asString()
        ?: throw PaymentGatewayException(Kind.INVALID_RESPONSE, "payOS response is missing $f")
}
