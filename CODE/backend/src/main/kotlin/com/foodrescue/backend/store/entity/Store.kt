package com.foodrescue.backend.store.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.store.StoreStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

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
    var status: StoreStatus,

    // T3.3 Q6: public description, editable by the owner without review.
    @field:Column(name = "description", nullable = true, columnDefinition = "text")
    var description: String? = null,

    // T3.3 Q7: reason for the current admin-set status (suspension); history lives in audit_log.
    @field:Column(name = "status_reason", nullable = true, columnDefinition = "text")
    var statusReason: String? = null,

    @field:Column(name = "status_changed_at", nullable = true)
    var statusChangedAt: Instant? = null
) : MutableEntity()
