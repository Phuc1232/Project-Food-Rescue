// AC-05: StoreChangeRequestRequest responsibility.
package com.foodrescue.backend.store.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.math.BigDecimal

/** Send only the fields to change; latitude and longitude go together. */
data class StoreChangeRequestRequest(
    @field:Size(max = 255) val name: String? = null,
    @field:Size(max = 1000) val address: String? = null,
    @field:DecimalMin("-90") @field:DecimalMax("90") @field:Digits(integer = 2, fraction = 7) val latitude: BigDecimal? = null,
    @field:DecimalMin("-180") @field:DecimalMax("180") @field:Digits(integer = 3, fraction = 7) val longitude: BigDecimal? = null,
    @field:Size(max = 2000) val note: String? = null,
    @field:Size(max = 10) @field:Valid val documents: List<ApplicationDocumentRequest>? = null
)
