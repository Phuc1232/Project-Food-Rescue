package com.foodrescue.app.feature.staff.presentation

import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.staff.domain.*

internal data class StaffNoShowUiState(val workspace: HandoverWorkspace = HandoverWorkspace(),
    val draft: NoShowDraft = NoShowDraft(), val loading: Boolean = true, val current: Boolean = false,
    val busy: Boolean = false, val confirm: Boolean = false, val now: Long = 0,
    val issues: Set<NoShowIssue> = emptySet(), val error: String? = null) {
    val order get() = workspace.orders.firstOrNull { it.id == draft.orderId }
    val canEdit get() = current && !busy && workspace.access.canHandOver() && order != null && order?.noShow == null
    val availableBoxes get() = order?.let { order -> workspace.boxes.filter { it.canLinkNoShow(order) } }.orEmpty()
    val validation get() = draft.noShowIssues(workspace.access, order, workspace.boxes, now)
}
