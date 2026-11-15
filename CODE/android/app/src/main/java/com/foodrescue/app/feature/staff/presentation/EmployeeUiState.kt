package com.foodrescue.app.feature.staff.presentation

import com.foodrescue.app.feature.staff.domain.*

internal enum class EmployeePage { LIST, DETAIL, FORM }
internal data class EmployeeUiState(
    val employees: List<StoreEmployee> = emptyList(), val loading: Boolean = true, val busy: Boolean = false,
    val loadError: String? = null, val error: String? = null, val issues: Set<EmployeeIssue> = emptySet(),
    val page: EmployeePage = EmployeePage.LIST, val selectedId: String? = null, val query: String = "",
    val filter: EmployeeFilter = EmployeeFilter.ALL, val draft: EmployeeDraft = EmployeeDraft(), val step: Int = 0,
    val confirmDiscard: Boolean = false, val accessTarget: EmployeeStatus? = null, val accessRevision: Int = 0, val reason: String = "",
    val message: String? = null,
) {
    val selected get() = employees.firstOrNull { it.id == selectedId }
    val visible get() = employees.searchEmployees(query, filter)
    val activeCount get() = employees.count { it.status == EmployeeStatus.ACTIVE }
    val dirty get() = draft != (employees.firstOrNull { it.id == draft.id }?.toDraft() ?: EmployeeDraft())
    val canMutate get() = !loading && !busy && loadError == null
}
