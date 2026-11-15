// AC-06 (T3.4): StaffPermissionsRequest responsibility.
package com.foodrescue.backend.staff.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** Replaces the whole permission set. */
data class StaffPermissionsRequest(@field:NotNull @field:Size(max = 10) val permissions: List<String>?)
