// AC-07: ChangeDecisionRequest responsibility.
package com.foodrescue.backend.store.dto

import com.foodrescue.backend.store.ChangeRequestDecision
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** reason is required for REJECT (checked in the service), optional note for APPROVE. */
data class ChangeDecisionRequest(@field:NotNull val decision: ChangeRequestDecision?, @field:Size(max = 2000) val reason: String? = null)
