// AC-07,13: DocumentView responsibility.
package com.foodrescue.backend.store.dto

import java.util.UUID

/** Download through GET /api/v1/files/{fileId}/content. */
data class DocumentView(val fileId: UUID, val type: String, val originalName: String, val mimeType: String, val sizeBytes: Long)
