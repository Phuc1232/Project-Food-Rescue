// AC-01,04: FileResponse responsibility.
package com.foodrescue.backend.file.dto

import java.time.Instant
import java.util.UUID

/** File metadata; never exposes the storage key or disk path. */
data class FileResponse(val id: UUID, val originalName: String, val mimeType: String, val sizeBytes: Long, val checksum: String, val createdAt: Instant)
