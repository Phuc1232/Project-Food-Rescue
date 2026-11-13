// AC-01,06,07: LocalDiskFileStorage responsibility.
package com.foodrescue.backend.file.storage

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest

/** Stores each file at `<root>/ab/cd/<key>`; writes go to a temp file first and are moved atomically. */
class LocalDiskFileStorage(root: Path) : FileStorage {
    private val root: Path = root.toAbsolutePath().normalize()

    override fun store(key: String, content: InputStream): StoredObject {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, ".upload-", ".tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val size = DigestInputStream(content, digest).use { Files.copy(it, temp, StandardCopyOption.REPLACE_EXISTING) }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
            return StoredObject(size, digest.digest().joinToString("") { "%02x".format(it) })
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override fun open(key: String): InputStream = Files.newInputStream(resolve(key))

    override fun delete(key: String) { Files.deleteIfExists(resolve(key)) }

    private fun resolve(key: String): Path {
        require(KEY.matches(key)) { "Invalid storage key" }
        val path = root.resolve(key.substring(0, 2)).resolve(key.substring(2, 4)).resolve(key).normalize()
        require(path.startsWith(root)) { "Invalid storage key" }
        return path
    }

    private companion object { val KEY = Regex("^[a-f0-9]{64}$") }
}
