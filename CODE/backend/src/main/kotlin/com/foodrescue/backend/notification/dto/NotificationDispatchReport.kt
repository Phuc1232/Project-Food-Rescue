// AC-01..08 (T8.2): NotificationDispatchReport responsibility.
package com.foodrescue.backend.notification.dto
data class NotificationDispatchReport(val claimed: Int,val delivered: Int,val sent: Int,val failed: Int)
