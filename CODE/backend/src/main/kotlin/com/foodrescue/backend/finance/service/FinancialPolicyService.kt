// AC-01..06 (T4.5): FinancialPolicyService responsibility.
package com.foodrescue.backend.finance.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.audit.AuditService
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.finance.dto.FinancialPolicyRequest
import com.foodrescue.backend.finance.dto.FinancialPolicyView
import com.foodrescue.backend.finance.entity.FinancialPolicy
import com.foodrescue.backend.finance.repository.FinancialPolicyRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/** UC-84..87. Versions are insert-only; a new version never changes orders created under an older one (BR-33). */
@Service
class FinancialPolicyService(
    private val policies: FinancialPolicyRepository,
    private val resolver: FinancialPolicyResolver,
    private val accounts: AccountRepository,
    private val audit: AuditService,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun current(): FinancialPolicyView = view(resolver.current())

    @Transactional(readOnly = true)
    fun all(): List<FinancialPolicyView> = policies.findAllByOrderByPolicyVersionDesc().map(::view)

    /** The advisory lock serializes creators; basedOnVersion detects a version created meanwhile (UC-84 E2). */
    @Transactional
    fun create(adminId: UUID, r: FinancialPolicyRequest): FinancialPolicyView {
        val errors = listOfNotNull(rate(r.commissionRate!!, "commissionRate"), rate(r.noShowRetentionRate!!, "noShowRetentionRate"),
            FieldViolation("disputeWindowHours", "Must be 48 (BR-43)").takeIf { r.disputeWindowHours != DISPUTE_HOURS })
        if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext('financial_policy'))") { _, _ -> 1 }
        val latest = policies.findFirstByOrderByPolicyVersionDesc()
        if (r.basedOnVersion != (latest?.policyVersion ?: 0))
            throw ApiException(409, "CONFLICT", "A newer policy version exists; reload and review it first")
        val now = clock.instant()
        val effectiveFrom = r.effectiveFrom ?: now
        if (effectiveFrom.isBefore(now)) throw ApiException(409, "CONFLICT", "effectiveFrom must not be in the past")
        val lastStart = policies.findFirstByOrderByEffectiveFromDesc()?.effectiveFrom
        if (lastStart != null && !effectiveFrom.isAfter(lastStart))
            throw ApiException(409, "CONFLICT", "effectiveFrom must be after the start of the latest version ($lastStart)")
        val admin = accounts.findById(adminId).orElse(null) ?: authenticationFailed()
        val p = policies.saveAndFlush(FinancialPolicy((latest?.policyVersion ?: 0) + 1, effectiveFrom, null, r.commissionRate.setScale(6),
            r.noShowRetentionRate.setScale(6), DISPUTE_HOURS, r.cancellationTerms!!.trim(), r.refundTerms!!.trim(), admin, r.reason!!.trim()))
        audit.record(admin, ENTITY, p.id!!, "FINANCIAL_POLICY_CREATED", afterState = mapper.writeValueAsString(mapOf("version" to p.policyVersion,
            "commissionRate" to p.commissionRate, "noShowRetentionRate" to p.noShowRetentionRate, "effectiveFrom" to p.effectiveFrom.toString(),
            "reason" to p.reason)))
        return view(p)
    }

    private fun rate(v: BigDecimal, field: String): FieldViolation? = when {
        v < BigDecimal.ZERO || v > BigDecimal.ONE -> FieldViolation(field, "Must be between 0 and 1")
        v.stripTrailingZeros().scale() > 4 -> FieldViolation(field, "At most 4 decimal places")
        else -> null
    }

    fun view(p: FinancialPolicy): FinancialPolicyView {
        val now = clock.instant(); val active = resolver.at(now)
        val state = when {
            p.id == active.id -> "ACTIVE"
            p.effectiveFrom.isAfter(now) -> "SCHEDULED"
            else -> "SUPERSEDED"
        }
        return FinancialPolicyView(p.id!!, p.policyVersion, state, p.effectiveFrom, p.commissionRate.stripTrailingZeros(),
            p.noShowRetentionRate.stripTrailingZeros(), p.disputeWindowHours, p.cancellationTerms, p.refundTerms, MoneyRounding.RULE, p.reason,
            p.createdBy?.id, p.createdAt)
    }

    companion object {
        const val ENTITY = "FINANCIAL_POLICY"
        const val DISPUTE_HOURS = 48
    }
}
