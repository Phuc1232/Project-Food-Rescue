package com.foodrescue.backend.file.entity

import com.foodrescue.backend.catalog.entity.MenuItem
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.complaint.entity.Complaint
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.finance.entity.StorePayout
import com.foodrescue.backend.payment.entity.Payment
import com.foodrescue.backend.payment.entity.Refund
import com.foodrescue.backend.pickup.entity.NoShowCase
import com.foodrescue.backend.store.entity.PartnerApplication
import com.foodrescue.backend.surprisebox.entity.PhysicalBox
import jakarta.persistence.*

@Entity
@Table(name = "attachment")
class Attachment(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "file_id", nullable = false)
    var file: StoredFile,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "partner_application_id", nullable = true)
    var partnerApplication: PartnerApplication? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "menu_item_id", nullable = true)
    var menuItem: MenuItem? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "box_id", nullable = true)
    var box: PhysicalBox? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "complaint_id", nullable = true)
    var complaint: Complaint? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "no_show_case_id", nullable = true)
    var noShowCase: NoShowCase? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "payment_id", nullable = true)
    var payment: Payment? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "refund_id", nullable = true)
    var refund: Refund? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "payout_id", nullable = true)
    var payout: StorePayout? = null
) : MutableEntity()
