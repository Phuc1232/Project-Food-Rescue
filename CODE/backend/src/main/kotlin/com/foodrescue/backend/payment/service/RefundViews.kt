// AC-01..06 (T6.5): RefundViews responsibility.
package com.foodrescue.backend.payment.service

import com.foodrescue.backend.file.repository.AttachmentRepository
import com.foodrescue.backend.payment.dto.RefundAttemptView
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.payment.entity.RefundRequest
import com.foodrescue.backend.payment.repository.RefundRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** Must run inside a transaction. */
@Component
class RefundViews(private val refunds: RefundRepository, private val jdbc: JdbcTemplate) {
    fun view(r: RefundRequest, forAdmin: Boolean): RefundRequestView {
        val attempts = refunds.findAllByRequestIdOrderByCreatedAt(r.id!!).map {
            RefundAttemptView(it.id!!, it.status.name, it.amount.setScale(0), it.providerRefundId, it.transferredAt, it.verifiedAt, it.note,
                jdbc.queryForList("SELECT file_id FROM attachment WHERE refund_id = ?", UUID::class.java, it.id).filterNotNull(), it.createdAt)
        }
        val refunded = attempts.filter { it.status == "SUCCEEDED" }.fold(java.math.BigDecimal.ZERO) { a, x -> a + x.amount }
        val p = r.payment
        return RefundRequestView(r.id!!, r.order.id!!, r.order.orderCode, r.status.name, r.reasonCode, r.reason, r.requestedAmount.setScale(0),
            r.approvedAmount?.setScale(0), refunded, p?.id, p?.providerTransactionId, p?.receivedAmount?.setScale(0),
            p?.let { (it.receivedAmount - refunds.refundedForPayment(it.id!!)).setScale(0) },
            if (forAdmin) r.order.customer.displayName else null, if (forAdmin) r.order.customer.loginIdentifier else null,
            r.createdAt, r.approvedAt, attempts)
    }
}
