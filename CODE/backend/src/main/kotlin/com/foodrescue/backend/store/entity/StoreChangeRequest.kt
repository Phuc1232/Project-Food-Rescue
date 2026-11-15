// AC-05,06,07: StoreChangeRequest responsibility.
package com.foodrescue.backend.store.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.store.StoreChangeRequestStatus
import jakarta.persistence.*
import java.time.Instant

/** A D04-bound store change (name, address, location) awaiting admin review; not applied until APPROVED (UC-41 A1). */
@Entity
@Table(name = "store_change_request")
class StoreChangeRequest(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "requested_by_id", nullable = false)
    var requestedBy: Account,

    /** JSON StoreChangeValues: store values at request time for the requested fields. */
    @field:Column(name = "current_values", nullable = false, columnDefinition = "text")
    var currentValues: String,

    /** JSON StoreChangeValues: only the requested fields are non-null. */
    @field:Column(name = "proposed_values", nullable = false, columnDefinition = "text")
    var proposedValues: String,

    /** JSON array of {type, fileId} (same shape as the partner application snapshot). */
    @field:Column(name = "documents", nullable = false, columnDefinition = "text")
    var documents: String,

    @field:Column(name = "note", nullable = true, columnDefinition = "text")
    var note: String?,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: StoreChangeRequestStatus,

    @field:Column(name = "requested_at", nullable = false)
    var requestedAt: Instant,

    /** The admin who decided, or the owner who cancelled. */
    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "resolved_by_id", nullable = true)
    var resolvedBy: Account? = null,

    @field:Column(name = "resolved_at", nullable = true)
    var resolvedAt: Instant? = null,

    @field:Column(name = "resolution_reason", nullable = true, columnDefinition = "text")
    var resolutionReason: String? = null
) : MutableEntity()
