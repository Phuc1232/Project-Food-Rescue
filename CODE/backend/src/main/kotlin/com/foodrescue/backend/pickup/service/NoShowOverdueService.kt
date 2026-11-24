// AC-01..14 (T7.4): NoShowOverdueService responsibility.
package com.foodrescue.backend.pickup.service
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import org.springframework.jdbc.core.namedparam.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Service
class NoShowOverdueService(private val jdbc: NamedParameterJdbcTemplate, private val orders: CustomerOrderRepository,
    private val history: StoreHistoryRecorder, private val sessions: com.foodrescue.backend.pickup.repository.PickupSessionRepository) {
    @Transactional
    fun markOverdue(now: Instant, batchSize: Int = 100): Int {
        require(batchSize in 1..1000)
        val ids = jdbc.queryForList("""SELECT o.id FROM customer_order o WHERE o.status='CONFIRMED' AND o.pickup_ends_at<=:now
            AND NOT EXISTS (SELECT 1 FROM pickup_session s WHERE s.order_id=o.id AND s.status IN ('OPEN','RECONCILING'))
            ORDER BY o.pickup_ends_at,o.id LIMIT :n FOR UPDATE SKIP LOCKED""",
            MapSqlParameterSource("now", Timestamp.from(now.truncatedTo(java.time.temporal.ChronoUnit.MICROS))).addValue("n", batchSize), UUID::class.java).filterNotNull()
        ids.forEach { id ->
            val order = orders.findById(id).orElseThrow()
            if (order.status != OrderStatus.CONFIRMED || order.pickupEndsAt.isAfter(now) ||
                sessions.findByOrderIdAndStatusIn(id, listOf(com.foodrescue.backend.pickup.PickupStatus.OPEN,
                    com.foodrescue.backend.pickup.PickupStatus.RECONCILING)) != null) return@forEach
            order.status = OrderStatus.PENDING_NO_SHOW; order.statusReason = "PICKUP_WINDOW_ENDED"; order.statusChangedAt = now
            history.record(null, order.store, "CUSTOMER_ORDER", id, "ORDER_MARKED_PENDING_NO_SHOW",
                mapOf("status" to "CONFIRMED"), mapOf("status" to "PENDING_NO_SHOW"))
        }
        return ids.size
    }
}
