// AC-01,02,03,04,07,09: FileService responsibility.
package com.foodrescue.backend.file.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.file.FileProperties
import com.foodrescue.backend.file.dto.FileResponse
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.file.repository.StoredFileRepository
import com.foodrescue.backend.file.storage.FileStorage
import com.foodrescue.backend.middleware.ratelimit.RateLimitPolicy
import com.foodrescue.backend.middleware.ratelimit.RateLimitService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.security.SecureRandom
import java.util.UUID

/** A readable file: metadata plus an open stream the caller must close. */
class FileContent(val metadata: FileResponse, val stream: InputStream)

@Service
class FileService(
    private val storage: FileStorage,
    private val files: StoredFileRepository,
    private val accounts: AccountRepository,
    private val access: FileAccessService,
    private val detector: FileTypeDetector,
    private val names: FileNameSanitizer,
    private val limits: RateLimitService,
    private val properties: FileProperties,
    private val tx: TransactionTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    /** File bytes are written outside any DB transaction; a failed insert removes them again. */
    fun upload(accountId: UUID, part: MultipartFile?): FileResponse {
        limits.check(RateLimitPolicy.UPLOAD_ACCOUNT, "account:$accountId")
        val max = properties.maxSize.toBytes()
        if (part == null) invalid("File is required")
        if (part.isEmpty) invalid("File is empty")
        if (part.size > max) invalid("File exceeds $max bytes")
        val key = newKey()
        val stored = try {
            part.inputStream.use { input ->
                val header = input.readNBytes(FileTypeDetector.HEADER_BYTES)
                val mime = detector.detect(header) ?: invalid("Unsupported file type")
                mime to storage.store(key, SequenceInputStream(header.inputStream(), input))
            }
        } catch (e: IOException) { unavailable("store", e) }
        val (mime, written) = stored
        if (written.sizeBytes > max) { discard(key); invalid("File exceeds $max bytes") }
        val saved = try {
            tx.execute {
                files.saveAndFlush(StoredFile(key, names.sanitize(part.originalFilename), mime, written.sizeBytes,
                    written.sha256Hex, accounts.getReferenceById(accountId)))
            }!!
        } catch (e: RuntimeException) { discard(key); throw e }
        return saved.toResponse()
    }

    fun metadata(accountId: UUID, fileId: UUID): FileResponse = readable(accountId, fileId).toResponse()

    fun content(accountId: UUID, fileId: UUID): FileContent {
        val file = readable(accountId, fileId)
        val stream = try { storage.open(file.storageKey) } catch (e: IOException) { unavailable("open", e) }
        return FileContent(file.toResponse(), stream)
    }

    /** Missing and forbidden look identical (404) so file ids cannot be probed. */
    private fun readable(accountId: UUID, fileId: UUID): StoredFile = tx.execute {
        files.findById(fileId).orElse(null)?.takeIf { access.canRead(accountId, it) }
    } ?: throw ApiException(404, "NOT_FOUND", "Resource not found")

    private fun newKey(): String = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private fun discard(key: String) {
        try { storage.delete(key) } catch (e: IOException) { log.error("Could not remove orphan upload", e) }
    }

    private fun invalid(message: String): Nothing =
        throw ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(FieldViolation("file", message)))

    private fun unavailable(action: String, e: IOException): Nothing {
        log.error("File storage failed to {}", action, e)
        throw ApiException(503, "SERVICE_UNAVAILABLE", "Service unavailable")
    }

    private fun StoredFile.toResponse() = FileResponse(id!!, originalName, mimeType, sizeBytes, checksum, createdAt)
}
