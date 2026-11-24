// AC-01..11 (T8.1): ComplaintHandoverView responsibility.
package com.foodrescue.backend.complaint.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import com.foodrescue.backend.complaint.*
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.pickup.dto.*
data class ComplaintHandoverView(val sessions: List<PickupSessionView>, val incidents: List<PickupIncidentView>)
