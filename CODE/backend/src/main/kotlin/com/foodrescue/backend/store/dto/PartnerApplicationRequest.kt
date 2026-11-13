// AC-01,02,03: PartnerApplicationRequest responsibility.
package com.foodrescue.backend.store.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.math.BigDecimal

data class PartnerApplicationRequest(
    @field:NotBlank @field:Size(max = 255) val storeName: String?,
    @field:NotBlank @field:Size(max = 1000) val address: String?,
    @field:NotNull @field:DecimalMin("-90") @field:DecimalMax("90") @field:Digits(integer = 2, fraction = 7) val latitude: BigDecimal?,
    @field:NotNull @field:DecimalMin("-180") @field:DecimalMax("180") @field:Digits(integer = 3, fraction = 7) val longitude: BigDecimal?,
    @field:Size(max = 64) val timezone: String? = null,
    @field:NotBlank @field:Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$") val contactPhone: String?,
    @field:Size(max = 2000) val businessDescription: String? = null,
    @field:NotEmpty @field:Size(max = 21) @field:Valid val openingHours: List<OpeningHourRequest>?,
    @field:NotEmpty @field:Size(max = 10) @field:Valid val documents: List<ApplicationDocumentRequest>?
)
