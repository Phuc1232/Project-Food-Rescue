package com.foodrescue.app.feature.finance.domain

/** Immutable terms captured when a customer accepts a reservation. */
internal data class FinancialTerms(
    val version: Long = 0,
    val commissionBps: Int = 2000,
    val cancellationMinutes: Int = 0,
    val cancellationRefundBps: Int = 10_000,
    val noShowGraceMinutes: Int = 0,
    val noShowRefundBps: Int = 0,
) {
    init {
        require(version >= 0 && commissionBps in 0..10_000)
        require(cancellationMinutes in 0..1440 && cancellationRefundBps in 0..10_000)
        require(noShowGraceMinutes in 0..120 && noShowRefundBps in 0..10_000)
    }
}

internal data class FinancialPolicy(val terms: FinancialTerms, val effectiveAt: Long,
    val publishedAt: Long, val actor: String, val reason: String) {
    init {
        require(terms.version > 0 && effectiveAt >= publishedAt && publishedAt >= 0)
        require(actor.isNotBlank() && validFinanceReason(reason))
    }
}

internal data class FinancialPolicyBook(val versions: List<FinancialPolicy> = emptyList()) {
    init {
        require(versions.map { it.terms.version } == (1L..versions.size.toLong()).toList())
        require(versions.zipWithNext().all { (a, b) -> a.effectiveAt < b.effectiveAt && a.publishedAt <= b.publishedAt })
    }
    val revision: Long get() = versions.size.toLong()
    fun termsAt(time: Long): FinancialTerms = versions.lastOrNull { it.effectiveAt <= time }?.terms ?: FinancialTerms()
    fun publish(expectedRevision: Long, terms: FinancialTerms, effectiveAt: Long,
        actor: String, reason: String, now: Long): FinancialPolicyBook {
        if (revision != expectedRevision) throw StaleFinanceData()
        require(effectiveAt >= now && versions.none { it.effectiveAt > now })
        require(effectiveAt > (versions.lastOrNull()?.effectiveAt ?: -1))
        require(terms.copy(version = 0) != termsAt(now).copy(version = 0))
        return copy(versions = versions + FinancialPolicy(terms.copy(version = revision + 1), effectiveAt, now, actor, reason.trim()))
    }
}

internal fun interface FinancialTermsSource { fun at(time: Long): FinancialTerms }
internal interface FinancialPolicyRepository {
    suspend fun load(): FinancialPolicyBook
    suspend fun publish(expectedRevision: Long, terms: FinancialTerms, effectiveAt: Long, reason: String): FinancialPolicyBook
}
internal fun validFinanceReason(value: String): Boolean = value.trim().length in 10..500 &&
    value.none { it.isISOControl() && it != '\n' }
internal class StaleFinanceData : IllegalStateException()
