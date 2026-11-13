package com.foodrescue.backend.store.entity

import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import org.hibernate.annotations.JdbcType
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType
import java.time.LocalTime

@Entity
@Table(name = "store_opening_hour")
class StoreOpeningHour(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:Column(name = "day_of_week", nullable = false)
    var dayOfWeek: Int,

    @field:Column(name = "shift_number", nullable = false)
    var shiftNumber: Int,

    // T3.2: bind LocalTime as-is; hibernate.jdbc.time_zone=UTC would otherwise shift it by the JVM zone (1970 offset).
    @field:JdbcType(LocalTimeJdbcType::class)
    @field:Column(name = "opens_at", nullable = false)
    var opensAt: LocalTime,

    // T3.2: bind LocalTime as-is; hibernate.jdbc.time_zone=UTC would otherwise shift it by the JVM zone (1970 offset).
    @field:JdbcType(LocalTimeJdbcType::class)
    @field:Column(name = "closes_at", nullable = false)
    var closesAt: LocalTime,

    @field:Column(name = "closes_next_day", nullable = false)
    var closesNextDay: Boolean
) : MutableEntity()
