// AC-01,05,07,12,13,15: StoreViews responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.audit.entity.AuditLog
import com.foodrescue.backend.file.repository.StoredFileRepository
import com.foodrescue.backend.store.StoreChangeRequestStatus
import com.foodrescue.backend.store.dto.*
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.store.entity.StoreChangeRequest
import com.foodrescue.backend.store.repository.StoreChangeRequestRepository
import com.foodrescue.backend.store.repository.StoreOpeningHourRepository
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/** Builds T3.3 API views; must run inside a transaction (lazy associations). */
@Component
class StoreViews(
    private val mapper: ObjectMapper,
    private val hours: StoreOpeningHourRepository,
    private val requests: StoreChangeRequestRepository,
    private val files: StoredFileRepository,
    private val roles: AccountRoleRepository
) {
    fun openingHours(store: Store): List<OpeningHourView> = hours.findAllByStoreIdOrderByDayOfWeekAscShiftNumberAsc(store.id!!).map {
        OpeningHourView(it.dayOfWeek, it.shiftNumber, it.opensAt.format(PartnerApplicationViews.HH_MM),
            it.closesAt.format(PartnerApplicationViews.HH_MM), it.closesNextDay)
    }

    fun profile(store: Store) = StoreProfileView(store.id!!, store.name, store.description, store.address, store.latitude,
        store.longitude, store.timezone, store.status.name, store.statusReason, store.statusChangedAt, store.version,
        openingHours(store), requests.findFirstByStoreIdAndStatus(store.id!!, StoreChangeRequestStatus.PENDING)?.let(::request))

    fun public(store: Store) = StorePublicView(store.id!!, store.name, store.description, store.address, store.latitude,
        store.longitude, store.timezone, openingHours(store))

    fun request(r: StoreChangeRequest): ChangeRequestView {
        val docs = documents(r)
        val stored = files.findAllById(docs.map { it.fileId }).associateBy { it.id }
        val documentViews = docs.mapNotNull { d -> stored[d.fileId]?.let { DocumentView(it.id!!, d.type, it.originalName, it.mimeType, it.sizeBytes) } }
        return ChangeRequestView(r.id!!, r.status.name, values(r.currentValues), values(r.proposedValues), r.note, documentViews,
            r.requestedAt, r.resolvedAt, r.resolutionReason)
    }

    fun adminRequest(r: StoreChangeRequest) = AdminChangeRequestView(request(r), StoreSummary(r.store.id!!, r.store.name, r.store.status.name),
        account(r.requestedBy), r.resolvedBy?.let(::account))

    /** Owners see the actor's role only (BR-T33-10); administrators also see the actor id. Role: OWNER, ADMIN or STAFF (T3.4). */
    fun history(log: AuditLog, store: Store, withActorId: Boolean): StoreHistoryItem {
        val actorId = log.actor?.id
        val role = when {
            actorId == store.owner.id -> "OWNER"
            actorId != null && roles.existsByAccountIdAndRole(actorId, PlatformRole.ADMIN) -> "ADMIN"
            else -> "STAFF"
        }
        return StoreHistoryItem(log.action, log.occurredAt, role,
            if (withActorId) actorId else null, log.beforeState?.let(mapper::readTree), log.afterState?.let(mapper::readTree))
    }

    fun account(a: Account) = AccountSummary(a.id!!, a.loginIdentifier, a.displayName)

    fun values(json: String): StoreChangeValues = mapper.readValue(json, StoreChangeValues::class.java)
    fun toJson(value: Any): String = mapper.writeValueAsString(value)
    /** Non-null fields only, for history before/after. */
    fun toMap(values: StoreChangeValues): Map<String, Any?> = linkedMapOf<String, Any?>("name" to values.name, "address" to values.address,
        "latitude" to values.latitude, "longitude" to values.longitude).filterValues { it != null }
    fun documents(r: StoreChangeRequest): List<SnapshotDocument> =
        mapper.readValue(r.documents, object : TypeReference<List<SnapshotDocument>>() {})
}
