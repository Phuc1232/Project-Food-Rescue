package com.foodrescue.backend.store.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.store.ApplicationStatus
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "partner_application")
class PartnerApplication(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "applicant_id", nullable = false)
    var applicant: Account,

    @field:Column(name = "revision", nullable = false)
    var revision: Int,

    @field:Column(name = "business_details", nullable = false, columnDefinition = "text")
    var businessDetails: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: ApplicationStatus,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "reviewer_id", nullable = true)
    var reviewer: Account? = null,

    @field:Column(name = "reviewed_at", nullable = true)
    var reviewedAt: Instant? = null,

    @field:Column(name = "review_reason", nullable = true, columnDefinition = "text")
    var reviewReason: String? = null,

    @field:Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant
) : MutableEntity()
