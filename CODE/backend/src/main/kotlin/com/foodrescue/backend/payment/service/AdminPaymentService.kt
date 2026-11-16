// AC-02,04,05 (T6.4): AdminPaymentService responsibility.
package com.foodrescue.backend.payment.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.payment.PaymentStatus
import com.foodrescue.backend.payment.dto.AdminPaymentView
import com.foodrescue.backend.payment.dto.OrderMoneySummary
import com.foodrescue.backend.payment.repository.PaymentRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.authenticationFailed
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.ResultSet
import java.util.UUID

/** Admin lookup of received money by order / transaction / status, and assignment of unmatched payments (UC-14 E1). Callers are ADMIN. */
@Service
class AdminPaymentService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val payments: PaymentRepository,
    private val orders: CustomerOrderRepository,
    private val accounts: AccountRepository,
    private val obligations: RefundObligationService,
    private val history: StoreHistoryRecorder
) {
    @Transactional(readOnly = true)
    fun search(status: PaymentStatus?, orderCode: String?, reference: String?, bucket: String?, page: Int, size: Int): PageResponse<AdminPaymentView> {
        if (page < 0 || size !in 1..100) throw ApiException(400, "INVALID_PAGINATION", "Invalid page, size or sort")
        if (bucket != null && bucket !in BUCKETS) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(FieldViolation("bucket", "One of $BUCKETS")))
        val where = mutableListOf("true"); val p = MapSqlParameterSource()
        status?.let { where += "p.status = :status"; p.addValue("status", it.name) }
        orderCode?.trim()?.takeIf { it.isNotEmpty() }?.let { where += "o.order_code = :orderCode"; p.addValue("orderCode", it) }
        reference?.trim()?.takeIf { it.isNotEmpty() }?.let { where += "p.provider_transaction_id = :ref"; p.addValue("ref", it) }
        bucket?.let { where += "$BUCKET_SQL = :bucket"; p.addValue("bucket", it) }
        val clause = where.joinToString(" AND ")
        val total = jdbc.queryForObject("SELECT count(*) $FROM WHERE $clause", p, Long::class.java)!!
        p.addValue("limit", size).addValue("offset", page * size)
        val rows = jdbc.query("$SELECT $FROM WHERE $clause ORDER BY p.received_at DESC, p.id LIMIT :limit OFFSET :offset", p) { rs, _ -> view(rs) }
        return PageResponse(rows, page, size, total, ((total + size - 1) / size).toInt())
    }

    @Transactional(readOnly = true)
    fun detail(id: UUID): AdminPaymentView =
        jdbc.query("$SELECT $FROM WHERE p.id = :id", MapSqlParameterSource("id", id)) { rs, _ -> view(rs) }.firstOrNull() ?: notFound()

    @Transactional(readOnly = true)
    fun orderMoney(orderId: UUID): OrderMoneySummary {
        val o = orders.findById(orderId).orElse(null) ?: notFound()
        val list = jdbc.query("$SELECT $FROM WHERE p.order_id = :id ORDER BY p.received_at", MapSqlParameterSource("id", orderId)) { rs, _ -> view(rs) }
        fun sum(b: String) = list.filter { it.bucket == b }.fold(BigDecimal.ZERO) { a, v -> a + v.amount }
        // T6.5: refunded = refunds actually recorded (supports partial refunds); refundPending = still owed on open obligations.
        val refunded = jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM refund WHERE order_id = :id AND status = 'SUCCEEDED'", MapSqlParameterSource("id", orderId), BigDecimal::class.java)!!.setScale(0)
        val owed = jdbc.queryForObject("SELECT coalesce(sum(coalesce(approved_amount, requested_amount)),0) FROM refund_request WHERE order_id = :id AND status IN ('PENDING','APPROVED')",
            MapSqlParameterSource("id", orderId), BigDecimal::class.java)!!.setScale(0)
        return OrderMoneySummary(o.id!!, o.orderCode, o.status.name, o.totalAmount.setScale(0), list.fold(BigDecimal.ZERO) { a, v -> a + v.amount },
            sum("APPLIED"), owed, refunded, list)
    }

    /** An unassigned payment is linked to an order on the administrator's evidence and becomes a refund obligation (never confirms the order). */
    @Transactional
    fun assign(adminId: UUID, paymentId: UUID, orderCode: String, note: String): AdminPaymentView {
        val payment = payments.findForUpdate(paymentId) ?: notFound()
        if (payment.order != null || payment.status != PaymentStatus.REQUIRES_REVIEW) throw ApiException(409, "CONFLICT", "Payment is already assigned")
        val order = jdbc.queryForList("SELECT id FROM customer_order WHERE order_code = :c", MapSqlParameterSource("c", orderCode.trim()), UUID::class.java)
            .firstOrNull()?.let { orders.findForUpdate(it) } ?: notFound()
        val admin = accounts.findById(adminId).orElse(null) ?: authenticationFailed()
        payment.order = order; payments.saveAndFlush(payment)
        obligations.open(payment, order, RefundObligationService.ASSIGNED_BY_ADMIN, note.trim())
        history.record(admin, order.store, "PAYMENT", payment.id!!, "PAYMENT_ASSIGNED", null, mapOf("orderCode" to order.orderCode, "note" to note.trim()))
        return detail(payment.id!!)
    }

    private fun view(rs: ResultSet) = AdminPaymentView(rs.getObject("id", UUID::class.java), rs.getString("provider"), rs.getString("provider_transaction_id"),
        rs.getObject("provider_order_code") as Long?, rs.getObject("order_id", UUID::class.java), rs.getString("order_code"),
        rs.getBigDecimal("received_amount").setScale(0), rs.getString("currency"), rs.getString("status"), rs.getString("bucket"),
        rs.getTimestamp("received_at").toInstant(), rs.getTimestamp("verified_at")?.toInstant(), rs.getObject("refund_id", UUID::class.java), rs.getString("reason_code"))

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")

    companion object {
        val BUCKETS = setOf("APPLIED", "REFUND_PENDING", "REFUNDED", "UNASSIGNED", "RECEIVED")
        /** BR-T64-01: exactly one bucket per payment. */
        const val BUCKET_SQL = """(CASE WHEN p.status = 'VERIFIED' THEN 'APPLIED' WHEN rr.status = 'FULFILLED' THEN 'REFUNDED'
            WHEN rr.status IN ('PENDING','APPROVED') THEN 'REFUND_PENDING' WHEN p.order_id IS NULL THEN 'UNASSIGNED' ELSE 'RECEIVED' END)"""
        const val FROM = "FROM payment p LEFT JOIN customer_order o ON o.id = p.order_id LEFT JOIN payment_request q ON q.id = p.request_id LEFT JOIN refund_request rr ON rr.payment_id = p.id"
        const val SELECT = "SELECT p.id, p.provider, p.provider_transaction_id, q.provider_order_code, p.order_id, o.order_code, p.received_amount, p.currency, p.status, " +
            "$BUCKET_SQL AS bucket, p.received_at, p.verified_at, rr.id AS refund_id, rr.reason_code"
    }
}
