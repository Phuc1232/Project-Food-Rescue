package com.foodrescue.app.feature.staff.presentation

import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.staff.domain.*

internal enum class HandoverFilter { WAITING, UPCOMING, OVERDUE, ATTENTION, COMPLETED, NO_SHOW }
internal data class StaffHandoverUiState(
    val workspace: HandoverWorkspace = HandoverWorkspace(), val loading: Boolean = true, val current: Boolean = false,
    val busy: Boolean = false, val query: String = "", val filter: HandoverFilter = HandoverFilter.WAITING,
    val draft: HandoverDraft = HandoverDraft("", ""), val codeVerified: Boolean = false,
    val confirm: Boolean = false, val failureForm: Boolean = false,
    val failure: HandoverFailureDraft = HandoverFailureDraft("", ""), val issues: Set<HandoverIssue> = emptySet(),
    val error: String? = null, val message: String? = null, val now: Long = 0,
) {
    val canRead get() = workspace.access?.allowed == true && EmployeePermission.VIEW_ORDERS in workspace.access.permissions
    val canWrite get() = current && !busy && workspace.access.canHandOver()
    val order get() = workspace.orders.firstOrNull { it.id == draft.orderId }
    val availableBoxes get() = order?.let { order -> workspace.boxes.filter { it.canHandOverFor(order, now) }.sortedBy { it.expiresAt } }.orEmpty()
    val validation get() = draft.handoverIssues(workspace.access, order, workspace.boxes, now)
    val ready get() = canWrite && codeVerified && validation.isEmpty()
    val visibleOrders get() = workspace.orders.filter { order ->
        val waiting = order.statusAt(now) == OrderStatus.WAITING_PICKUP
        order.isPaid && when (filter) {
            HandoverFilter.WAITING -> waiting && now >= order.pickup.startMillis && now < order.pickup.endMillis
            HandoverFilter.UPCOMING -> waiting && now < order.pickup.startMillis
            HandoverFilter.OVERDUE -> waiting && now >= order.pickup.endMillis
            HandoverFilter.NO_SHOW -> order.noShow != null
            HandoverFilter.ATTENTION -> order.noShow == null && order.collectedAtMillis == null && order.cancelledAtMillis == null &&
                (order.ownerCancellation != null || order.handoverFailures.isNotEmpty() || now >= order.pickup.endMillis)
            HandoverFilter.COMPLETED -> order.collectedAtMillis != null
        } && (query.isBlank() || "${order.reference} ${order.boxSnapshot?.name.orEmpty()}".contains(query.trim(), ignoreCase = true))
    }.sortedBy { if (filter == HandoverFilter.COMPLETED) -(it.collectedAtMillis ?: 0) else it.pickup.endMillis }
}
