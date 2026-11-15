// AC-03,07 (T4.3): BatchAvailability responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.surprisebox.dto.BoxCountsView
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

/**
 * The single definition of sellable stock (BR-14, BR-15, BR-18): boxes of the batch that are AVAILABLE, worth at least the batch
 * minimum value, safe until the end of the pickup window (and still safe now), and not held by an active allocation.
 * T5.1 (search) and T5.2 (holding) must use this definition.
 */
@Component
class BatchAvailability(private val jdbc: JdbcTemplate, private val clock: Clock) {
    fun counts(batchId: UUID): BoxCountsView {
        val now = Timestamp.from(clock.instant())
        return jdbc.queryForObject("""
            SELECT count(*) AS packed,
              count(*) FILTER (WHERE p.status IN ('AVAILABLE','RESERVED','ALLOCATED') AND $MEETS) AS eligible,
              count(*) FILTER (WHERE p.status = 'RESERVED') AS held,
              count(*) FILTER (WHERE p.status = 'ALLOCATED') AS allocated,
              count(*) FILTER (WHERE p.status = 'DELIVERED') AS delivered,
              count(*) FILTER (WHERE p.status = 'BLOCKED') AS blocked,
              count(*) FILTER (WHERE p.status = 'AWAITING_INSPECTION') AS awaiting,
              count(*) FILTER (WHERE p.status = 'AVAILABLE' AND NOT ($MEETS)) AS ineligible,
              count(*) FILTER (WHERE p.status = 'AVAILABLE' AND $MEETS AND $FREE) AS available
            FROM physical_box p JOIN sale_batch b ON b.id = p.current_batch_id WHERE p.current_batch_id = ?""", { rs, _ ->
            BoxCountsView(rs.getLong("packed"), rs.getLong("eligible"), rs.getLong("held"), rs.getLong("allocated"), rs.getLong("delivered"),
                rs.getLong("blocked"), rs.getLong("awaiting"), rs.getLong("ineligible"), rs.getLong("available"), now.toInstant())
        }, now, now, now, batchId)!!
    }

    fun available(batchId: UUID): Long = counts(batchId).available

    private companion object {
        const val MEETS = "p.total_listed_value >= b.minimum_value AND p.safe_until >= b.pickup_ends_at AND p.safe_until > ?"
        const val FREE = "NOT EXISTS (SELECT 1 FROM box_allocation a WHERE a.box_id = p.id AND a.status IN ('HELD','CONFIRMED'))"
    }
}
