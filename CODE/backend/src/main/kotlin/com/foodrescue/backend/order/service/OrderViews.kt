// AC-02,08 (T5.2): OrderViews responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.finance.service.MoneyRounding
import com.foodrescue.backend.order.dto.OrderView
import com.foodrescue.backend.order.dto.PolicySummary
import com.foodrescue.backend.order.entity.CustomerOrder
import org.springframework.stereotype.Component

/** Built only from the order snapshot, never from current store/batch/policy data (BR-33). */
@Component
class OrderViews {
    fun view(o: CustomerOrder) = OrderView(o.id!!, o.orderCode, o.status.name, o.store.id!!, o.store.name, o.saleBatch.id!!, o.quantity,
        o.unitPrice.setScale(0), o.totalAmount.setScale(0), o.minimumValue.setScale(0), o.currency, o.safetyWarningsSnapshot,
        o.pickupAddressSnapshot, o.pickupStartsAt, o.pickupEndsAt, o.holdExpiresAt, o.acceptedAt,
        PolicySummary(o.policy.policyVersion, o.noShowRetentionRate.stripTrailingZeros(), MoneyRounding.applyRate(o.totalAmount, o.noShowRetentionRate),
            o.disputeWindowHours, o.cancellationTermsSnapshot, o.refundTermsSnapshot))
}
