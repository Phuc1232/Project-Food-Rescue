package com.foodrescue.app.feature.complaint.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.core.net.toUri
import android.provider.OpenableColumns
import com.foodrescue.app.feature.complaint.domain.*
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

internal fun complaintAccountKey(email: String): String = MessageDigest.getInstance("SHA-256")
    .digest(email.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Copies only explicitly selected images. Original and bounded preview remain private to this account. */
internal class LocalEvidenceStore(context: Context, email: String) : ComplaintEvidenceStore {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.applicationContext.filesDir, "complaint-evidence/" + complaintAccountKey(email))
    private val validId = Regex("[a-f0-9-]{36}")

    override suspend fun import(sourceUri: String): EvidenceImportResult {
        val jobContext = currentCoroutineContext()
        var created: ComplaintEvidence? = null
        try {
            return runInterruptible(Dispatchers.IO) {
                val uri = sourceUri.toUri()
                if (uri.scheme != "content") return@runInterruptible EvidenceImportResult.Failed(EvidenceIssue.UNSUPPORTED)
                val mime = resolver.getType(uri).orEmpty()
                if (mime !in setOf("image/jpeg", "image/png", "image/webp", "image/heif", "image/heic")) {
                    return@runInterruptible EvidenceImportResult.Failed(EvidenceIssue.UNSUPPORTED)
                }
                var name = "Ảnh bằng chứng"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex) && cursor.getLong(sizeIndex) > MAX_EVIDENCE_BYTES) {
                            return@runInterruptible EvidenceImportResult.Failed(EvidenceIssue.TOO_LARGE)
                        }
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty().substringAfterLast('/').substringAfterLast('\\').take(120)
                    }
                }
                directory.mkdirs()
                val id = UUID.randomUUID().toString()
                val original = File(directory, "$id.source")
                val preview = File(directory, "$id.jpg")
                var keep = false
                try {
                    var size = 0L
                    val input = resolver.openInputStream(uri) ?: return@runInterruptible EvidenceImportResult.Failed(EvidenceIssue.UNREADABLE)
                    input.use { source -> original.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            jobContext.ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            size += count
                            if (size > MAX_EVIDENCE_BYTES) return@runInterruptible EvidenceImportResult.Failed(EvidenceIssue.TOO_LARGE)
                            output.write(buffer, 0, count)
                        }
                    } }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(original.path, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) {
                        return@runInterruptible EvidenceImportResult.Failed(EvidenceIssue.UNSUPPORTED)
                    }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
                    val bitmap = BitmapFactory.decodeFile(original.path, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: return@runInterruptible EvidenceImportResult.Failed(EvidenceIssue.UNREADABLE)
                    val orientation = try { ExifInterface(original.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                        catch (_: IOException) { ExifInterface.ORIENTATION_NORMAL }
                    val oriented = orient(bitmap, orientation)
                    try {
                        jobContext.ensureActive()
                        preview.outputStream().use { output ->
                            if (!oriented.compress(Bitmap.CompressFormat.JPEG, 88, output)) throw IOException("Cannot prepare preview")
                        }
                    } finally { if (oriented !== bitmap) oriented.recycle(); bitmap.recycle() }
                    jobContext.ensureActive()
                    val evidence = restore(id, name.ifBlank { "Ảnh bằng chứng" }, mime, size)
                    created = evidence
                    keep = true
                    EvidenceImportResult.Added(evidence)
                } finally { if (!keep) { original.delete(); preview.delete() } }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { created?.let(::removeFiles) }
            throw cancelled
        } catch (_: IOException) { return EvidenceImportResult.Failed(EvidenceIssue.UNREADABLE) }
        catch (_: SecurityException) { return EvidenceImportResult.Failed(EvidenceIssue.UNREADABLE) }
        catch (_: IllegalArgumentException) { return EvidenceImportResult.Failed(EvidenceIssue.UNREADABLE) }
    }

    internal fun restore(id: String, name: String, mime: String, size: Long): ComplaintEvidence {
        require(validId.matches(id) && size in 1..MAX_EVIDENCE_BYTES)
        val preview = File(directory, "$id.jpg")
        return ComplaintEvidence(id, name.take(120), mime, size, if (preview.isFile) Uri.fromFile(preview).toString() else "")
    }

    override fun isAvailable(evidence: ComplaintEvidence): Boolean = validId.matches(evidence.id) &&
        File(directory, "${evidence.id}.source").isFile && File(directory, "${evidence.id}.jpg").isFile

    override suspend fun remove(evidence: ComplaintEvidence) = withContext(Dispatchers.IO) { removeFiles(evidence) }

    private fun removeFiles(evidence: ComplaintEvidence) {
        if (!validId.matches(evidence.id)) return
        File(directory, "${evidence.id}.source").delete()
        File(directory, "${evidence.id}.jpg").delete()
    }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
