package com.foodrescue.backend.finance.entity

import com.foodrescue.backend.common.persistence.BaseEntity
import com.foodrescue.backend.finance.entity.LedgerEntry
import com.foodrescue.backend.finance.entity.Settlement
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.math.BigDecimal
import org.hibernate.annotations.Immutable

@Entity
@Table(name = "settlement_line")
@Immutable
class SettlementLine(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    val store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "settlement_id", nullable = false)
    val settlement: Settlement,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "ledger_entry_id", nullable = false)
    val ledgerEntry: LedgerEntry,

    @field:Column(name = "amount", nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal
) : BaseEntity()
