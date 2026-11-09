package com.foodrescue.backend.file.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import jakarta.persistence.*

@Entity
@Table(name = "stored_file")
class StoredFile(
    @field:Column(name = "storage_key", nullable = false, length = 255)
    var storageKey: String,

    @field:Column(name = "original_name", nullable = false, length = 255)
    var originalName: String,

    @field:Column(name = "mime_type", nullable = false, length = 255)
    var mimeType: String,

    @field:Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,

    @field:Column(name = "checksum", nullable = false, length = 255)
    var checksum: String,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "uploaded_by_id", nullable = false)
    var uploadedBy: Account
) : MutableEntity()
