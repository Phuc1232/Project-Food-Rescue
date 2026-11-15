package com.foodrescue.backend.finance.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import org.hibernate.annotations.Immutable

@Entity
@Table(name = "financial_policy")
@Immutable
class FinancialPolicy(
    @field:Column(name = "policy_version", nullable = false)
    val policyVersion: Int,

    @field:Column(name = "effective_from", nullable = false)
    val effectiveFrom: Instant,

    @field:Column(name = "effective_until", nullable = true)
    val effectiveUntil: Instant? = null,

    @field:Column(name = "commission_rate", nullable = false, precision = 9, scale = 6)
    val commissionRate: BigDecimal,

    @field:Column(name = "no_show_retention_rate", nullable = false, precision = 9, scale = 6)
    val noShowRetentionRate: BigDecimal,

    @field:Column(name = "dispute_window_hours", nullable = false)
    val disputeWindowHours: Int,

    @field:Column(name = "cancellation_terms", nullable = false, columnDefinition = "text")
    val cancellationTerms: String,

    @field:Column(name = "refund_terms", nullable = false, columnDefinition = "text")
    val refundTerms: String,

    /** Null for the system seed (T4.5 V12). */
    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "created_by_id", nullable = true)
    val createdBy: Account?,

    // T4.5: why this version was created (UC-84..87 step 3).
    @field:Column(name = "reason", nullable = true, columnDefinition = "text")
    val reason: String? = null
) : BaseEntity()
