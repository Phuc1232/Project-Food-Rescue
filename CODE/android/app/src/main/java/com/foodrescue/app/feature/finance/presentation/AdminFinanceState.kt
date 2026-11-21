package com.foodrescue.app.feature.finance.presentation

import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.core.util.searchKey
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal data class AdminFinanceState(val snapshot: AdminFinanceSnapshot? = null, val busy: Boolean = false,
    val error: String? = null, val query: String = "", val filter: SettlementStatus? = null,
    val selectedKey: String? = null, val decision: SettlementDecision? = null, val confirming: Boolean = false,
    val attempted: Boolean = false, val notice: String? = null) {
    val selected: ManagedSettlement? get() = snapshot?.settlements?.find { it.key == selectedKey }
    val visible: List<ManagedSettlement> get() = snapshot?.settlements.orEmpty().filter {
        (filter == null || it.record.status == filter) &&
            (it.storeName + " " + it.record.id + " " + it.record.transferReference.orEmpty()).searchKey().contains(query.searchKey())
    }
    val ready: Long get() = revenueSum(snapshot?.settlements.orEmpty().filter {
        it.record.status in listOf(SettlementStatus.READY, SettlementStatus.FAILED)
    }.map { it.record.net })
    val paid: Long get() = revenueSum(snapshot?.settlements.orEmpty().filter { it.record.status == SettlementStatus.PAID }.map { it.record.net })
    val decisionValid: Boolean get() = decision?.let { validFinanceReason(it.reason) &&
        (it.action != SettlementAction.COMPLETE || validTransferReference(it.transferReference)) } == true
}
internal val SettlementAction.label: String get() = when (this) {
    SettlementAction.CONFIRM -> "Xác nhận đối soát"
    SettlementAction.START -> "Bắt đầu chi trả"
    SettlementAction.COMPLETE -> "Ghi nhận đã chi trả"
    SettlementAction.FAIL -> "Ghi nhận thất bại"
    SettlementAction.VOID -> "Hủy kỳ đối soát"
}
internal val SettlementIssue.message: String get() = when (this) {
    SettlementIssue.STORE -> "Cửa hàng chưa được duyệt."
    SettlementIssue.BANK -> "Cần tài khoản ngân hàng đầy đủ trước khi chi trả."
    SettlementIssue.ORDER_CHANGED -> "Có đơn đang khiếu nại, hoàn tiền hoặc đã thay đổi. Hủy kỳ chưa chi để lập lại số tiền chính xác."
    SettlementIssue.ADJUSTMENT -> "Còn khoản hoàn của kỳ đã chi cần bù trừ. Xác nhận kỳ có khoản điều chỉnh trước khi chi trả."
    SettlementIssue.AMOUNT -> "Doanh thu chưa đủ bù trừ khoản hoàn từ kỳ trước."
}

internal data class PolicyDraft(val commission: String = "20", val cancellationMinutes: String = "0",
    val cancellationRefund: String = "100", val noShowGrace: String = "0", val noShowRefund: String = "0",
    val scheduled: Boolean = false, val effectiveText: String = "", val reason: String = "") {
    fun terms(): FinancialTerms? = try {
        FinancialTerms(commissionBps = percentage(commission), cancellationMinutes = cancellationMinutes.toInt(),
            cancellationRefundBps = percentage(cancellationRefund), noShowGraceMinutes = noShowGrace.toInt(),
            noShowRefundBps = percentage(noShowRefund))
    } catch (_: IllegalArgumentException) { null } catch (_: ArithmeticException) { null }
    fun effectiveAt(now: Long): Long? {
        if (!scheduled) return now
        if (!effectiveText.matches(Regex("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}"))) return null
        return try { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).apply {
            isLenient = false; timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }.parse(effectiveText)?.time?.takeIf { it > now } } catch (_: java.text.ParseException) { null }
    }
    companion object {
        private fun percentage(value: String): Int {
            require(value.trim().matches(Regex("[0-9]{1,3}([.,][0-9]{1,2})?")))
            return BigDecimal(value.trim().replace(',', '.')).movePointRight(2).intValueExact()
        }
        fun from(terms: FinancialTerms) = PolicyDraft(revenueRate(terms.commissionBps).removeSuffix("%"),
            terms.cancellationMinutes.toString(), revenueRate(terms.cancellationRefundBps).removeSuffix("%"),
            terms.noShowGraceMinutes.toString(), revenueRate(terms.noShowRefundBps).removeSuffix("%"))
    }
}
internal data class FinancialPolicyState(val book: FinancialPolicyBook? = null, val now: Long = 0,
    val draft: PolicyDraft? = null, val busy: Boolean = false, val error: String? = null,
    val confirming: Boolean = false, val attempted: Boolean = false, val notice: String? = null) {
    val current: FinancialTerms get() = book?.termsAt(now) ?: FinancialTerms()
    val scheduled: FinancialPolicy? get() = book?.versions?.lastOrNull { it.effectiveAt > now }
    val valid: Boolean get() = draft?.let { it.terms()?.copy(version = 0) != current.copy(version = 0) &&
        it.terms() != null && it.effectiveAt(now) != null && validFinanceReason(it.reason) } == true && scheduled == null
}
