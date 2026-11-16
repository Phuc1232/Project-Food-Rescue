// AC-07 (T3.4): StaffRevokeRequest responsibility.
package com.foodrescue.backend.staff.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class StaffRevokeRequest(@field:NotBlank @field:Size(max = 2000) val reason: String?)
