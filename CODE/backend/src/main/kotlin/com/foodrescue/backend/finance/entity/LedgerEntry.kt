package com.foodrescue.backend.finance.entity

import com.foodrescue.backend.common.persistence.BaseEntity
import com.foodrescue.backend.finance.LedgerEntryType
import com.foodrescue.backend.finance.entity.FinancialEvent
import com.foodrescue.backend.finance.entity.LedgerEntry
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import org.hibernate.annotations.Immutable

@Entity
@Table(name = "ledger_entry")
@Immutable
class LedgerEntry(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "event_id", nullable = false)
    val event: FinancialEvent,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    val store: Store,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "type", nullable = false, length = 50)
    val type: LedgerEntryType,

    @field:Column(name = "amount", nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @field:Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "original_entry_id", nullable = true)
    val originalEntry: LedgerEntry? = null,

    @field:Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant
) : BaseEntity()
