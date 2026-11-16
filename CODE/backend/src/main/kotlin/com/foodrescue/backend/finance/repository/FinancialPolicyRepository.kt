// AC-01..05 (T4.5): FinancialPolicyRepository responsibility.
package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.FinancialPolicy
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/** Insert-only (trigger V3). */
interface FinancialPolicyRepository : JpaRepository<FinancialPolicy, UUID> {
    fun findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(at: Instant): FinancialPolicy?
    fun findFirstByOrderByPolicyVersionDesc(): FinancialPolicy?
    fun findFirstByOrderByEffectiveFromDesc(): FinancialPolicy?
    fun findAllByOrderByPolicyVersionDesc(): List<FinancialPolicy>
}
