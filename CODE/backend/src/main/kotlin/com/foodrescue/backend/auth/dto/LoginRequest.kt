// AC-07,08,15: LoginRequest responsibility.
package com.foodrescue.backend.auth.dto

class LoginRequest(@field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max=255) val email: String, @field:jakarta.validation.constraints.Size(max=512) val password: String)
