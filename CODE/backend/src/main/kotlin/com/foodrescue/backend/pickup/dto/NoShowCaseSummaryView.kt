// AC-01..14 (T7.4): NoShowCaseSummaryView responsibility.
package com.foodrescue.backend.pickup.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.order.dto.OrderMoneyView
import com.foodrescue.backend.payment.dto.RefundRequestView
data class NoShowCaseSummaryView(val id: UUID, val orderId: UUID, val verifiedById: UUID, val verifiedAt: Instant,
    val evidence: String, val compensationStatus: String, val notificationReceivedAt: Instant?, val disputeDeadline: Instant?)
