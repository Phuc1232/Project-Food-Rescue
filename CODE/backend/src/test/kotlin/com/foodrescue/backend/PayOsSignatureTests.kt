// AC-01,02 (T6.1): PayOsSignatureTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.payment.gateway.InvalidSignatureException
import com.foodrescue.backend.payment.gateway.PayOsPaymentGateway
import com.foodrescue.backend.payment.gateway.PayOsSignature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PayOsSignatureTests {
    private val mapper = JsonMapper.builder().build()
    // Sample from https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/ (read 2026-11-16).
    private val docKey = "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675"
    private val docData = """{"orderCode":123,"amount":3000,"description":"VQRIO123","accountNumber":"12345678","reference":"TF230204212323",
        "transactionDateTime":"2023-02-04 18:25:00","currency":"VND","paymentLinkId":"124c33293c43417ab7879e14c8d9eb18","code":"00","desc":"Thành công",
        "counterAccountBankId":"","counterAccountBankName":"","counterAccountName":"","counterAccountNumber":"","virtualAccountName":"","virtualAccountNumber":""}"""
    private val docSignature = "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03"
    private fun body(data: String = docData, signature: String? = docSignature) =
        """{"code":"00","desc":"success","success":true,"data":$data${if (signature != null) ",\"signature\":\"$signature\"" else ""}}"""

    @Test fun `AC-01 documented webhook sample verifies`() {
        val sig = PayOsSignature(docKey, mapper)
        assertEquals(docSignature, sig.forData(mapper.readTree(docData)))
        val p = PayOsPaymentGateway.verifyWith(sig, mapper, body())
        assertEquals(123, p.orderCode); assertEquals(3000, p.amount); assertEquals("TF230204212323", p.reference); assertEquals("00", p.transactionCode)
        assertTrue(p.success)
    }

    @Test fun `AC-01 tampered, wrong or missing signatures are rejected`() {
        val sig = PayOsSignature(docKey, mapper)
        assertThrows(InvalidSignatureException::class.java) { PayOsPaymentGateway.verifyWith(sig, mapper, body(docData.replace("3000", "300000"))) }
        assertThrows(InvalidSignatureException::class.java) { PayOsPaymentGateway.verifyWith(sig, mapper, body(signature = "0".repeat(64))) }
        assertThrows(InvalidSignatureException::class.java) { PayOsPaymentGateway.verifyWith(sig, mapper, body(signature = null)) }
        assertThrows(InvalidSignatureException::class.java) { PayOsPaymentGateway.verifyWith(PayOsSignature("other-key", mapper), mapper, body()) }
        assertThrows(InvalidSignatureException::class.java) { PayOsPaymentGateway.verifyWith(sig, mapper, "not json") }
        assertEquals(docSignature, PayOsSignature(docKey, mapper).forData(mapper.readTree(docData.replace("\"currency\":\"VND\"", "\"currency\":\"VND\"").trimIndent())))
        // upper-case hex from a client is accepted (same bytes)
        assertEquals(123, PayOsPaymentGateway.verifyWith(sig, mapper, body(signature = docSignature.uppercase())).orderCode)
    }

    @Test fun `null and nested values follow the documented rules`() {
        val sig = PayOsSignature(docKey, mapper)
        assertEquals(sig.forData(mapper.readTree("""{"a":"","b":"x"}""")), sig.forData(mapper.readTree("""{"b":"x","a":null}""")))
        assertEquals(sig.forData(mapper.readTree("""{"a":""}""")), sig.forData(mapper.readTree("""{"a":"undefined"}""")))
        assertEquals(hmac(docKey, """items=[{"name":"x","price":1}]"""), sig.forData(mapper.readTree("""{"items":[{"price":1,"name":"x"}]}""")))
    }

    @Test fun `AC-02 payment request signature uses the five documented fields`() {
        val sig = PayOsSignature(docKey, mapper)
        assertEquals(hmac(docKey, "amount=30000&cancelUrl=https://a.test/c&description=FR1&orderCode=1&returnUrl=https://a.test/r"),
            sig.forPaymentRequest(30000, "https://a.test/c", "FR1", 1, "https://a.test/r"))
    }

    private fun hmac(key: String, text: String): String = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }
        .doFinal(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
