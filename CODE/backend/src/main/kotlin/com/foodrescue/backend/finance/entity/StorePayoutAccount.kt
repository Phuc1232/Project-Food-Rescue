package com.foodrescue.backend.finance.entity

import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "store_payout_account")
class StorePayoutAccount(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:Column(name = "bank_code", nullable = false, length = 255)
    var bankCode: String,

    @field:Column(name = "account_number_ciphertext", nullable = false, columnDefinition = "text")
    var accountNumberCiphertext: String,

    @field:Column(name = "account_holder_name", nullable = false, length = 255)
    var accountHolderName: String,

    @field:Column(name = "account_version", nullable = false)
    var accountVersion: Int,

    @field:Column(name = "verified_at", nullable = true)
    var verifiedAt: Instant? = null,

    @field:Column(name = "effective_from", nullable = false)
    var effectiveFrom: Instant,

    @field:Column(name = "effective_until", nullable = true)
    var effectiveUntil: Instant? = null
) : MutableEntity()
