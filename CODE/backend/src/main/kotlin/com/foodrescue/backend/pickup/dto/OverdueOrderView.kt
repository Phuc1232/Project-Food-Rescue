// AC-01..14 (T7.4): OverdueOrderView responsibility.
package com.foodrescue.backend.pickup.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.order.dto.OrderMoneyView
import com.foodrescue.backend.payment.dto.RefundRequestView
data class OverdueOrderView(val orderId: UUID, val orderCode: String, val status: String, val customerDisplayName: String,
    val quantity: Int, val pickupStartsAt: Instant, val pickupEndsAt: Instant, val boxes: List<AllocatedBoxView>,
    val money: OrderMoneyView, val projectedAmounts: NoShowAmountsView, val activeSessionId: UUID?, val activeSessionStatus: String?,
    val incidents: List<PickupIncidentView>, val customerArrivedIncident: Boolean, val noShowCase: NoShowCaseSummaryView?)
