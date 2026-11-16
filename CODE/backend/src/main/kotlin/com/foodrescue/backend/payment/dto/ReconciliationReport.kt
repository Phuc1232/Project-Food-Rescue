// AC-03 (T6.4): ReconciliationReport responsibility.
package com.foodrescue.backend.payment.dto

/** What one reconciliation run changed. */
data class ReconciliationReport(val checked: Int, val expiredRequests: Int, val ordersReleased: Int, val missingWebhook: Int, val providerErrors: Int)
