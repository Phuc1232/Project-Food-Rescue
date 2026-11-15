// AC-07: AdminChangeRequestView responsibility.
package com.foodrescue.backend.store.dto

data class AdminChangeRequestView(
    val request: ChangeRequestView, val store: StoreSummary, val requestedBy: AccountSummary, val resolvedBy: AccountSummary?
)
