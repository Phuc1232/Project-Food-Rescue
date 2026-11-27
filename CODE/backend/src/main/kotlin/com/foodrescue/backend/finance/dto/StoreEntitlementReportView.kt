// AC-01..10 (T8.3): StoreEntitlementReportView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
data class StoreEntitlementReportView(val summary: StoreEntitlementSummaryView, val items: PageResponse<StoreEntitlementItemView>)
