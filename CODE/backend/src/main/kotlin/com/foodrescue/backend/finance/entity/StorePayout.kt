package com.foodrescue.backend.finance.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.finance.PayoutStatus
import com.foodrescue.backend.finance.entity.Settlement
import com.foodrescue.backend.finance.entity.StorePayoutAccount
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "store_payout")
class StorePayout(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "settlement_id", nullable = false)
    var settlement: Settlement,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "payout_account_id", nullable = false)
    var payoutAccount: StorePayoutAccount,

    @field:Column(name = "recipient_snapshot", nullable = false, columnDefinition = "text")
    var recipientSnapshot: String,

    @field:Column(name = "amount", nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @field:Column(name = "transfer_reference", nullable = false, length = 255)
    var transferReference: String,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "confirmed_by_id", nullable = true)
    var confirmedBy: Account? = null,

    @field:Column(name = "confirmed_at", nullable = true)
    var confirmedAt: Instant? = null,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: PayoutStatus
) : MutableEntity()
