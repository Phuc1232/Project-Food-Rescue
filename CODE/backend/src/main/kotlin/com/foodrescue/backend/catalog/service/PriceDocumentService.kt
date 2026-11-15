// AC-08,09 (T4.1): PriceDocumentService responsibility.
package com.foodrescue.backend.catalog.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.catalog.dto.PriceDocumentRequest
import com.foodrescue.backend.catalog.dto.PriceDocumentView
import com.foodrescue.backend.catalog.entity.PriceReferenceDocument
import com.foodrescue.backend.catalog.repository.MenuItemRepository
import com.foodrescue.backend.catalog.repository.PriceReferenceDocumentRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.repository.AttachmentRepository
import com.foodrescue.backend.file.repository.StoredFileRepository
import com.foodrescue.backend.store.repository.StoreRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** UC-53: registering a document records evidence only; it never marks prices as verified. */
@Service
class PriceDocumentService(
    private val access: MenuAccess,
    private val stores: StoreRepository,
    private val items: MenuItemRepository,
    private val documents: PriceReferenceDocumentRepository,
    private val files: StoredFileRepository,
    private val attachments: AttachmentRepository,
    private val accounts: AccountRepository,
    private val history: StoreHistoryRecorder,
    private val views: MenuViews,
    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun list(accountId: UUID, storeId: UUID): List<PriceDocumentView> {
        access.requireRead(accountId, storeId)
        return documents.findAllByStoreIdOrderByUploadedAtDesc(storeId).map(views::document)
    }

    @Transactional
    fun add(ownerId: UUID, storeId: UUID, r: PriceDocumentRequest): PriceDocumentView {
        access.requireWrite(ownerId, storeId)
        val errors = mutableListOf<FieldViolation>()
        val file = files.findById(r.fileId!!).orElse(null)?.takeIf { it.uploadedBy.id == ownerId }
        if (file == null) errors += FieldViolation("fileId", "File not found")
        val item = r.menuItemId?.let { id -> items.findById(id).orElse(null)?.takeIf { it.store.id == storeId }
            ?: run { errors += FieldViolation("menuItemId", "Menu item not found"); null } }
        if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)
        val replaced = r.replacesDocumentId?.let { id ->
            val old = documents.findForUpdate(id)?.takeIf { it.store.id == storeId }
                ?: throw ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(FieldViolation("replacesDocumentId", "Document not found")))
            if (old.replacedBy != null) throw ApiException(409, "CONFLICT", "Document has already been replaced")
            old
        }
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        val store = stores.findById(storeId).orElse(null) ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        val doc = documents.saveAndFlush(PriceReferenceDocument(store, item, file!!, r.description!!.trim(), r.appliesFrom!!, owner, clock.instant()))
        attachments.save(Attachment(file, priceDocument = doc))
        replaced?.let { it.replacedBy = doc; documents.saveAndFlush(it) }
        history.record(owner, store, ENTITY, doc.id!!, "PRICE_DOCUMENT_ADDED", null,
            mapOf("menuItemId" to item?.id, "appliesFrom" to doc.appliesFrom.toString(), "replaces" to replaced?.id))
        return views.document(doc)
    }

    companion object { const val ENTITY = "PRICE_DOCUMENT" }
}
