package com.foodrescue.app.feature.complaint.data

import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.auth.domain.AdminIdentity
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.order.data.LocalOrderHistoryStore
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.order.domain.BoxReservation
import com.foodrescue.app.feature.surprisebox.data.LocalPackedBoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal class LocalComplaintReviewRepository(context: Context, email: String,
    private val clock: () -> Long = System::currentTimeMillis) : ComplaintReviewRepository {
    private val app = context.applicationContext
    private val actor = email.trim().lowercase(Locale.ROOT)
    private val records = LocalComplaintRecords(app)
    private val ledger = LocalOrderLedger(app)
    private val boxes = LocalPackedBoxStore(app)

    override suspend fun load(): List<ComplaintReviewCase> = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor))
        synchronized(LocalCommerceLock) {
            val shared = ledger.read()
            records.accounts().flatMap { account ->
                val cases = records.read(account)
                cases.map { item -> project(account, item, cases, findOrder(account, item.order.id, shared)) }
            }.sortedBy { it.complaint.createdAtMillis }
        }
    }

    override suspend fun review(expected: ComplaintReviewCase, action: ComplaintReviewAction,
        note: String, refundAmount: Long): ComplaintReviewCase = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor))
        synchronized(LocalCommerceLock) {
            val cases = records.read(expected.accountKey)
            val current = cases.singleOrNull { it.id == expected.complaint.id } ?: throw StaleComplaintReview()
            if (current.revision != expected.complaint.revision) throw StaleComplaintReview()
            val order = findOrder(expected.accountKey, current.order.id, ledger.read())
            val available = complaintRefundableAmount(order, cases)
            if (order != expected.order || available != expected.refundableAmount) throw ChangedComplaintOrder()
            val updated = current.review(expected.complaint.revision, action, note, refundAmount, available, order, actor, clock())
            val next = cases.map { if (it.id == current.id) updated else it }
            // Build the result first: a projection failure must never report a committed decision as failed.
            val result = project(expected.accountKey, updated, next, order)
            records.write(expected.accountKey, next)
            result
        }
    }
    private fun findOrder(account: String, id: String, shared: List<LocalOrderLedger.Row>): BoxReservation? =
        shared.singleOrNull { it.account == account && it.order.id == id }?.order
            ?: LocalOrderHistoryStore.forAccountKey(app, account).load().singleOrNull { it.id == id }

    private fun project(account: String, item: CustomerComplaint, cases: List<CustomerComplaint>, order: BoxReservation?): ComplaintReviewCase {
        val ids = order?.handover?.boxIds.orEmpty() + order?.noShow?.boxIds.orEmpty()
        val physical = if (ids.isEmpty()) emptyList() else order?.boxSnapshot?.storeId?.let { storeId ->
            boxes.load(storeId).filter { it.id in ids }
        }.orEmpty()
        val projected = item.copy(refundTransaction = order?.adminRefunds?.singleOrNull { it.id == item.refundRequest?.id })
        return ComplaintReviewCase(account, projected, order, physical, complaintRefundableAmount(order, cases))
    }
}
