// AC-02..07 (T5.2): OrderCreateRequest responsibility.
package com.foodrescue.backend.order.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.util.UUID

/** acceptedPolicyVersion: the financial policy version shown in the preview (D02); a newer one returns 409 POLICY_CHANGED. */
data class OrderCreateRequest(@field:NotNull val batchId: UUID?, @field:NotNull @field:Min(1) val quantity: Int?,
                              @field:NotNull val acceptedPolicyVersion: Int?)
