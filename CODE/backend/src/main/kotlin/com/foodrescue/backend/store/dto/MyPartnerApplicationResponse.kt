// AC-01,02,13: MyPartnerApplicationResponse responsibility.
package com.foodrescue.backend.store.dto

/** Applicant view (UC-39); reviewer identity is intentionally omitted. */
data class MyPartnerApplicationResponse(val store: StoreSummary, val current: ApplicationView, val history: List<ApplicationHistoryItem>)
