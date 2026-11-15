package com.foodrescue.app.feature.finance.domain

internal data class RevenueReport(val received: Long = 0, val earnedGross: Long = 0,
    val commission: Long = 0, val net: Long = 0, val completedOrders: Int = 0,
    val refunded: Long = 0, val refundPending: Long = 0)
internal fun revenueReport(snapshot: RevenueSnapshot, window: RevenueWindow): RevenueReport {
    val earned = snapshot.entries.filter { it.earnedAt?.let { time -> time in window } == true }
    return RevenueReport(
        received = revenueSum(snapshot.entries.filter { it.paidAt in window }.map { it.gross }),
        earnedGross = revenueSum(earned.map { it.gross }), commission = revenueSum(earned.map { it.commission }),
        net = revenueSum(earned.map { it.net }), completedOrders = earned.size,
        refunded = revenueSum(snapshot.entries.filter { it.order.refund?.updatedAtMillis?.let { time -> time in window } == true }.map { it.refunded }),
        refundPending = revenueSum(snapshot.entries.filter { it.order.refund?.requestedAtMillis?.let { time -> time in window } == true }.map { it.refundPending }),
    )
}
