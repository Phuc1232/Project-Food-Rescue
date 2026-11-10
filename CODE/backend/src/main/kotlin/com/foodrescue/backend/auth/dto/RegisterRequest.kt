// AC-05,06,16,17,19,23: RegisterRequest responsibility.
package com.foodrescue.backend.auth.dto

class RegisterRequest(@field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max=255) val email: String, @field:jakarta.validation.constraints.Size(max=512) val password: String, @field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max=255) val displayName: String)
