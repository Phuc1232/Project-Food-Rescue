// AC-01,02,03 (T5.3): OrderExpiryService responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * BR-23/BR-24: expires unpaid orders whose hold ended. Rows are claimed with FOR UPDATE SKIP LOCKED, so two runs (or two instances)
 * never process the same order, and an order locked by a payment confirmation (T6.3) is skipped and re-read next time.
 */
@Service
class OrderExpiryService(private val jdbc: NamedParameterJdbcTemplate, private val orders: CustomerOrderRepository, private val releaser: OrderReleaser) {
    @Transactional
    fun expireDue(now: Instant, batchSize: Int = 100): Int {
        val ids = jdbc.queryForList("""SELECT id FROM customer_order WHERE status = 'PENDING_PAYMENT' AND hold_expires_at <= :now
            ORDER BY hold_expires_at LIMIT :n FOR UPDATE SKIP LOCKED""", MapSqlParameterSource("now", Timestamp.from(now)).addValue("n", batchSize),
            UUID::class.java).filterNotNull()
        ids.forEach { id ->
            val order = orders.findById(id).orElseThrow()
            if (order.status == OrderStatus.PENDING_PAYMENT) releaser.release(order, OrderStatus.EXPIRED, REASON, null, "ORDER_EXPIRED")
        }
        return ids.size
    }

    companion object { const val REASON = "HOLD_EXPIRED" }
}
