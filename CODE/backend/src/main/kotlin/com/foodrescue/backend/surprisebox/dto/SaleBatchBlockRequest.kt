// AC-08 (T4.3): SaleBatchBlockRequest responsibility.
package com.foodrescue.backend.surprisebox.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SaleBatchBlockRequest(@field:NotBlank @field:Size(max = 2000) val reason: String?, val includeDeliveredBoxesIncident: Boolean = true)
