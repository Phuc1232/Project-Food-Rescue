// AC-01,06,07: LocalDiskFileStorageTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.file.storage.LocalDiskFileStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class LocalDiskFileStorageTests {
    @TempDir lateinit var root: Path
    private val key = "ab" + "cd" + "0".repeat(60)

    @Test fun `stores under sharded path with checksum and reads back`() {
        val storage = LocalDiskFileStorage(root)
        val content = ByteArray(70_000) { (it % 251).toByte() }
        val stored = storage.store(key, content.inputStream())
        assertEquals(70_000L, stored.sizeBytes)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }, stored.sha256Hex)
        assertTrue(Files.isRegularFile(root.resolve("ab").resolve("cd").resolve(key)))
        assertArrayEquals(content, storage.open(key).use { it.readAllBytes() })
        assertEquals(1, Files.walk(root).filter { Files.isRegularFile(it) }.count(), "no temp files left behind")
        storage.delete(key); assertFalse(Files.exists(root.resolve("ab/cd/$key")))
        storage.delete(key) // idempotent
    }

    @Test fun `rejects keys that are not 64 lowercase hex characters`() {
        val storage = LocalDiskFileStorage(root)
        for (bad in listOf("../../etc/passwd", "..", "", "A".repeat(64), "a".repeat(63), "a".repeat(65), "ab/cd/" + "0".repeat(58), "0".repeat(63) + "/")) {
            assertThrows(IllegalArgumentException::class.java) { storage.store(bad, ByteArray(1).inputStream()) }
            assertThrows(IllegalArgumentException::class.java) { storage.open(bad) }
            assertThrows(IllegalArgumentException::class.java) { storage.delete(bad) }
        }
        assertEquals(0, Files.walk(root).filter { Files.isRegularFile(it) }.count())
    }

    @Test fun `failed write leaves no partial file`() {
        val storage = LocalDiskFileStorage(root)
        val failing = object : java.io.InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 10_000) 1 else throw java.io.IOException("client aborted")
        }
        assertThrows(java.io.IOException::class.java) { storage.store(key, failing) }
        assertEquals(0, Files.walk(root).filter { Files.isRegularFile(it) }.count())
    }

    @Test fun `unusable root raises IOException instead of writing elsewhere`() {
        val notADirectory = Files.createFile(root.resolve("plain-file"))
        assertThrows(java.io.IOException::class.java) { LocalDiskFileStorage(notADirectory).store(key, ByteArray(10).inputStream()) }
        assertEquals(1, Files.walk(root).filter { Files.isRegularFile(it) }.count())
    }

    @Test fun `missing file raises NoSuchFileException`() {
        assertThrows(java.nio.file.NoSuchFileException::class.java) { LocalDiskFileStorage(root).open(key) }
    }
}
