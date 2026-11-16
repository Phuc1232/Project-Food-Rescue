// AC-03,07 (T4.3), AC-01 (T5.1): BatchAvailability responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.surprisebox.dto.BoxCountsView
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

/**
 * The single definition of sellable stock (BR-14, BR-15, BR-18): boxes of the batch that are AVAILABLE, worth at least the batch
 * minimum value, safe until the end of the pickup window (and still safe now), and not held by an active allocation.
 * T5.1 (search) and T5.2 (holding) reuse [MEETS_TERMS] / [NOT_HELD] / [availableCountSql].
 */
@Component
class BatchAvailability(private val jdbc: NamedParameterJdbcTemplate, private val clock: Clock) {
    fun counts(batchId: UUID): BoxCountsView {
        val now = Timestamp.from(clock.instant())
        return jdbc.queryForObject("""
            SELECT count(*) AS packed,
              count(*) FILTER (WHERE p.status IN ('AVAILABLE','RESERVED','ALLOCATED') AND $MEETS_TERMS) AS eligible,
              count(*) FILTER (WHERE p.status = 'RESERVED') AS held,
              count(*) FILTER (WHERE p.status = 'ALLOCATED') AS allocated,
              count(*) FILTER (WHERE p.status = 'DELIVERED') AS delivered,
              count(*) FILTER (WHERE p.status = 'BLOCKED') AS blocked,
              count(*) FILTER (WHERE p.status = 'AWAITING_INSPECTION') AS awaiting,
              count(*) FILTER (WHERE p.status = 'AVAILABLE' AND NOT ($MEETS_TERMS)) AS ineligible,
              count(*) FILTER (WHERE p.status = 'AVAILABLE' AND $MEETS_TERMS AND $NOT_HELD) AS available
            FROM physical_box p JOIN sale_batch b ON b.id = p.current_batch_id WHERE p.current_batch_id = :batchId""",
            MapSqlParameterSource("now", now).addValue("batchId", batchId)) { rs, _ ->
            BoxCountsView(rs.getLong("packed"), rs.getLong("eligible"), rs.getLong("held"), rs.getLong("allocated"), rs.getLong("delivered"),
                rs.getLong("blocked"), rs.getLong("awaiting"), rs.getLong("ineligible"), rs.getLong("available"), now.toInstant())
        }!!
    }

    fun available(batchId: UUID): Long = counts(batchId).available

    companion object {
        /** Box `p` of batch `b` meets the batch terms at `:now`. */
        const val MEETS_TERMS = "(p.total_listed_value >= b.minimum_value AND p.safe_until >= b.pickup_ends_at AND p.safe_until > :now)"
        const val NOT_HELD = "NOT EXISTS (SELECT 1 FROM box_allocation a WHERE a.box_id = p.id AND a.status IN ('HELD','CONFIRMED'))"

        /** Scalar subquery: sellable boxes of batch alias `b` at `:now`. */
        const val AVAILABLE_COUNT_SQL =
            "(SELECT count(*) FROM physical_box p WHERE p.current_batch_id = b.id AND p.status = 'AVAILABLE' AND $MEETS_TERMS AND $NOT_HELD)"
    }
}
