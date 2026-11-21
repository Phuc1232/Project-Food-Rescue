package com.foodrescue.app.feature.menu.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.foodrescue.app.feature.menu.domain.*
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.util.UUID

internal class LocalMenuAssetStore private constructor(context: Context, private val directory: File) : MenuAssetStore {
    constructor(context: Context, email: String) : this(context, File(context.applicationContext.filesDir, "owner-menu/" + menuAccountKey(email)))
    private val resolver = context.applicationContext.contentResolver
    private val validId = Regex("[a-f0-9-]{36}")

    override suspend fun import(sourceUri: String, allowPdf: Boolean): MenuAsset {
        val job = currentCoroutineContext()
        var imported: MenuAsset? = null
        try {
            return runInterruptible(Dispatchers.IO) {
                val uri = sourceUri.toUri()
                if (uri.scheme != "content") throw MenuAssetException(MenuAssetIssue.UNSUPPORTED)
                val mime = resolver.getType(uri).orEmpty()
                if (mime !in imageTypes && !(allowPdf && mime == "application/pdf")) throw MenuAssetException(MenuAssetIssue.UNSUPPORTED)
                var name = if (mime == "application/pdf") "Tài liệu.pdf" else "Ảnh món ăn"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex) && cursor.getLong(sizeIndex) > MAX_MENU_FILE_BYTES) throw MenuAssetException(MenuAssetIssue.TOO_LARGE)
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty().substringAfterLast('/').substringAfterLast('\\')
                            .filterNot(Char::isISOControl).take(120).ifBlank { name }
                    }
                }
                check(directory.isDirectory || directory.mkdirs())
                val id = UUID.randomUUID().toString()
                val source = File(directory, "$id.source")
                val preview = File(directory, "$id.jpg")
                var keep = false
                try {
                    var size = 0L
                    (resolver.openInputStream(uri) ?: throw IOException()).use { input -> source.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            job.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            size += read
                            if (size > MAX_MENU_FILE_BYTES) throw MenuAssetException(MenuAssetIssue.TOO_LARGE)
                            output.write(buffer, 0, read)
                        }
                    } }
                    if (size == 0L) throw MenuAssetException(MenuAssetIssue.UNREADABLE)
                    val pages = if (mime == "application/pdf") preparePdf(source, preview) else { prepareImage(source, preview); 1 }
                    job.ensureActive()
                    val asset = restore(id, name, mime, size, pages)
                    imported = asset
                    keep = true
                    asset
                } finally { if (!keep) { source.delete(); preview.delete() } }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { imported?.let(::removeFiles) }
            throw cancelled
        } catch (issue: MenuAssetException) { throw issue
        } catch (_: IOException) { throw MenuAssetException(MenuAssetIssue.UNREADABLE)
        } catch (_: SecurityException) { throw MenuAssetException(MenuAssetIssue.UNREADABLE)
        } catch (_: IllegalArgumentException) { throw MenuAssetException(MenuAssetIssue.UNSUPPORTED)
        } catch (_: IllegalStateException) { throw MenuAssetException(MenuAssetIssue.UNREADABLE) }
    }

    internal fun restore(id: String, name: String, mime: String, bytes: Long, pages: Int): MenuAsset {
        require(validId.matches(id) && bytes in 1..MAX_MENU_FILE_BYTES && pages > 0)
        val preview = File(directory, "$id.jpg")
        val source = File(directory, "$id.source")
        return MenuAsset(id, name.take(120), mime, bytes, if (preview.isFile) Uri.fromFile(preview).toString() else "",
            if (source.isFile) Uri.fromFile(source).toString() else "", pages)
    }

    internal fun isAvailable(asset: MenuAsset) = validId.matches(asset.id) &&
        File(directory, "${asset.id}.source").isFile && File(directory, "${asset.id}.jpg").isFile

    override suspend fun remove(asset: MenuAsset) = withContext(Dispatchers.IO) { removeFiles(asset) }

    override suspend fun prune(retainedIds: Set<String>): Unit = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { file ->
            val id = file.name.substringBeforeLast('.')
            if (validId.matches(id) && file.extension in setOf("source", "jpg") && id !in retainedIds) {
                try { file.delete() } catch (_: SecurityException) { /* Retry on next load. */ }
            }
        }
        Unit
    }

    private fun removeFiles(asset: MenuAsset) {
        if (!validId.matches(asset.id)) return
        try { File(directory, "${asset.id}.source").delete(); File(directory, "${asset.id}.jpg").delete() }
        catch (_: SecurityException) { /* A later orphan cleanup can retry. */ }
    }

    private fun preparePdf(source: File, preview: File): Int {
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount < 1) throw MenuAssetException(MenuAssetIssue.UNSUPPORTED)
                renderer.openPage(0).use { page ->
                    val width = 800
                    val height = (width.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 1600)
                    val bitmap = createBitmap(width, height)
                    try { bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); writePreview(bitmap, preview) }
                    finally { bitmap.recycle() }
                }
                return renderer.pageCount
            }
        }
    }

    private fun prepareImage(source: File, preview: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000) throw MenuAssetException(MenuAssetIssue.UNSUPPORTED)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
        val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw IOException()
        var oriented = bitmap
        try {
            val orientation = try { ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                catch (_: IOException) { ExifInterface.ORIENTATION_NORMAL }
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            }
            if (!matrix.isIdentity) oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            writePreview(oriented, preview)
        } finally { if (oriented !== bitmap) oriented.recycle(); bitmap.recycle() }
    }

    private fun writePreview(bitmap: Bitmap, output: File) {
        output.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) throw IOException() }
    }

    companion object {
        private val imageTypes = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
        fun forAccountKey(context: Context, key: String): LocalMenuAssetStore {
            require(key.matches(Regex("[0-9a-f]{64}")))
            return LocalMenuAssetStore(context, File(context.applicationContext.filesDir, "owner-menu/$key"))
        }
    }
}
