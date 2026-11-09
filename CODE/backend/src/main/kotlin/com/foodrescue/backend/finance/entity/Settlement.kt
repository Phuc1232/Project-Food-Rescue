package com.foodrescue.backend.finance.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.finance.SettlementStatus
import com.foodrescue.backend.finance.entity.Settlement
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "settlement")
class Settlement(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:Column(name = "period_starts_at", nullable = false)
    var periodStartsAt: Instant,

    @field:Column(name = "period_ends_at", nullable = false)
    var periodEndsAt: Instant,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: SettlementStatus,

    @field:Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    var totalAmount: BigDecimal,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "confirmed_by_id", nullable = true)
    var confirmedBy: Account? = null,

    @field:Column(name = "confirmed_at", nullable = true)
    var confirmedAt: Instant? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "previous_settlement_id", nullable = true)
    var previousSettlement: Settlement? = null
) : MutableEntity()
