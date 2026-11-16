package com.foodrescue.app.feature.staff.presentation

import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.surprisebox.domain.*

internal enum class StaffPage { OVERVIEW, MENU, BOXES, HANDOVER, ACCOUNT, PACKING, DETAIL }
internal data class StaffPackingUiState(
    val workspace: StaffWorkspace = StaffWorkspace(), val loading: Boolean = true, val busy: Boolean = false,
    val verified: Boolean = false, val error: String? = null, val page: StaffPage = StaffPage.OVERVIEW,
    val draft: PackingDraft = PackingDraft(), val step: Int = 0, val query: String = "", val now: Long = 0,
    val selectedId: String? = null, val issues: Set<PackingIssue> = emptySet(), val confirmDiscard: Boolean = false,
    val boxFilter: BoxCondition? = null,
) {
    val canPrepare: Boolean get() = verified && workspace.access?.allowed == true &&
        EmployeePermission.PREPARE_BOXES in workspace.access.permissions
    val batch: SaleBatch? get() = workspace.batches.firstOrNull { it.id == draft.batchId }
    val selectedBox: PackedBox? get() = workspace.boxes.firstOrNull { it.id == selectedId }
    val canInspect: Boolean get() = verified && workspace.access.canInspectBoxes()
    val filteredBoxes: List<PackedBox> get() = workspace.boxes.filter {
        (boxFilter == null || it.conditionAt(now) == boxFilter) && query.boxSearchKey() in "${it.code} ${it.templateName}".boxSearchKey()
    }.sortedByDescending { it.packedAt }
    val estimate: BoxValueEstimate get() = estimateBoxValue(workspace.menu, draft.selections.associate { it.itemId to it.quantity })
    val validation: Set<PackingIssue> get() = draft.packingIssues(workspace.access, batch, workspace.menu, now)
    val canComplete: Boolean get() = canPrepare && !busy && step == 2 && validation.isEmpty()
    val batchesToPrepare: List<SaleBatch> get() = if (canPrepare) workspace.batches.filter { it.canPackAt(now) }.sortedBy { it.pickupStart } else emptyList()
}
