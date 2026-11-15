// AC-02,03,04 (T3.4): StaffInviteRequest responsibility.
package com.foodrescue.backend.staff.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** email is normalized and checked like registration. displayName is required only when no account exists for the email. permissions may be empty. */
data class StaffInviteRequest(
    @field:NotBlank @field:Size(max = 320) val email: String?,
    @field:Size(max = 255) val displayName: String? = null,
    @field:NotNull @field:Size(max = 10) val permissions: List<String>?
)
