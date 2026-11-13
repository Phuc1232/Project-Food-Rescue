// AC-07: FileStorageFailureTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.file.storage.FileStorage
import com.foodrescue.backend.support.AuthTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.IOException

/** The storage adapter fails on every write (disk full, unmounted volume, ...). */
class FileStorageFailureTests : AuthTestFixtures() {
    @MockitoBean lateinit var storage: FileStorage

    /** Mockito matchers return null; a generic cast keeps Kotlin from null-checking it. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> nullable(): T = null as T
    private fun anyStream(): java.io.InputStream { ArgumentMatchers.any(java.io.InputStream::class.java); return nullable() }

    @Test fun `storage failure returns 503 and creates no database row`() {
        `when`(storage.store(anyString(), anyStream())).thenThrow(IOException("No space left on device /secret/path"))
        register(); val token = login()["accessToken"].asString()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(100)
        val r = upload(png, token, "a.png", "image/png")
        assertEquals(503, r.statusCode(), r.body()); assertEquals("SERVICE_UNAVAILABLE", json(r)["code"].asString())
        assertFalse(r.body().contains("/secret/path"))
        assertEquals(0, jdbc.queryForObject("select count(*) from stored_file", Int::class.java))
    }
}
