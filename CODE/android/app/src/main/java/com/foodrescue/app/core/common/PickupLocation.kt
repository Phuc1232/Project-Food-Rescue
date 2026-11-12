package com.foodrescue.app.core.common

data class PickupLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(address.isNotBlank())
    }
}
