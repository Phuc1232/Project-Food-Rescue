// AC-01,05,06 (T5.1): Geo responsibility.
package com.foodrescue.backend.search.service

import java.math.BigDecimal

/** Haversine distance in SQL (no PostGIS) and Google Maps directions links (UC-12). */
object Geo {
    /** Kilometres between store `s` and (:lat, :lng). */
    const val DISTANCE_SQL = """(6371.0 * 2 * asin(sqrt(power(sin(radians(s.latitude::float8 - :lat) / 2), 2) +
        cos(radians(:lat)) * cos(radians(s.latitude::float8)) * power(sin(radians(s.longitude::float8 - :lng) / 2), 2))))"""

    /** Cheap bounding box before the exact distance (1 degree latitude ~ 111 km). */
    const val BOX_SQL = "s.latitude BETWEEN :minLat AND :maxLat AND s.longitude BETWEEN :minLng AND :maxLng"

    fun directions(lat: BigDecimal, lng: BigDecimal) =
        "https://www.google.com/maps/dir/?api=1&destination=${lat.stripTrailingZeros().toPlainString()},${lng.stripTrailingZeros().toPlainString()}"

    fun round(km: Double) = Math.round(km * 100) / 100.0
}
