package com.foodrescue.backend.common.persistence

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    @field:Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}

@MappedSuperclass
abstract class MutableEntity : BaseEntity() {
    @field:Version
    var version: Long = 0
        protected set

    @field:Column(nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    @PreUpdate
    protected fun touch() { updatedAt = Instant.now() }
}
