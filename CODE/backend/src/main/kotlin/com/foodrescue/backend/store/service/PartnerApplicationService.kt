// AC-01,02,03,04,13,14: PartnerApplicationService responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.audit.AuditService
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.repository.AttachmentRepository
import com.foodrescue.backend.store.ApplicationStatus
import com.foodrescue.backend.store.StoreStatus
import com.foodrescue.backend.store.dto.MyPartnerApplicationResponse
import com.foodrescue.backend.store.dto.OpeningHourView
import com.foodrescue.backend.store.dto.PartnerApplicationRequest
import com.foodrescue.backend.store.entity.PartnerApplication
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.store.entity.StoreOpeningHour
import com.foodrescue.backend.store.repository.PartnerApplicationRepository
import com.foodrescue.backend.store.repository.StoreOpeningHourRepository
import com.foodrescue.backend.store.repository.StoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

/** created=false means an existing SUBMITTED revision was returned unchanged (UC-38 A1). */
data class SubmitResult(val application: MyPartnerApplicationResponse, val created: Boolean)

@Service
class PartnerApplicationService(
    private val accounts: AccountRepository,
    private val stores: StoreRepository,
    private val applications: PartnerApplicationRepository,
    private val hours: StoreOpeningHourRepository,
    private val attachments: AttachmentRepository,
    private val hoursValidator: OpeningHoursValidator,
    private val documentValidator: ApplicationDocumentValidator,
    private val views: PartnerApplicationViews,
    private val audit: AuditService,
    private val clock: Clock
) {
    /** The account row lock serializes submissions of the same applicant. */
    @Transactional
    fun submit(applicantId: UUID, request: PartnerApplicationRequest): SubmitResult {
        val applicant = accounts.findForUpdate(applicantId) ?: authenticationFailed()
        val existing = stores.findOpenByOwner(applicantId)
        if (existing != null && existing.status != StoreStatus.PENDING_APPROVAL)
            throw ApiException(409, "CONFLICT", "Account already owns a store")
        val latest = existing?.let { applications.findTopByStoreIdOrderByRevisionDesc(it.id!!) }
        if (existing != null && latest?.status == ApplicationStatus.SUBMITTED) return SubmitResult(views.mine(existing, latest), false)

        val timezone = request.timezone?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_ZONE
        val (shifts, hourErrors) = hoursValidator.validate(request.openingHours!!)
        val (documents, documentErrors) = documentValidator.validate(request.documents!!, applicantId)
        val errors = hourErrors + documentErrors + listOfNotNull(
            FieldViolation("timezone", "Unknown timezone").takeUnless { validZone(timezone) })
        if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)

        val name = request.storeName!!.trim(); val address = request.address!!.trim()
        val store = existing?.apply {
            this.name = name; this.address = address; latitude = request.latitude!!; longitude = request.longitude!!; this.timezone = timezone
        } ?: Store(applicant, name, address, request.latitude!!, request.longitude!!, timezone, StoreStatus.PENDING_APPROVAL)
        stores.saveAndFlush(store)
        hours.deleteAllByStoreId(store.id!!)
        hours.saveAllAndFlush(shifts.map { StoreOpeningHour(store, it.dayOfWeek, it.shiftNumber, it.opensAt, it.closesAt, it.closesNextDay) })

        val snapshot = ApplicationSnapshot(name, address, request.latitude!!, request.longitude!!, timezone, request.contactPhone!!.trim(),
            request.businessDescription?.trim()?.takeIf { it.isNotEmpty() },
            shifts.map { OpeningHourView(it.dayOfWeek, it.shiftNumber, it.opensAt.format(PartnerApplicationViews.HH_MM),
                it.closesAt.format(PartnerApplicationViews.HH_MM), it.closesNextDay) },
            documents.map { SnapshotDocument(it.type.name, it.file.id!!) })
        val application = applications.saveAndFlush(PartnerApplication(store, applicant, (latest?.revision ?: 0) + 1,
            views.toJson(snapshot), ApplicationStatus.SUBMITTED, submittedAt = clock.instant()))
        attachments.saveAll(documents.map { Attachment(it.file, partnerApplication = application) })
        audit.record(applicant, ENTITY, application.id!!, "PARTNER_APPLICATION_SUBMITTED", store, """{"revision":${application.revision}}""")
        return SubmitResult(views.mine(store, application), true)
    }

    @Transactional(readOnly = true)
    fun mine(applicantId: UUID): MyPartnerApplicationResponse {
        val store = stores.findOpenByOwner(applicantId) ?: notFound()
        val latest = applications.findTopByStoreIdOrderByRevisionDesc(store.id!!) ?: notFound()
        return views.mine(store, latest)
    }

    /** Region ids only (e.g. Asia/Ho_Chi_Minh); fixed offsets like +07:00 do not follow daylight rules. */
    private fun validZone(zone: String) = zone in ZoneId.getAvailableZoneIds()

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")

    companion object {
        const val ENTITY = "PARTNER_APPLICATION"
        const val DEFAULT_ZONE = "Asia/Ho_Chi_Minh"
    }
}
