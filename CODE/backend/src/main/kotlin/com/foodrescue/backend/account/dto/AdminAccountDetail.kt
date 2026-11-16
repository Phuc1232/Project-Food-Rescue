// AC-02..06 (T3.5): AdminAccountDetail responsibility.
package com.foodrescue.backend.account.dto

import java.time.Instant

/** UC-69: profile, roles, store links, status with reason, unfinished orders, newest 50 history entries. No credentials. */
data class AdminAccountDetail(
    val account: AdminAccountSummary, val statusReason: String?, val statusChangedAt: Instant?, val stores: List<AccountStoreLink>,
    val outstandingCustomerOrders: Long, val outstandingStoreOrders: Long, val history: List<AccountHistoryItem>
)
