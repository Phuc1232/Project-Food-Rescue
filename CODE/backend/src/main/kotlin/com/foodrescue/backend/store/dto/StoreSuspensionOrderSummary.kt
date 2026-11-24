// AC-01..12 (T7.5): StoreSuspensionOrderSummary responsibility.
package com.foodrescue.backend.store.dto
import java.util.UUID
data class StoreSuspensionOrderSummary(val retainedOrderIds: List<UUID>, val cancelledUnpaidOrderIds: List<UUID>,
    val cancelledConfirmedOrderIds: List<UUID>, val blockedBatchIds: List<UUID>, val pausedBatchIds: List<UUID>,
    val refundRequestIds: List<UUID>, val hasAuthorizedHandoverActor: Boolean) {
    val retainedConfirmedOrders: Int get() = retainedOrderIds.size
    val cancelledUnpaidOrders: Int get() = cancelledUnpaidOrderIds.size
    val cancelledConfirmedOrders: Int get() = cancelledConfirmedOrderIds.size
    val blockedSaleBatches: Int get() = blockedBatchIds.size
    val pausedSaleBatches: Int get() = pausedBatchIds.size
}
