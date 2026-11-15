// AC-08,09 (T4.1): PriceReferenceDocument responsibility.
package com.foodrescue.backend.catalog.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

/** A listed-price reference (UC-53). menuItem = null covers the whole menu. A replaced document keeps replacedBy (A1). */
@Entity
@Table(name = "price_reference_document")
class PriceReferenceDocument(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "menu_item_id", nullable = true)
    var menuItem: MenuItem?,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "file_id", nullable = false)
    var file: StoredFile,

    @field:Column(name = "description", nullable = false, columnDefinition = "text")
    var description: String,

    @field:Column(name = "applies_from", nullable = false)
    var appliesFrom: LocalDate,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "uploaded_by_id", nullable = false)
    var uploadedBy: Account,

    @field:Column(name = "uploaded_at", nullable = false)
    var uploadedAt: Instant,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "replaced_by_id", nullable = true)
    var replacedBy: PriceReferenceDocument? = null
) : MutableEntity()
