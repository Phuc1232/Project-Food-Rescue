// AC-09,10: StoreStatusRequest responsibility.
package com.foodrescue.backend.store.dto

import com.foodrescue.backend.store.StoreStatusAction
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class StoreStatusRequest(@field:NotNull val action: StoreStatusAction?, @field:NotBlank @field:Size(max = 2000) val reason: String?, val existingOrderHandling: com.foodrescue.backend.store.StoreSuspendOrderHandling? = null)
