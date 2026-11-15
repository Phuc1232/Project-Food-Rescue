package com.foodrescue.app.feature.menu

import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.menu.data.*
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class MenuPersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "menu_qa_${UUID.randomUUID()}_"
    private val preferences = mutableSetOf<String>()
    private val directory = File(base.cacheDir, prefix).apply { mkdirs() }
    private val sources = mutableListOf<Uri>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            preferences += prefix + name
            return base.getSharedPreferences(prefix + name, mode)
        }
    }
    private val owner = object : OwnerStoreRepository {
        var status = StoreReviewStatus.APPROVED
        override suspend fun load() = OwnerStore(status = status)
        override suspend fun saveDraft(profile: OwnerStoreProfile): OwnerStore = error("Unused")
        override suspend fun submit(profile: OwnerStoreProfile): OwnerStore = error("Unused")
        override suspend fun updateProfile(profile: OwnerStoreProfile): OwnerStore = error("Unused")
        override suspend fun updatePayout(payout: StorePayoutAccount): OwnerStore = error("Unused")
    }
    private val assets = LocalMenuAssetStore(context, "owner@example.com")
    private fun repository(email: String = "owner@example.com") = LocalMenuRepository(context, email, owner, LocalMenuAssetStore(context, email))

    @After fun cleanup() {
        sources.forEach { base.contentResolver.delete(it, null, null) }
        preferences.forEach { base.deleteSharedPreferences(it) }
        check(directory.canonicalPath.startsWith(base.cacheDir.canonicalPath + File.separator))
        directory.deleteRecursively()
    }
    private fun source(mime: String): Uri {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val collection = if (mime == "application/pdf") MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return checkNotNull(base.contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "FoodRescue-menu-check-${UUID.randomUUID()}" + if (mime == "application/pdf") ".pdf" else ".png")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (mime == "application/pdf") "Download/FoodRescueChecks" else "Pictures/FoodRescueChecks")
        })).also { sources += it }
    }
    private fun image(): Uri = source("image/png").also { uri ->
        val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(31, 75, 58)) }
        base.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun editsAndStatusPersistAcrossAccountCaseWithoutDuplicatingOrLeaking() = runBlocking {
        val repo = repository()
        val first = repo.save(MenuDraft(name = "Bánh mì bơ tỏi", price = "25000", category = MenuCategory.BAKERY)).items.single()
        val edited = repo.save(first.toDraft().copy(price = "29000", description = "Bánh nướng bơ tỏi")).items.single()
        assertEquals(first.id, edited.id)
        assertEquals(first.createdAtMillis, edited.createdAtMillis)
        val stopped = repo.setStatus(first.id, MenuStatus.INACTIVE).items.single()
        assertEquals(29_000L, stopped.price)
        assertEquals(MenuStatus.INACTIVE, stopped.status)
        assertEquals(repo.load(), repository(" OWNER@EXAMPLE.COM ").load())
        assertTrue(repository("another@example.com").load().items.isEmpty())
        assertEquals(MenuStatus.ACTIVE, repo.setStatus(first.id, MenuStatus.ACTIVE).items.single().status)
    }

    @Test fun unapprovedAndDuplicateWritesLeaveExistingMenuIntact() = runBlocking {
        val repo = repository()
        val draft = MenuDraft(name = "Bánh mì", price = "25000")
        val saved = repo.save(draft)
        try { repo.save(draft.copy(name = "  BÁNH MÌ  ")); fail("Duplicate accepted") } catch (e: MenuValidationException) { assertTrue(MenuField.DUPLICATE in e.fields) }
        owner.status = StoreReviewStatus.PENDING
        try { repo.setStatus(saved.items.single().id, MenuStatus.INACTIVE); fail("Unapproved write") } catch (_: IllegalStateException) {}
        assertEquals(saved, repo.load())
    }

    @Test fun importedPhotoSurvivesOriginalRemovalAndDocumentDeletionKeepsPhoto() = runBlocking {
        val uri = image()
        val photo = assets.import(uri.toString(), false)
        val repo = repository()
        repo.save(MenuDraft(name = "Bánh mì", price = "25000", photo = photo))
        base.contentResolver.delete(uri, null, null); sources.remove(uri)
        assertTrue(assets.isAvailable(photo))
        assertFalse(LocalMenuAssetStore(context, "another@example.com").isAvailable(photo))
        val document = assets.import(image().toString(), true)
        repo.addDocument(document)
        val restored = repository().load()
        assertEquals(photo, restored.items.single().photo)
        assertEquals(document, restored.documents.single())
        repo.removeDocument(document.id)
        assertFalse(assets.isAvailable(document))
        assertTrue(assets.isAvailable(photo))
    }

    @Test fun pdfCopiesAllPagesAndDocumentLimitIsEnforced() = runBlocking {
        val uri = source("application/pdf")
        val pdf = PdfDocument()
        try {
            repeat(2) { index -> val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 600, index + 1).create())
                page.canvas.drawColor(if (index == 0) Color.WHITE else Color.LTGRAY); pdf.finishPage(page) }
            base.contentResolver.openOutputStream(uri)!!.use { pdf.writeTo(it) }
        } finally { pdf.close() }
        try { assets.import(uri.toString(), false); fail("PDF accepted as dish photo") } catch (e: MenuAssetException) { assertEquals(MenuAssetIssue.UNSUPPORTED, e.issue) }
        val repo = repository()
        repeat(MAX_MENU_DOCUMENTS) {
            val asset = assets.import(uri.toString(), true)
            assertEquals(2, asset.pageCount)
            repo.addDocument(asset)
        }
        val extra = assets.import(uri.toString(), true)
        try { repo.addDocument(extra); fail("Document limit exceeded") } catch (e: MenuAssetException) { assertEquals(MenuAssetIssue.LIMIT, e.issue) }
        assets.prune(repo.load().assetIds)
        assertFalse(assets.isAvailable(extra))
        assertEquals(MAX_MENU_DOCUMENTS, repository().load().documents.size)
    }

    @Test fun corruptOversizedAndFileUrisAreRejectedWithoutResidualFiles() = runBlocking {
        val bad = source("image/png")
        base.contentResolver.openOutputStream(bad)!!.use { it.write("not an image".toByteArray()) }
        val large = source("image/png")
        base.contentResolver.openOutputStream(large)!!.use { it.write(ByteArray(MAX_MENU_FILE_BYTES.toInt() + 1)) }
        listOf(bad.toString() to MenuAssetIssue.UNSUPPORTED, large.toString() to MenuAssetIssue.TOO_LARGE,
            "file:///sdcard/test.png" to MenuAssetIssue.UNSUPPORTED).forEach { (uri, expected) ->
            try { assets.import(uri, false); fail("Invalid attachment accepted") } catch (e: MenuAssetException) { assertEquals(expected, e.issue) }
        }
        assertTrue(directory.walkTopDown().none { it.isFile })
    }
}
