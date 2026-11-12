package com.foodrescue.app.core.common

data class GeoPoint(val latitude: Double, val longitude: Double, val accuracy: Float = 0f) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) }
}
