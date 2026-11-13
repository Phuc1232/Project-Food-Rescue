// AC-02,03: ProfileUpdateRequestParserTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.account.service.ProfileUpdateRequestParser
import com.foodrescue.backend.common.exception.ApiException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger

class ProfileUpdateRequestParserTests {
    private val parser = ProfileUpdateRequestParser()

    private fun rejected(body: Map<String, Any?>?): ApiException =
        assertThrows(ApiException::class.java) { parser.parse(body) }

    private fun fields(body: Map<String, Any?>?): List<String> = rejected(body).fields.map { it.field }

    @Test fun `valid body is trimmed and keeps version`() {
        val command = parser.parse(mapOf("displayName" to "  Nguyễn Văn A \t", "version" to 3))
        assertEquals("Nguyễn Văn A", command.displayName)
        assertEquals(3L, command.version)
        assertEquals(7L, parser.parse(mapOf("displayName" to "A", "version" to 7L)).version)
    }

    @Test fun `display name length counts code points between 1 and 255`() {
        assertEquals(255, parser.parse(mapOf("displayName" to "a".repeat(255), "version" to 0)).displayName.length)
        assertEquals(255, parser.parse(mapOf("displayName" to "😀".repeat(255), "version" to 0)).displayName.codePointCount(0, 510))
        assertEquals(listOf("displayName"), fields(mapOf("displayName" to "a".repeat(256), "version" to 0)))
        assertEquals(listOf("displayName"), fields(mapOf("displayName" to "😀".repeat(256), "version" to 0)))
    }

    @Test fun `invalid display names are rejected with the field name`() {
        for (value in listOf("", "   ", "line\nbreak", "tab\there", "nul\u0000", 123, true, null, listOf("a"), mapOf("a" to "b"))) {
            val error = rejected(mapOf("displayName" to value, "version" to 0))
            assertEquals(400, error.status); assertEquals("VALIDATION_FAILED", error.code)
            assertEquals(listOf("displayName"), error.fields.map { it.field }, value.toString())
        }
        assertEquals(listOf("displayName"), fields(mapOf("version" to 0)))
    }

    @Test fun `invalid versions are rejected with the field name`() {
        for (value in listOf(-1, "1", 1.0, BigInteger("99999999999999999999"), true, null))
            assertEquals(listOf("version"), fields(mapOf("displayName" to "A", "version" to value)), value.toString())
        assertEquals(listOf("version"), fields(mapOf("displayName" to "A")))
    }

    @Test fun `non editable fields are each rejected and reported together with other violations`() {
        val error = rejected(mapOf("displayName" to "A", "version" to 0, "roles" to listOf("ADMIN"), "email" to "x@example.test",
            "password" to "Long-password-123!", "stores" to emptyList<Any>(), "status" to "ACTIVE", "id" to "x", "phone" to "0900000000"))
        assertEquals(listOf("email", "id", "password", "phone", "roles", "status", "stores"), error.fields.map { it.field })
        assertTrue(error.fields.all { it.message == "Field is not editable" })
        assertEquals(listOf("displayName", "roles", "version"), fields(mapOf("displayName" to "", "roles" to "ADMIN")))
    }

    @Test fun `null body is an invalid request`() {
        val error = rejected(null)
        assertEquals(400, error.status); assertEquals("INVALID_REQUEST", error.code)
    }
}
