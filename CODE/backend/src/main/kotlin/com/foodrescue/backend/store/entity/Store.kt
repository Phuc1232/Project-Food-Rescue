package com.foodrescue.backend.store.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.store.StoreStatus
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "store")
class Store(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "owner_id", nullable = false)
    var owner: Account,

    @field:Column(name = "name", nullable = false, length = 255)
    var name: String,

    @field:Column(name = "address", nullable = false, columnDefinition = "text")
    var address: String,

    @field:Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    var latitude: BigDecimal,

    @field:Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    var longitude: BigDecimal,

    @field:Column(name = "timezone", nullable = false, length = 255)
    var timezone: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: StoreStatus
) : MutableEntity()
