// AC-01..14 (T7.4): NoShowService responsibility.
package com.foodrescue.backend.pickup.service
import com.foodrescue.backend.notification.service.NotificationOutboxService
import com.foodrescue.backend.notification.NotificationType as NType

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.finance.NoShowAmounts
import com.foodrescue.backend.finance.FinancialEventType
import com.foodrescue.backend.finance.entity.FinancialEvent
import com.foodrescue.backend.finance.repository.FinancialEventRepository
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.file.repository.AttachmentRepository
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.repository.*
import com.foodrescue.backend.order.service.OrderMoneyReader
import com.foodrescue.backend.payment.PaymentStatus
import com.foodrescue.backend.payment.RefundRequestStatus
import com.foodrescue.backend.payment.entity.RefundRequest
import com.foodrescue.backend.payment.repository.*
import com.foodrescue.backend.payment.service.RefundViews
import com.foodrescue.backend.pickup.CompensationStatus
import com.foodrescue.backend.pickup.PickupStatus
import com.foodrescue.backend.pickup.dto.*
import com.foodrescue.backend.pickup.entity.NoShowCase
import com.foodrescue.backend.pickup.repository.*
import com.foodrescue.backend.store.repository.StoreRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.jdbc.core.namedparam.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.sql.Timestamp
import java.util.UUID

data class NoShowConfirmResult(val view: NoShowCaseDetailView, val created: Boolean)

