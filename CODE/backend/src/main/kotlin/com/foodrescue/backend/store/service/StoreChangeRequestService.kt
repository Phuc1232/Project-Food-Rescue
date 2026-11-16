// AC-05,06,08: StoreChangeRequestService responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.repository.AttachmentRepository
import com.foodrescue.backend.store.StoreChangeRequestStatus
import com.foodrescue.backend.store.dto.ChangeRequestView
import com.foodrescue.backend.store.dto.StoreChangeRequestRequest
import com.foodrescue.backend.store.dto.StoreChangeValues
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.store.entity.StoreChangeRequest
import com.foodrescue.backend.store.repository.StoreChangeRequestRepository
import com.foodrescue.backend.store.repository.StoreRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** Owner side of UC-41 A1: D04-bound changes become a review request and are not applied (T3.3 Q1-Q4). */
@Service
class StoreChangeRequestService(
    private val profiles: StoreProfileService,
    private val stores: StoreRepository,
    private val requests: StoreChangeRequestRepository,
    private val accounts: AccountRepository,
    private val attachments: AttachmentRepository,
    private val documentValidator: ApplicationDocumentValidator,
    private val history: StoreHistoryRecorder,
    private val views: StoreViews,
    private val clock: Clock
) {
    /** The store row lock serializes requests of the same store; the partial unique index is the second guard. */
    @Transactional
    fun submit(accountId: UUID, storeId: UUID, request: StoreChangeRequestRequest): ChangeRequestView {
        profiles.authorize(accountId, storeId)
        val store = stores.findForUpdate(storeId) ?: notFound()
        if (requests.findFirstByStoreIdAndStatus(storeId, StoreChangeRequestStatus.PENDING) != null)
            throw ApiException(409, "CONFLICT", "A change request is already awaiting review")
        val (proposed, current, fieldErrors) = diff(store, request)
        val (documents, documentErrors) = documentValidator.validate(request.documents.orEmpty(), accountId, requireMandatory = false)
        val errors = fieldErrors + documentErrors
        if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)

        val owner = accounts.findById(accountId).orElse(null) ?: authenticationFailed()
        val saved = try {
            requests.saveAndFlush(StoreChangeRequest(store, owner, views.toJson(current), views.toJson(proposed),
                views.toJson(documents.map { SnapshotDocument(it.type.name, it.file.id!!) }),
                request.note?.trim()?.takeIf { it.isNotEmpty() }, StoreChangeRequestStatus.PENDING, clock.instant()))
        } catch (_: DataIntegrityViolationException) {
            throw ApiException(409, "CONFLICT", "A change request is already awaiting review")
        }
        attachments.saveAll(documents.map { Attachment(it.file, storeChangeRequest = saved) })
        history.record(owner, store, StoreHistoryRecorder.CHANGE_REQUEST, saved.id!!, "STORE_CHANGE_REQUESTED",
            views.toMap(current), views.toMap(proposed) + mapOf("note" to saved.note, "documents" to documents.size))
        return views.request(saved)
    }

    @Transactional(readOnly = true)
    fun list(accountId: UUID, storeId: UUID): List<ChangeRequestView> {
        profiles.authorize(accountId, storeId)
        return requests.findAllByStoreIdOrderByRequestedAtDesc(storeId).map(views::request)
    }

    @Transactional
    fun cancel(accountId: UUID, storeId: UUID, requestId: UUID): ChangeRequestView {
        profiles.authorize(accountId, storeId)
        val request = requests.findForUpdate(requestId)?.takeIf { it.store.id == storeId } ?: notFound()
        if (request.status != StoreChangeRequestStatus.PENDING) throw ApiException(409, "CONFLICT", "Change request has already been resolved")
        val owner = accounts.findById(accountId).orElse(null) ?: authenticationFailed()
        request.status = StoreChangeRequestStatus.CANCELLED; request.resolvedBy = owner; request.resolvedAt = clock.instant()
        requests.saveAndFlush(request)
        history.record(owner, request.store, StoreHistoryRecorder.CHANGE_REQUEST, request.id!!, "STORE_CHANGE_CANCELLED",
            null, mapOf("status" to request.status.name))
        return views.request(request)
    }

    /** Only fields that differ from the store are kept; latitude and longitude travel together (T3.3 P4). */
    private fun diff(store: Store, r: StoreChangeRequestRequest): Triple<StoreChangeValues, StoreChangeValues, List<FieldViolation>> {
        val errors = mutableListOf<FieldViolation>()
        val name = r.name?.trim()?.also { if (it.isEmpty()) errors += FieldViolation("name", "Must not be blank") }
        val address = r.address?.trim()?.also { if (it.isEmpty()) errors += FieldViolation("address", "Must not be blank") }
        if ((r.latitude == null) != (r.longitude == null))
            errors += FieldViolation(if (r.latitude == null) "latitude" else "longitude", "Latitude and longitude must be sent together")
        if (errors.isNotEmpty()) return Triple(StoreChangeValues(), StoreChangeValues(), errors)
        val newName = name?.takeIf { it != store.name }
        val newAddress = address?.takeIf { it != store.address }
        val moved = r.latitude != null && (r.latitude.compareTo(store.latitude) != 0 || r.longitude!!.compareTo(store.longitude) != 0)
        val proposed = StoreChangeValues(newName, newAddress, if (moved) r.latitude else null, if (moved) r.longitude else null)
        val current = StoreChangeValues(store.name.takeIf { newName != null }, store.address.takeIf { newAddress != null },
            store.latitude.takeIf { moved }, store.longitude.takeIf { moved })
        if (proposed == StoreChangeValues()) errors += FieldViolation("changes", "No change from the current store data")
        return Triple(proposed, current, errors)
    }

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")
}
