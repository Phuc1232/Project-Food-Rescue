package com.foodrescue.app.feature.staff.presentation

import com.foodrescue.app.feature.staff.domain.BoxConditionWorkspace
import com.foodrescue.app.feature.surprisebox.domain.*

internal data class StaffBoxConditionUiState(
    val workspace: BoxConditionWorkspace = BoxConditionWorkspace(), val draft: BoxInspectionDraft = BoxInspectionDraft(),
    val loading: Boolean = true, val verified: Boolean = false, val busy: Boolean = false, val saved: Boolean = false,
    val confirm: Boolean = false, val now: Long = 0, val issues: Set<InspectionIssue> = emptySet(), val error: String? = null,
) {
    val box: PackedBox? get() = workspace.boxes.firstOrNull { it.id == draft.boxId }
    val canEdit: Boolean get() = verified && !busy && !saved && workspace.access.canInspectBoxes() && box?.canInspect() == true
    val validation: Set<InspectionIssue> get() = draft.inspectionIssues(workspace.access, box, now)
}
