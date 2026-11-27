// AC-01..12 (T8.4): SettlementErrors responsibility.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.common.exception.ApiException
internal fun settlementMissing(): Nothing = throw ApiException(404,"NOT_FOUND","Settlement, payout account or payout not found")
internal fun settlementConflict(message: String): Nothing = throw ApiException(409,"CONFLICT",message)
internal fun settlementInvalid(message: String): Nothing = throw ApiException(400,"VALIDATION_FAILED",message)
