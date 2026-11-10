// AC-09,21,22: RefreshRequest responsibility.
package com.foodrescue.backend.auth.dto

class RefreshRequest(@field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max=512) val refreshToken: String)
