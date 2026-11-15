package com.foodrescue.app.feature.store.domain

import com.foodrescue.app.core.common.PickupLocation

/** Public storefront information only. No owner account identity or payout details. */
internal data class PublicStore(
    val id: Int, val name: String, val category: String, val description: String,
    val phone: String, val area: String, val opensAt: String, val closesAt: String,
    val location: PickupLocation,
    val contactEmail: String = "",
)

internal interface PublicStoreRepository {
    suspend fun getStores(): List<PublicStore>
}
