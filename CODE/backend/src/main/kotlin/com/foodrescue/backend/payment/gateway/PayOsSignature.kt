// AC-01,02 (T6.1): PayOsSignature responsibility.
package com.foodrescue.backend.payment.gateway

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * payOS HMAC-SHA256 signatures (docs: "kiem-tra-du-lieu-voi-signature"). Webhook: data keys sorted, "key=value" joined by "&", null/"null"/"undefined"
 * as "", arrays as JSON, no URL encoding, lowercase hex. Payment request: amount&cancelUrl&description&orderCode&returnUrl.
 */
class PayOsSignature(private val checksumKey: String, private val mapper: ObjectMapper) {
    fun forPaymentRequest(amount: Long, cancelUrl: String, description: String, orderCode: Long, returnUrl: String): String =
        hmac("amount=$amount&cancelUrl=$cancelUrl&description=$description&orderCode=$orderCode&returnUrl=$returnUrl")

    fun forData(data: JsonNode): String {
        val keys = data.propertyNames().toList().sorted()
        return hmac(keys.joinToString("&") { "$it=${value(data[it])}" })
    }

    /** Constant-time comparison (BR-T61-01). */
    fun matches(data: JsonNode, signature: String?): Boolean =
        signature != null && MessageDigest.isEqual(forData(data).toByteArray(), signature.lowercase().toByteArray())

    private fun value(v: JsonNode?): String = when {
        v == null || v.isNull -> ""
        v.isArray -> "[" + (0 until v.size()).joinToString(",") { mapper.writeValueAsString(sortKeys(v[it])) } + "]"
        v.isObject -> mapper.writeValueAsString(sortKeys(v))
        else -> v.asString().let { if (it == "null" || it == "undefined") "" else it }
    }

    private fun sortKeys(v: JsonNode): Any? = when {
        v.isObject -> v.propertyNames().toList().sorted().associateWith { sortKeys(v[it]) }.toSortedMap()
        v.isArray -> (0 until v.size()).map { sortKeys(v[it]) }
        v.isNull -> null
        v.isNumber -> v.numberValue()
        v.isBoolean -> v.asBoolean()
        else -> v.asString()
    }

    private fun hmac(text: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(checksumKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
