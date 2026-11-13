// AC-07,13: PartnerApplicationViews responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.file.repository.StoredFileRepository
import com.foodrescue.backend.store.dto.*
import com.foodrescue.backend.store.entity.PartnerApplication
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.store.repository.PartnerApplicationRepository
import com.foodrescue.backend.store.repository.StoreOpeningHourRepository
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** Builds API views; must run inside a transaction (lazy associations). */
@Component
class PartnerApplicationViews(
    private val mapper: ObjectMapper,
    private val files: StoredFileRepository,
    private val hours: StoreOpeningHourRepository,
    private val applications: PartnerApplicationRepository
) {
    fun snapshot(application: PartnerApplication): ApplicationSnapshot =
        mapper.readValue(application.businessDetails, ApplicationSnapshot::class.java)

    fun toJson(snapshot: ApplicationSnapshot): String = mapper.writeValueAsString(snapshot)

    fun mine(store: Store, current: PartnerApplication) =
        MyPartnerApplicationResponse(StoreSummary(store.id!!, store.name, store.status.name), application(current), history(store))

    fun summary(a: PartnerApplication) = AdminApplicationSummary(a.id!!, a.store.id!!, a.store.name, account(a.applicant, true),
        a.revision, a.status.name, a.submittedAt)

    fun detail(a: PartnerApplication): AdminApplicationDetail {
        val view = application(a); val s = a.store
        val store = StoreView(s.id!!, s.status.name, s.name, s.address, s.latitude, s.longitude, s.timezone, openingHours(s))
        return AdminApplicationDetail(a.id!!, a.revision, a.status.name, a.submittedAt, a.reviewedAt, a.reviewReason,
            account(a.applicant, true), a.reviewer?.let { account(it, false) }, store, view.submission, view.documents, history(s))
    }

    private fun application(a: PartnerApplication): ApplicationView {
        val snap = snapshot(a)
        val submission = SubmissionView(snap.storeName, snap.address, snap.latitude, snap.longitude, snap.timezone,
            snap.contactPhone, snap.businessDescription, snap.openingHours)
        val stored = files.findAllById(snap.documents.map { it.fileId }).associateBy { it.id }
        val documents = snap.documents.mapNotNull { d ->
            stored[d.fileId]?.let { DocumentView(it.id!!, d.type, it.originalName, it.mimeType, it.sizeBytes) }
        }
        return ApplicationView(a.id!!, a.revision, a.status.name, a.submittedAt, a.reviewedAt, a.reviewReason, submission, documents)
    }

    private fun history(store: Store) = applications.findAllByStoreIdOrderByRevisionDesc(store.id!!)
        .map { ApplicationHistoryItem(it.id!!, it.revision, it.status.name, it.submittedAt, it.reviewedAt, it.reviewReason) }

    private fun openingHours(store: Store) = hours.findAllByStoreIdOrderByDayOfWeekAscShiftNumberAsc(store.id!!).map {
        OpeningHourView(it.dayOfWeek, it.shiftNumber, it.opensAt.format(HH_MM), it.closesAt.format(HH_MM), it.closesNextDay)
    }

    private fun account(a: Account, withEmail: Boolean) = AccountSummary(a.id!!, if (withEmail) a.loginIdentifier else null, a.displayName)

    companion object { val HH_MM: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm") }
}
