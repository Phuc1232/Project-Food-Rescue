// AC-01..14 (T7.4): NoShowCaseDetailView responsibility.
package com.foodrescue.backend.pickup.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.order.dto.OrderMoneyView
import com.foodrescue.backend.payment.dto.RefundRequestView
data class NoShowCaseDetailView(val id: UUID, val orderId: UUID, val orderCode: String, val verifiedById: UUID,
    val verifiedAt: Instant, val evidence: String, val compensationStatus: String, val notificationReceivedAt: Instant?,
    val disputeDeadline: Instant?, val amounts: NoShowAmountsView, val notificationId: UUID?, val notificationStatus: String?,
    val attachmentFileIds: List<UUID>, val refundRequests: List<RefundRequestView>, val boxes: List<AllocatedBoxView>, val incidents: List<PickupIncidentView>)
