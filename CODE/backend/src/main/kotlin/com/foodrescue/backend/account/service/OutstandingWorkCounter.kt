// AC-02,06 (T3.5): OutstandingWorkCounter responsibility.
package com.foodrescue.backend.account.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** Unfinished orders that would lose their handler if the account is locked (business D10, UC-70 E2). */
@Component
class OutstandingWorkCounter(private val jdbc: JdbcTemplate) {
    fun customerOrders(accountId: UUID): Long =
        jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE customer_id = ? AND status IN $OPEN", Long::class.java, accountId)!!

    fun storeOrders(accountId: UUID): Long = jdbc.queryForObject("""SELECT count(*) FROM customer_order o JOIN store s ON s.id = o.store_id
        WHERE s.owner_id = ? AND s.status <> 'CLOSED' AND o.status IN $OPEN""", Long::class.java, accountId)!!

    private companion object { const val OPEN = "('PENDING_PAYMENT', 'CONFIRMED', 'HANDOVER_IN_PROGRESS', 'PENDING_NO_SHOW')" }
}
