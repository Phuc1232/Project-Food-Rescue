// AC-04,06 (T5.1): SearchParams responsibility.
package com.foodrescue.backend.search.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.FieldViolation
import java.math.BigDecimal
import java.time.Instant

data class GeoFilter(val lat: Double, val lng: Double, val radiusKm: Double) {
    val latDelta get() = radiusKm / 111.0
    val lngDelta get() = radiusKm / (111.0 * Math.max(Math.cos(Math.toRadians(lat)), 0.01))
}

/** BR-T51-04 checks shared by the search endpoints; every problem is reported by field. */
object SearchParams {
    fun geo(lat: Double?, lng: Double?, radiusKm: Double?, errors: MutableList<FieldViolation>): GeoFilter? {
        if (lat == null && lng == null) {
            if (radiusKm != null) errors += FieldViolation("radiusKm", "Requires lat and lng")
            return null
        }
        if (lat == null || lng == null) { errors += FieldViolation(if (lat == null) "lat" else "lng", "lat and lng must be sent together"); return null }
        if (lat !in -90.0..90.0) errors += FieldViolation("lat", "Must be between -90 and 90")
        if (lng !in -180.0..180.0) errors += FieldViolation("lng", "Must be between -180 and 180")
        val r = radiusKm ?: 5.0
        if (r !in 0.1..50.0) errors += FieldViolation("radiusKm", "Must be between 0.1 and 50")
        return GeoFilter(lat, lng, r)
    }

    fun prices(min: BigDecimal?, max: BigDecimal?, errors: MutableList<FieldViolation>) {
        if (min != null && min.signum() < 0) errors += FieldViolation("minPrice", "Must not be negative")
        if (max != null && max.signum() < 0) errors += FieldViolation("maxPrice", "Must not be negative")
        if (min != null && max != null && min > max) errors += FieldViolation("maxPrice", "Must not be below minPrice")
    }

    fun window(from: Instant?, to: Instant?, errors: MutableList<FieldViolation>) {
        if (from != null && to != null && !from.isBefore(to)) errors += FieldViolation("pickupTo", "Must be after pickupFrom")
    }

    fun page(page: Int, size: Int, errors: MutableList<FieldViolation>) {
        if (page < 0) errors += FieldViolation("page", "Must not be negative")
        if (size !in 1..50) errors += FieldViolation("size", "Must be between 1 and 50")
    }

    fun throwIfAny(errors: List<FieldViolation>) { if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors) }
}
