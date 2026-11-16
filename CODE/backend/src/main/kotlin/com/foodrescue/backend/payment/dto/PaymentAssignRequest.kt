// AC-02 (T6.4): PaymentAssignRequest responsibility.
package com.foodrescue.backend.payment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Links an unassigned payment to an order (e.g. the customer proved the transfer); it then becomes a refund obligation. */
data class PaymentAssignRequest(@field:NotBlank @field:Size(max = 64) val orderCode: String?, @field:NotBlank @field:Size(max = 1000) val note: String?)
