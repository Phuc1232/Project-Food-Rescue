// AC-12,13 (T7.1): PickupCodeCodecTests verifies authenticated encryption and key validation.
package com.foodrescue.backend

import com.foodrescue.backend.pickup.PickupCodeProperties
import com.foodrescue.backend.pickup.service.PickupCodeCodec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.UUID

class PickupCodeCodecTests {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
    private fun codec(id: String = "v1", keys: String = "v1:$key") = PickupCodeCodec(PickupCodeProperties().apply {
        activeKeyId = id; this.keys = keys
    })

    @Test fun `AC-12 random codes normalize and encrypted values are bound to order`() {
        val codec = codec(); val id = UUID.randomUUID(); val code = codec.newCode()
        assertEquals(16, code.length); assertEquals(code, codec.normalize(code.lowercase().chunked(4).joinToString(" - ")))
        assertEquals(100, (1..100).map { codec.newCode() }.toSet().size)
        val encrypted = codec.encrypt(id, code)
        assertNotEquals(encrypted, codec.encrypt(id, code)); assertFalse(encrypted.contains(code))
        assertEquals(code, codec.decrypt(id, "v1", encrypted)); assertEquals(64, codec.hash(code).length)
        assertThrows(Exception::class.java) { codec.decrypt(UUID.randomUUID(), "v1", encrypted) }
        val damaged = Base64.getDecoder().decode(encrypted).apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { codec.decrypt(id, "v1", Base64.getEncoder().encodeToString(damaged)) }
        assertThrows(Exception::class.java) { codec.decrypt(id, "missing", encrypted) }
        for (bad in listOf("", "I".repeat(16), "X".repeat(129), "0123456789ABCDE!"))
            assertThrows(Exception::class.java) { codec.normalize(bad) }
    }

    @Test fun `AC-13 configuration rejects invalid secrets without exposing them and retains old keys`() {
        for ((id, keys) in listOf("v1" to "", "v1" to "v1:invalid-secret", "v2" to "v1:$key", "v1" to "v1:$key,v1:$key",
            "v1" to "v1:" + Base64.getEncoder().encodeToString(ByteArray(16)))) {
            val e = assertThrows(IllegalArgumentException::class.java) { codec(id, keys) }
            assertFalse(e.message.orEmpty().contains(key)); assertFalse(e.message.orEmpty().contains("invalid-secret"))
        }
        val id = UUID.randomUUID(); val code = codec().newCode(); val ciphertext = codec().encrypt(id, code)
        val rotated = codec("v2", "v1:$key,v2:" + Base64.getEncoder().encodeToString(ByteArray(32) { 9 }))
        assertEquals(code, rotated.decrypt(id, "v1", ciphertext))
    }
}
