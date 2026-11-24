// AC-01..11 (T8.1): AdminComplaintSummary responsibility.
package com.foodrescue.backend.complaint.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import com.foodrescue.backend.complaint.*
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.pickup.dto.*
data class AdminComplaintSummary(val id: UUID, val complaintCode: String, val orderId: UUID, val orderCode: String, val type: ComplaintType,
    val status: ComplaintStatus, val withinDisputeWindow: Boolean, val approvedRefundAmount: BigDecimal?,
    val outcome: ComplaintOutcome?, val responsibility: ComplaintResponsibility?, val createdAt: Instant, val resolvedAt: Instant?, val closedAt: Instant?, val storeId: UUID, val storeName: String,
    val customerDisplayName: String, val customerEmail: String?, val verifiedPaid: BigDecimal, val heldCompensationAmount: BigDecimal)
