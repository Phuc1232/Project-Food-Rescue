// AC-06,12,13,14,17,24: ResetPasswordRequest responsibility.
package com.foodrescue.backend.auth.dto

class ResetPasswordRequest(@field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max=512) val token: String, @field:jakarta.validation.constraints.Size(max=512) val newPassword: String)
