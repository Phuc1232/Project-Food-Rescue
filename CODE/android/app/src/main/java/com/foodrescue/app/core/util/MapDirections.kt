package com.foodrescue.app.core.util

import com.foodrescue.app.core.common.PickupLocation
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

/** Both attempts carry the identical coordinate destination; no order/account data leaves the app. */
internal fun openDirectionsWithFallback(location: PickupLocation, launch: (String, String?) -> Boolean): Boolean {
    val url = directionsUrl(location)
    return launch(url, GOOGLE_MAPS_PACKAGE) || launch(url, null)
}

/** Maps chooses the origin and transport mode; opening this URL does not start navigation. */
internal fun directionsUrl(location: PickupLocation): String {
    val destination = "${location.latitude},${location.longitude}"
    return "https://www.google.com/maps/dir/?api=1&destination=" +
        URLEncoder.encode(destination, StandardCharsets.UTF_8.name())
}
