// AC-05,07: StoreChangeValues responsibility.
package com.foodrescue.backend.store.dto

import java.math.BigDecimal

/** D04-bound store fields (T3.3 Q1); null = not part of the change. Stored as JSON in store_change_request. */
data class StoreChangeValues(val name: String? = null, val address: String? = null, val latitude: BigDecimal? = null,
                             val longitude: BigDecimal? = null)
