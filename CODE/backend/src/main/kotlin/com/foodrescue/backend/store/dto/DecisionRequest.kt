// AC-08,09,10: DecisionRequest responsibility.
package com.foodrescue.backend.store.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import com.foodrescue.backend.store.ApplicationDecision

/** reason is required for REJECT and REQUEST_INFO (checked in the service), optional note for APPROVE. */
data class DecisionRequest(@field:NotNull val decision: ApplicationDecision?, @field:Size(max = 2000) val reason: String? = null)
