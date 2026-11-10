// AC-06,13,20,24: ActionTokenRequest responsibility.
package com.foodrescue.backend.auth.dto

class ActionTokenRequest(@field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max=512) val token: String)
