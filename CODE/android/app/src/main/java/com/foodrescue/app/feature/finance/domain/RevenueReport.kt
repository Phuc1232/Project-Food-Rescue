package com.foodrescue.app.feature.finance.domain

import com.foodrescue.app.feature.refund.domain.refundMovements

internal data class RevenueReport(val received: Long = 0, val earnedGross: Long = 0,
    val commission: Long = 0, val net: Long = 0, val completedOrders: Int = 0,
    val refunded: Long = 0, val refundPending: Long = 0)
internal fun revenueReport(snapshot: RevenueSnapshot, window: RevenueWindow): RevenueReport {
    val earned = snapshot.entries.filter { it.earnedAt?.let { time -> time in window } == true }
    val refunds = snapshot.entries.flatMap { it.order.refundMovements + it.queuedRefunds }
    return RevenueReport(
        received = revenueSum(snapshot.entries.filter { it.paidAt in window }.map { it.gross }),
        earnedGross = revenueSum(earned.map { it.gross - it.refunded }), commission = revenueSum(earned.map { it.commission }),
        net = revenueSum(earned.map { it.net }), completedOrders = earned.size,
        refunded = revenueSum(refunds.filter { it.completedAtMillis?.let { time -> time in window } == true }.map { it.amount }),
        refundPending = revenueSum(refunds.filter { it.completedAtMillis == null && it.requestedAtMillis in window }.map { it.amount }),
    )
}
