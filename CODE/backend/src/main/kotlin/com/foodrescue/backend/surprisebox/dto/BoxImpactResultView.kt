// AC-01..12 (T7.5): BoxImpactResultView responsibility.
package com.foodrescue.backend.surprisebox.dto
import java.util.UUID
data class BoxImpactResultView(val box: BoxDetail, val outcome: String, val cancelledOrderIds: List<UUID>,
    val refundRequestIds: List<UUID>, val complaintId: UUID?, val releasedSafeBoxIds: List<UUID>, val cancelledOrders: Int = cancelledOrderIds.size)
