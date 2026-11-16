// AC-05 (T6.1): FakePaymentGateway responsibility.
package com.foodrescue.backend.payment.gateway

import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory provider for tests and local development (roadmap D2): same contract and the same webhook signature algorithm as payOS,
 * with a fixed test checksum key. [webhookFor] builds a correctly signed notification; [failNext] simulates a provider failure.
 */
class FakePaymentGateway(private val mapper: ObjectMapper) : PaymentGateway {
    override val provider = "FAKE"
    private val signature = PayOsSignature(CHECKSUM_KEY, mapper)
    private val links = ConcurrentHashMap<Long, PaymentLinkInfo>()
    @Volatile var failNext: PaymentGatewayException.Kind? = null
    val created = java.util.concurrent.atomic.AtomicInteger()

    override fun createLink(request: PaymentLinkRequest): PaymentLink {
        maybeFail()
        val existing = links[request.orderCode]
        if (existing != null && existing.status == PaymentLinkStatus.PENDING)
            throw PaymentGatewayException(PaymentGatewayException.Kind.REJECTED, "payOS rejected the request: code=231 desc=Đơn thanh toán đã tồn tại")
        val id = UUID.randomUUID().toString().replace("-", "")
        links[request.orderCode] = PaymentLinkInfo(request.orderCode, id, request.amount, 0, PaymentLinkStatus.PENDING)
        created.incrementAndGet()
        return PaymentLink(request.orderCode, id, request.amount, "https://pay.payos.vn/web/$id", "FAKE-VIETQR-$id", PaymentLinkStatus.PENDING)
    }

    override fun getLink(orderCode: Long): PaymentLinkInfo {
        maybeFail()
        return links[orderCode] ?: throw PaymentGatewayException(PaymentGatewayException.Kind.REJECTED, "payOS rejected the request: code=101 desc=not found")
    }

    override fun cancelLink(orderCode: Long, reason: String): PaymentLinkInfo {
        val l = getLink(orderCode).copy(status = PaymentLinkStatus.CANCELLED); links[orderCode] = l; return l
    }

    override fun verifyWebhook(rawBody: String): WebhookPayment = PayOsPaymentGateway.verifyWith(signature, mapper, rawBody)

    /** Marks the link paid and returns a signed payOS-shaped webhook body. */
    fun webhookFor(orderCode: Long, amount: Long, reference: String = "FT" + UUID.randomUUID().toString().take(10).uppercase(), code: String = "00"): String {
        links.computeIfPresent(orderCode) { _, l -> l.copy(amountPaid = l.amountPaid + amount, status = if (l.amountPaid + amount >= l.amount) PaymentLinkStatus.PAID else l.status) }
        val data = linkedMapOf<String, Any?>("orderCode" to orderCode, "amount" to amount, "description" to "FR$orderCode", "accountNumber" to "12345678",
            "reference" to reference, "transactionDateTime" to "2026-11-16 10:00:00", "currency" to "VND", "paymentLinkId" to links[orderCode]?.paymentLinkId,
            "code" to code, "desc" to if (code == "00") "Thành công" else "Lỗi", "counterAccountBankId" to "", "counterAccountBankName" to "",
            "counterAccountName" to "", "counterAccountNumber" to "", "virtualAccountName" to "", "virtualAccountNumber" to "")
        val node = mapper.valueToTree<tools.jackson.databind.JsonNode>(data)
        return mapper.writeValueAsString(mapOf("code" to "00", "desc" to "success", "success" to (code == "00"), "data" to data, "signature" to signature.forData(node)))
    }

    fun reset() { links.clear(); failNext = null; created.set(0) }

    private fun maybeFail() { failNext?.let { failNext = null; throw PaymentGatewayException(it, "Simulated provider failure") } }

    companion object { const val CHECKSUM_KEY = "fake-checksum-key-for-tests-and-dev-only" }
}
