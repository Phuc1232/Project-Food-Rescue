// AC-06,12,13,14,17,24: ChangePasswordRequest responsibility.
package com.foodrescue.backend.auth.dto

class ChangePasswordRequest(@field:jakarta.validation.constraints.Size(max=512) val currentPassword: String, @field:jakarta.validation.constraints.Size(max=512) val newPassword: String)
