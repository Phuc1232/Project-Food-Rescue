package com.foodrescue.app.core.util

import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.core.common.PickupLocation
import kotlin.math.*

internal fun distanceBetween(from: GeoPoint, to: PickupLocation): Int {
    val dLat = Math.toRadians(to.latitude - from.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) * sin(dLon / 2).pow(2)
    return (6_371_000.0 * 2 * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1 - a).coerceIn(0.0, 1.0)))).roundToInt()
}
