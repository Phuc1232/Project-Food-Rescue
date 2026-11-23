// AC-02,14 (T7.1): PickupCodeBackfillService assigns missing credentials under per order locks.
package com.foodrescue.backend.pickup.service

import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Service
class PickupCodeBackfillService(private val orders: CustomerOrderRepository, private val issuer: PickupCodeIssuer,
                                private val jdbc: JdbcTemplate, private val clock: Clock) {
    fun candidates(): List<UUID> = jdbc.queryForList("""select id from customer_order where status='CONFIRMED' and pickup_ends_at > ?
        and (pickup_code_hash is null or pickup_code_encrypted is null or pickup_code_key_id is null) order by id limit 100""", UUID::class.java, Timestamp.from(clock.instant())).filterNotNull()

    @Transactional
    fun issueOne(orderId: UUID): Boolean {
        val o = orders.findForUpdate(orderId) ?: return false
        if (o.status != OrderStatus.CONFIRMED || !clock.instant().isBefore(o.pickupEndsAt)) return false
        val issued = issuer.issue(o)
        orders.saveAndFlush(o)
        return issued
    }
}
