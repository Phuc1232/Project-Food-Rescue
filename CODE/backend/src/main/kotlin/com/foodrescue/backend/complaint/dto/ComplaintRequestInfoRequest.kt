// AC-01..11 (T8.1): ComplaintRequestInfoRequest responsibility.
package com.foodrescue.backend.complaint.dto
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import com.foodrescue.backend.complaint.*
import java.util.UUID
import java.math.BigDecimal
data class ComplaintRequestInfoRequest(@field:NotBlank @field:Size(max=10000) val note: String?=null)
