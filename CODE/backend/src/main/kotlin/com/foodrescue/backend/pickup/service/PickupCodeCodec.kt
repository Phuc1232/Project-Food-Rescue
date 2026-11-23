// AC-01,07,12,13 (T7.1): PickupCodeCodec generates and protects order bound pickup codes.
package com.foodrescue.backend.pickup.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.pickup.PickupCodeProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

@Component
class PickupCodeCodec(properties: PickupCodeProperties) {
    private val keyRing = properties.keyRing()
    val activeKeyId = properties.activeKeyId
    private val random = SecureRandom()
    private val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun newCode(): String = buildString { repeat(16) { append(alphabet[random.nextInt(32)]) } }

    fun normalize(input: String): String {
        val code = input.uppercase(Locale.ROOT).filterNot { it == '-' || it.isWhitespace() }
        if (input.length > 128 || code.length != 16 || code.any { it !in alphabet })
            throw ApiException(400, "VALIDATION_FAILED", "Invalid pickup code", listOf(FieldViolation("code", "Must be a 16 character pickup code")))
        return code
    }

    fun hash(code: String): String = MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun encrypt(orderId: UUID, code: String): String = try {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyRing.getValue(activeKeyId), GCMParameterSpec(128, iv))
        cipher.updateAAD(orderId.toString().toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(iv + cipher.doFinal(code.toByteArray(Charsets.UTF_8)))
    } catch (_: Exception) { throw ApiException(503, "SERVICE_UNAVAILABLE", "Pickup code could not be issued") }

    fun decrypt(orderId: UUID, keyId: String, encrypted: String): String = try {
        val bytes = Base64.getDecoder().decode(encrypted)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyRing.getValue(keyId), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(orderId.toString().toByteArray(Charsets.UTF_8))
        val code = String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        require(code.length == 16 && code.all { it in alphabet })
        code
    } catch (_: Exception) { throw ApiException(503, "SERVICE_UNAVAILABLE", "Pickup code is temporarily unavailable") }
}
