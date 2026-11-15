// AC-08,09 (T4.1): PriceDocumentView responsibility.
package com.foodrescue.backend.catalog.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Download the file through GET /api/v1/files/{fileId}/content. replacedById != null = superseded (kept for traceability). */
data class PriceDocumentView(val id: UUID, val menuItemId: UUID?, val fileId: UUID, val originalName: String, val description: String,
                             val appliesFrom: LocalDate, val uploadedAt: Instant, val replacedById: UUID?)
