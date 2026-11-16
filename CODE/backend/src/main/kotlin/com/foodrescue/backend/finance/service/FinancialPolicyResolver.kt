// AC-01,03,07 (T4.5): FinancialPolicyResolver responsibility.
package com.foodrescue.backend.finance.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.finance.entity.FinancialPolicy
import com.foodrescue.backend.finance.repository.FinancialPolicyRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/** Policy in force at an instant = the version with the latest effectiveFrom <= t. T5.2 snapshots it into the order (BR-33). */
@Component
class FinancialPolicyResolver(private val policies: FinancialPolicyRepository, private val clock: Clock) {
    fun at(t: Instant): FinancialPolicy = policies.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(t)
        ?: throw ApiException(503, "SERVICE_UNAVAILABLE", "No financial policy in force")

    fun current(): FinancialPolicy = at(clock.instant())
}
