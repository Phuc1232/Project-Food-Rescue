// AC-01..11 (T8.1): ComplaintSupplementRequest responsibility.
package com.foodrescue.backend.complaint.dto
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import com.foodrescue.backend.complaint.*
import java.util.UUID
import java.math.BigDecimal
data class ComplaintSupplementRequest(@field:NotBlank @field:Size(max=10000) val description: String?=null, @field:Size(max=20) val attachmentIds: List<UUID> = emptyList())
