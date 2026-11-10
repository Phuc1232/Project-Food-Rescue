// AC-06,13,20,24: EmailRequest responsibility.
package com.foodrescue.backend.auth.dto

class EmailRequest(@field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max=255) val email: String)
