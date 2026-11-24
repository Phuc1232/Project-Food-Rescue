// AC-01..11 (T8.1): AdminComplaintDetail responsibility.
package com.foodrescue.backend.complaint.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import com.foodrescue.backend.complaint.*
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.pickup.dto.*
data class AdminComplaintDetail(val complaint: CustomerComplaintDetail, val order: OrderView, val money: OrderMoneyView,
    val boxes: List<ComplaintBoxView>, val payments: List<ComplaintPaymentView>, val refundRequests: List<RefundRequestView>,
    val handover: ComplaintHandoverView, val noShowCase: NoShowCaseSummaryView?, val history: List<Map<String, Any?>>)
