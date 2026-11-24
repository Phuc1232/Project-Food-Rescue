// AC-01..15 (T7.3): OrderCancellationViews responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.order.entity.OrderCancellationRequest
import com.foodrescue.backend.pickup.dto.PickupIncidentView
import com.foodrescue.backend.pickup.repository.PickupIncidentRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OrderCancellationViews(private val jdbc: JdbcTemplate, private val orders: OrderViews,
    private val money: OrderMoneyReader, private val incidents: PickupIncidentRepository) {
    fun view(r: OrderCancellationRequest) = OrderCancellationView(r.id!!, r.order.id!!, r.initiator.name, r.status.name,
        r.reason, r.storeFault, r.requestedAt, r.requestedBy.id!!, r.reviewer?.id, r.reviewedAt, r.reviewNote,
        r.approvedRefundAmount?.setScale(0), r.refundRequest?.id,
        jdbc.queryForList("select file_id from attachment where order_cancellation_request_id=? order by id", UUID::class.java, r.id).filterNotNull())
    fun summary(r: OrderCancellationRequest) = AdminCancellationSummary(r.id!!, r.order.id!!, r.order.orderCode,
        r.order.customer.displayName, r.status.name, r.initiator.name, r.reason, r.requestedAt)
    fun detail(r: OrderCancellationRequest) = AdminCancellationDetail(view(r), orders.view(r.order), money.money(r.order),
        incidents.findAllByOrderIdOrderByOccurredAt(r.order.id!!).map {
            PickupIncidentView(it.id!!, r.order.id!!, it.session?.id, it.actor.id!!, it.reason, it.customerArrived, it.occurredAt, it.resolution)
        })
}
