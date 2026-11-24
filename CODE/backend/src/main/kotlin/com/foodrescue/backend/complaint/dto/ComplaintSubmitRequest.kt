// AC-01..11 (T8.1): ComplaintSubmitRequest responsibility.
package com.foodrescue.backend.complaint.dto
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import com.foodrescue.backend.complaint.*
import java.util.UUID
import java.math.BigDecimal
data class ComplaintSubmitRequest(@field:NotNull val orderId: UUID?=null, @field:NotNull val type: ComplaintType?=null, @field:NotBlank @field:Size(max=10000) val description: String?=null, @field:Size(max=20) val attachmentIds: List<UUID> = emptyList())
