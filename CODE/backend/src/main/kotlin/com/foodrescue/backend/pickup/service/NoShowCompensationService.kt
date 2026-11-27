// AC-01..14 (T7.4): NoShowCompensationService responsibility.
package com.foodrescue.backend.pickup.service
import com.foodrescue.backend.notification.service.NotificationOutboxService
import com.foodrescue.backend.notification.NotificationType as NType
import com.foodrescue.backend.pickup.CompensationStatus
import com.foodrescue.backend.pickup.entity.NoShowCase
import com.foodrescue.backend.pickup.repository.NoShowCaseRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import org.springframework.jdbc.core.namedparam.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class NoShowCompensationService(private val outbox: NotificationOutboxService, private val cases: NoShowCaseRepository, private val jdbc: NamedParameterJdbcTemplate,
    private val history: StoreHistoryRecorder) {
    @Transactional
    fun recordNotificationReceived(notificationId: UUID, receivedAt: Instant): NoShowCase? {
        val id = cases.findIdByNotificationId(notificationId) ?: return null
        val case = cases.findForUpdate(id) ?: return null
        if (case.notificationReceivedAt == null) {
            require(case.order.disputeWindowHours > 0)
            case.notificationReceivedAt = receivedAt
            case.disputeDeadline = receivedAt.plus(case.order.disputeWindowHours.toLong(), ChronoUnit.HOURS)
            history.record(null, case.order.store, "CUSTOMER_ORDER", case.order.id!!, "NO_SHOW_NOTIFICATION_RECEIVED", null,
                mapOf("caseId" to id, "receivedAt" to receivedAt, "disputeDeadline" to case.disputeDeadline))
        }
        return case
    }
    @Transactional
    fun releaseEligible(now: Instant, batchSize: Int = 100): Int {
        require(batchSize in 1..1000)
        val ids = jdbc.queryForList("""SELECT n.id FROM no_show_case n WHERE n.compensation_status='HELD'
            AND n.notification_received_at IS NOT NULL AND n.dispute_deadline IS NOT NULL AND n.dispute_deadline<=:now
            AND NOT EXISTS (SELECT 1 FROM complaint c WHERE c.order_id=n.order_id AND c.status IN ('OPEN','INVESTIGATING','AWAITING_EVIDENCE','DECIDED'))
            ORDER BY n.dispute_deadline,n.id LIMIT :n FOR UPDATE SKIP LOCKED""",
            MapSqlParameterSource("now", Timestamp.from(now.truncatedTo(java.time.temporal.ChronoUnit.MICROS))).addValue("n", batchSize), UUID::class.java).filterNotNull()
        ids.forEach { id ->
            val case = cases.findById(id).orElseThrow(); case.compensationStatus = CompensationStatus.ELIGIBLE
            outbox.orderEvent(case.order,NType.NO_SHOW,"NO_SHOW_COMPENSATION_ELIGIBLE",key="NO_SHOW_ELIGIBLE:${case.id}",customer=false,store=true,payload=mapOf("caseId" to case.id,"compensationStatus" to case.compensationStatus.name))
            history.record(null, case.order.store, "CUSTOMER_ORDER", case.order.id!!, "NO_SHOW_COMPENSATION_ELIGIBLE", null,
                mapOf("caseId" to id, "compensationStatus" to "ELIGIBLE"))
        }
        return ids.size
    }
}
