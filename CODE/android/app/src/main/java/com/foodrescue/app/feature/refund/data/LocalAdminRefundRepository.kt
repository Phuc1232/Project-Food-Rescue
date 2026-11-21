package com.foodrescue.app.feature.refund.data

import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.auth.domain.AdminIdentity
import com.foodrescue.app.feature.complaint.data.LocalComplaintRecords
import com.foodrescue.app.feature.complaint.domain.CustomerComplaint
import com.foodrescue.app.feature.order.data.LocalOrderHistoryStore
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.order.domain.BoxReservation
import com.foodrescue.app.feature.order.domain.OwnerCancellationReason
import com.foodrescue.app.feature.refund.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Local frontend processing only. Decisions, cancellation and audit share one ledger commit. */
internal class LocalAdminRefundRepository(context: Context, email: String,
    private val processor: RefundProcessor, private val clock: () -> Long = System::currentTimeMillis,
    private val ledger: LocalOrderLedger = LocalOrderLedger(context),
    private val persist: (List<LocalOrderLedger.Row>) -> Unit = { ledger.write(it) },
) : AdminRefundRepository {
    private val app = context.applicationContext
    private val actor = email.trim().lowercase(Locale.ROOT)
    private val complaints = LocalComplaintRecords(app)

    override suspend fun load(): List<RefundCase> = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor))
        synchronized(LocalCommerceLock) {
            val rows = ledger.read()
            val now = clock()
            val updated = rows.map { row -> row.copy(order = row.order.copy(adminRefunds = row.order.adminRefunds.map { item ->
                if (item.status != AdminRefundStatus.PROCESSING || now - item.updatedAtMillis < 2_000) item else {
                    // This adapter is synchronous and local. A network provider must reconcile unknown outcomes
                    // with the same idempotency key before permitting a new attempt.
                    val result = processor.check("${row.account}:${row.order.id}:${item.id}", row.order.reference, item.amount)
                    val status = when (result) {
                        RefundResult.COMPLETED -> AdminRefundStatus.COMPLETED
                        RefundResult.UNAVAILABLE -> AdminRefundStatus.FAILED
                    }
                    item.copy(status = status, history = item.history + RefundEvent(status, "Hệ thống", now,
                        if (status == AdminRefundStatus.COMPLETED) "Đã hoàn về phương thức thanh toán ban đầu."
                        else "Chưa kết nối được dịch vụ thanh toán. Có thể thử lại với cùng mã yêu cầu."))
                }
            })) }
            // Construct projections before committing; a projection error must not masquerade as a failed write.
            val result = project(updated)
            if (updated != rows) persist(updated)
            result
        }
    }

    override suspend fun decide(expected: RefundCase, approve: Boolean, note: String): RefundCase = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor))
        synchronized(LocalCommerceLock) {
            val rows = ledger.read()
            val current = project(rows).singleOrNull { it.key == expected.key } ?: throw StaleRefund()
            if (current != expected) throw StaleRefund()
            val order = current.decide(approve, note, actor, clock())
            val index = rows.indexOfFirst { it.account == current.accountKey && it.order.id == current.orderId }
            val next = rows.toMutableList().apply {
                val row = LocalOrderLedger.Row(current.accountKey, order)
                if (index < 0) add(row) else set(index, row)
            }
            val result = project(next).single { it.key == current.key }
            persist(next)
            result
        }
    }

    private fun project(shared: List<LocalOrderLedger.Row>): List<RefundCase> {
        val cases = complaints.accounts().associateWith(complaints::read)
        val orders = shared.toMutableList()
        cases.forEach { (account, items) ->
            val missing = items.filter { it.refundRequest != null && orders.none { row -> row.account == account && row.order.id == it.order.id } }
            if (missing.isNotEmpty()) {
                val legacy = LocalOrderHistoryStore.forAccountKey(app, account).load()
                missing.map { it.order.id }.distinct().forEach { id -> legacy.singleOrNull { it.id == id }?.let {
                    orders.add(LocalOrderLedger.Row(account, it))
                } }
            }
        }
        val result = mutableListOf<RefundCase>()
        cases.forEach { (account, items) -> items.forEach { complaint -> complaint.refundRequest?.let { request ->
            val order = orders.singleOrNull { it.account == account && it.order.id == complaint.order.id }?.order
            result += RefundCase(account, complaint.order.id, request.id, RefundSource.COMPLAINT, complaint.id,
                complaint.response.orEmpty(), request.amount, request.requestedAtMillis, order,
                order?.adminRefunds?.singleOrNull { it.id == request.id })
        } } }
        orders.forEach { row ->
            row.order.noShow?.let { noShow ->
                val terms = row.order.financialTerms
                val target = com.foodrescue.app.feature.finance.domain.revenueCommission(row.order.total, terms?.noShowRefundBps ?: 0)
                val id = "NS-${noShow.requestId}"
                val existing = row.order.adminRefunds.find { it.id == id }
                if (target > 0 && existing == null) {
                    val reserved = row.order.adminRefunds.filter { it.reservesMoney }.associate { "${it.source}:${it.sourceId}" to it.amount }.toMutableMap()
                    cases[row.account].orEmpty().filter { it.order.id == row.order.id }.forEach { complaint ->
                        complaint.refundRequest?.let { reserved["COMPLAINT:${complaint.id}"] = it.amount }
                    }
                    val amount = (target - (row.order.refund?.amount ?: 0) - reserved.values.sum()).coerceAtLeast(0)
                    if (amount > 0) result += RefundCase(row.account, row.order.id, id, RefundSource.NO_SHOW_POLICY, noShow.requestId,
                        "Hoàn theo chính sách phiên bản ${terms?.version ?: 0} sau khi xác minh khách không đến nhận.",
                        amount, noShow.atMillis, row.order)
                }
            }
            row.order.ownerCancellation?.let { request ->
                val id = "HC-${row.order.id}-${request.requestedAtMillis}"
                result += RefundCase(row.account, row.order.id, id, RefundSource.STORE_CANCELLATION, id,
                    listOf(cancellationReason(request.reason), request.note).filter(String::isNotBlank).joinToString(" · "),
                    row.order.total, request.requestedAtMillis, row.order)
            }
            row.order.adminRefunds.forEach { item ->
                if (result.none { it.accountKey == row.account && it.orderId == row.order.id && it.id == item.id }) {
                    result += RefundCase(row.account, row.order.id, item.id, item.source, item.sourceId, item.reason,
                        item.amount, item.requestedAtMillis, row.order, item)
                }
            }
        }
        require(result.distinctBy { it.key }.size == result.size)
        return result.map { item -> item.copy(otherReserved = reservedOthers(item, cases[item.accountKey].orEmpty())) }
            .sortedBy { it.requestedAtMillis }
    }

    private fun reservedOthers(item: RefundCase, cases: List<CustomerComplaint>): Long {
        val order = item.order ?: return 0
        val requests = cases.filter { it.order.id == item.orderId }.mapNotNull { complaint ->
            complaint.refundRequest?.let { "COMPLAINT:${complaint.id}" to it.amount }
        }.toMap().toMutableMap()
        order.adminRefunds.filter { it.reservesMoney }.forEach { requests["${it.source}:${it.sourceId}"] = it.amount }
        requests.remove("${item.source}:${item.sourceId}")
        return requests.values.fold(order.refund?.amount ?: 0L, Math::addExact)
    }
    private fun cancellationReason(reason: OwnerCancellationReason): String = when (reason) {
        OwnerCancellationReason.OUT_OF_STOCK -> "Không còn đủ hộp"
        OwnerCancellationReason.FOOD_QUALITY -> "Không đảm bảo chất lượng"
        OwnerCancellationReason.STORE_CLOSED -> "Cửa hàng đóng cửa"
        OwnerCancellationReason.OTHER -> "Lý do khác"
    }
}
