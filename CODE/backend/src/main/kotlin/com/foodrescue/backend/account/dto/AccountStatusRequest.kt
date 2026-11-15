// AC-03,04,05,06 (T3.5): AccountStatusRequest responsibility.
package com.foodrescue.backend.account.dto

import com.foodrescue.backend.account.AccountStatusAction
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** outstandingPlan is required to lock an account that still has unfinished orders (D10 of the business document). */
data class AccountStatusRequest(
    @field:NotNull val action: AccountStatusAction?,
    @field:NotBlank @field:Size(max = 2000) val reason: String?,
    @field:Size(max = 2000) val outstandingPlan: String? = null
)