@Service
class NoShowService(private val outbox: NotificationOutboxService,
    private val ledgerPosting: com.foodrescue.backend.finance.service.LedgerPostingService, private val authorization: StoreAuthorizationService, private val orders: CustomerOrderRepository,
    private val cases: NoShowCaseRepository, private val sessions: PickupSessionRepository, private val incidents: PickupIncidentRepository,
    private val allocations: BoxAllocationRepository, private val boxes: PhysicalBoxRepository, private val stores: StoreRepository,
    private val accounts: AccountRepository, private val payments: PaymentRepository, private val refunds: RefundRequestRepository,
    private val refundViews: RefundViews, private val money: OrderMoneyReader,
    private val events: FinancialEventRepository, private val attachments: AttachmentRepository, private val history: StoreHistoryRecorder,
    private val mapper: ObjectMapper, private val em: EntityManager, private val jdbc: NamedParameterJdbcTemplate, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun overdueOrders(actorId: UUID, storeId: UUID, date: LocalDate?, status: OrderStatus?): List<OverdueOrderView> {
        authorization.requirePermission(actorId, storeId, "NO_SHOW_CONFIRM")
        val store = stores.findById(storeId).orElse(null) ?: missing()
        val allowed = listOf(OrderStatus.CONFIRMED, OrderStatus.HANDOVER_IN_PROGRESS, OrderStatus.PENDING_NO_SHOW, OrderStatus.NO_SHOW)
        if (status != null && status !in allowed) invalid("Invalid overdue order status")
        val params = MapSqlParameterSource("store", storeId).addValue("now", Timestamp.from(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)))
            .addValue("statuses", (status?.let { listOf(it) } ?: allowed).map { it.name })
        val dateFilter = if (date == null) "" else {
            val zone = ZoneId.of(store.timezone)
            params.addValue("start", Timestamp.from(date.atStartOfDay(zone).toInstant()))
                .addValue("end", Timestamp.from(date.plusDays(1).atStartOfDay(zone).toInstant()))
            " AND o.pickup_ends_at>=:start AND o.pickup_ends_at<:end"
        }
        return jdbc.queryForList("SELECT o.id FROM customer_order o WHERE o.store_id=:store AND o.status IN (:statuses) AND o.pickup_ends_at<=:now$dateFilter ORDER BY o.pickup_ends_at,o.id", params, UUID::class.java).filterNotNull().map { id ->
            val o = orders.findById(id).orElseThrow()
            val m = money.money(o); val amounts = NoShowAmounts.calculate(o.totalAmount, m.verifiedPaid, o.noShowRetentionRate)
            val active = sessions.findByOrderIdAndStatusIn(id, listOf(PickupStatus.OPEN, PickupStatus.RECONCILING))
            val incidentViews = incidentViews(id)
            OverdueOrderView(id, o.orderCode, o.status.name, o.customer.displayName, o.quantity, o.pickupStartsAt, o.pickupEndsAt,
                boxViews(id), m, NoShowAmountsView(amounts.retainedAmount, amounts.refundAmount, amounts.storeCompensationAmount),
                active?.id, active?.status?.name, incidentViews, incidentViews.any { it.customerArrived }, cases.findByOrderId(id)?.let(::summary))
        }
    }
    @Transactional(readOnly = true)
    fun detail(actorId: UUID, storeId: UUID, caseId: UUID): NoShowCaseDetailView {
        authorization.requirePermission(actorId, storeId, "NO_SHOW_CONFIRM")
        return view(cases.findById(caseId).orElse(null)?.takeIf { it.order.store.id == storeId } ?: missing())
    }
    @Transactional
    fun confirm(actorId: UUID, storeId: UUID, orderId: UUID, r: NoShowConfirmRequest, key: String?): NoShowConfirmResult {
        authorization.requirePermission(actorId, storeId, "NO_SHOW_CONFIRM")
        if (key != null && !key.matches(Regex("^[A-Za-z0-9_-]{1,255}$"))) invalid("Invalid Idempotency-Key")
        val fingerprint = mapper.writeValueAsString(listOf("NO_SHOW_CONFIRM", r.storeOpenedOnTime, r.boxesReadyAndCompliant,
            r.evidence!!.trim(), r.attachmentIds.distinct().sorted()))
        val order = orders.findForUpdate(orderId)?.takeIf { it.store.id == storeId } ?: missing()
        cases.findByOrderId(orderId)?.let {
            if (key != null && key == it.idempotencyKey) {
                if (fingerprint != it.requestFingerprint) conflict("IDEMPOTENCY_KEY_REUSED", "Key was used with a different payload")
                return NoShowConfirmResult(view(it), false)
            }
            conflict("NO_SHOW_ALREADY_CONFIRMED", "No-show has already been confirmed")
        }
        val now = clock.instant()
        if (order.status == OrderStatus.COMPLETED) conflict("ORDER_ALREADY_COMPLETED", "Use the post-delivery complaint process")
        if (order.status in listOf(OrderStatus.CANCELLED, OrderStatus.EXPIRED)) conflict("INVALID_ORDER_STATUS", "Order has ended")
        if (now.isBefore(order.pickupEndsAt)) conflict("PICKUP_WINDOW_NOT_ENDED", "Pickup window has not ended")
        if (sessions.findByOrderIdAndStatusIn(orderId, listOf(PickupStatus.OPEN, PickupStatus.RECONCILING)) != null)
            conflict("HANDOVER_IN_PROGRESS", "An active handover prevents no-show confirmation")
        if (incidents.findAllByOrderIdOrderByOccurredAt(orderId).any { it.customerArrived })
            conflict("CUSTOMER_ARRIVED_INCIDENT", "Customer arrived at pickup; resolve via the pickup incident process")
        if (r.storeOpenedOnTime != true || r.boxesReadyAndCompliant != true)
            conflict("NO_SHOW_PRECONDITION_FAILED", "Store must have opened on time with compliant boxes ready")
        if (order.status !in listOf(OrderStatus.CONFIRMED, OrderStatus.PENDING_NO_SHOW, OrderStatus.HANDOVER_IN_PROGRESS))
            conflict("INVALID_ORDER_STATUS", "Order is not eligible for no-show")
        val m = money.money(order)
        val amounts = NoShowAmounts.calculate(order.totalAmount, m.verifiedPaid, order.noShowRetentionRate)
        val sources = if (amounts.refundAmount.signum() == 0) emptyList() else {
            if (amounts.refundAmount > m.verifiedPaid - m.refunded - m.refundInProgress)
                conflict("REFUND_AMOUNT_CONFLICT", "Refund exceeds remaining verified funds")
            payments.findAllByOrderIdOrderByReceivedAt(orderId).filter { it.status == PaymentStatus.VERIFIED }.sortedBy { it.id }
                .map { payments.findForUpdate(it.id!!)!! }.filterNot { refunds.existsByPaymentId(it.id!!) }.also { available ->
                    if (available.fold(BigDecimal.ZERO) { sum, p -> sum + p.receivedAmount } < amounts.refundAmount)
                        conflict("REFUND_ALREADY_REQUESTED", "Original payment already has a refund obligation")
                }
        }
        val files = r.attachmentIds.distinct().sorted().map { id ->
            val file = em.find(StoredFile::class.java, id, LockModeType.PESSIMISTIC_WRITE)?.takeIf { it.uploadedBy.id == actorId }
                ?: invalid("Evidence file not found or not owned by you")
            if (attachments.findAllByFileId(id).isNotEmpty()) invalid("Evidence file is already attached")
            file
        }
        allocations.findAllByOrderIdOrderByReservedAt(orderId).filter { it.status in listOf(AllocationStatus.HELD, AllocationStatus.CONFIRMED) }
            .sortedBy { it.box.id }.forEach { a ->
                em.lock(a, LockModeType.PESSIMISTIC_WRITE)
                val box = boxes.findForUpdate(a.box.id!!) ?: missing()
                a.status = AllocationStatus.RELEASED; a.releasedAt = now; a.releaseReason = "NO_SHOW_CONFIRMED"
                if (box.status != BoxStatus.BLOCKED) box.status = BoxStatus.AWAITING_INSPECTION
            }
        order.status = OrderStatus.NO_SHOW; order.statusReason = "NO_SHOW_CONFIRMED: ${r.evidence.trim()}"; order.statusChangedAt = now
        order.pickupCodeHash = null; order.pickupCodeEncrypted = null; order.pickupCodeKeyId = null
        val notification = outbox.enqueue(order.customer,NType.NO_SHOW,"NO_SHOW_CONFIRMED","No-show confirmed",
            mapOf("orderId" to orderId,"retainedAmount" to amounts.retainedAmount,"refundAmount" to amounts.refundAmount),order,"NO_SHOW_CONFIRMED:$orderId")
        val actor = accounts.getReferenceById(actorId)
        val case = cases.save(NoShowCase(order = order, verifiedBy = actor, verifiedAt = now, evidence = r.evidence.trim(),
            notification = notification, retainedAmount = amounts.retainedAmount, compensationStatus = CompensationStatus.HELD,
            idempotencyKey = key, requestFingerprint = fingerprint, refundAmount = amounts.refundAmount))
        files.forEach { attachments.save(Attachment(it, noShowCase = case)) }
        var remaining = amounts.refundAmount
        sources.forEach { payment ->
            val part = remaining.min(payment.receivedAmount)
            if (part.signum() > 0) {
                refunds.save(RefundRequest(order = order, reason = "No-show refund", requestedAmount = part.setScale(0),
                    status = RefundRequestStatus.PENDING, payment = payment, reasonCode = "NO_SHOW_REFUND"))
                remaining -= part
            }
        }
        val noShowEvent = events.saveAndFlush(FinancialEvent(order, FinancialEventType.NO_SHOW_CONFIRMED, noShowCase = case, occurredAt = now, businessKey = "NO_SHOW_CONFIRMED:$orderId"))
        ledgerPosting.postNoShowConfirmed(order, case, noShowEvent)
        history.record(actor, order.store, "CUSTOMER_ORDER", orderId, "NO_SHOW_CONFIRMED", null,
            mapOf("caseId" to case.id, "retainedAmount" to amounts.retainedAmount, "refundAmount" to amounts.refundAmount))
        cases.flush()
        return NoShowConfirmResult(view(case), true)
    }
    private fun summary(n: NoShowCase) = NoShowCaseSummaryView(n.id!!, n.order.id!!, n.verifiedBy.id!!, n.verifiedAt, n.evidence,
        n.compensationStatus.name, n.notificationReceivedAt, n.disputeDeadline)
    private fun view(n: NoShowCase) = NoShowCaseDetailView(n.id!!, n.order.id!!, n.order.orderCode, n.verifiedBy.id!!, n.verifiedAt, n.evidence,
        n.compensationStatus.name, n.notificationReceivedAt, n.disputeDeadline,
        NoShowAmountsView(n.retainedAmount.setScale(0), n.refundAmount.setScale(0), n.retainedAmount.setScale(0)), n.notification?.id, n.notification?.status?.name,
        jdbc.queryForList("SELECT file_id FROM attachment WHERE no_show_case_id=:id ORDER BY id", mapOf("id" to n.id), UUID::class.java).filterNotNull(),
        refunds.findAllByOrderIdOrderByCreatedAt(n.order.id!!).map { refundViews.view(it, false) }, boxViews(n.order.id!!), incidentViews(n.order.id!!))
    private fun boxViews(orderId: UUID) = allocations.findAllByOrderIdOrderByReservedAt(orderId).map {
        AllocatedBoxView(it.box.id!!, it.box.boxCode, it.box.totalListedValue.setScale(0), it.box.status.name, it.status.name, it.reservedAt, it.releasedAt, it.releaseReason)
    }
    private fun incidentViews(orderId: UUID) = incidents.findAllByOrderIdOrderByOccurredAt(orderId).map {
        PickupIncidentView(it.id!!, orderId, it.session?.id, it.actor.id!!, it.reason, it.customerArrived, it.occurredAt, it.resolution)
    }
    private fun missing(): Nothing = throw ApiException(404, "NOT_FOUND", "Order or no-show case not found")
    private fun invalid(message: String): Nothing = throw ApiException(400, "VALIDATION_FAILED", message)
    private fun conflict(code: String, message: String): Nothing = throw ApiException(409, code, message)
}
