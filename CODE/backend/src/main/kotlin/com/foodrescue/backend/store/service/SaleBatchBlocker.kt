// AC-09: SaleBatchBlocker responsibility.
package com.foodrescue.backend.store.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

/**
 * BR-55: when a store is suspended, its batches that have no order yet stop (BLOCKED). Batches with orders are left
 * for T7.5. Runs inside the caller's transaction, after the store row is locked.
 */
@Component
class SaleBatchBlocker(private val jdbc: JdbcTemplate, private val clock: Clock) {
    fun blockBatchesWithoutOrders(storeId: UUID): Int = jdbc.update(
        """UPDATE sale_batch b SET status = 'BLOCKED', version = b.version + 1, updated_at = ?
           WHERE b.store_id = ? AND b.status IN ('DRAFT', 'OPEN', 'PAUSED')
             AND NOT EXISTS (SELECT 1 FROM customer_order o WHERE o.sale_batch_id = b.id)""",
        Timestamp.from(clock.instant()), storeId)
}
