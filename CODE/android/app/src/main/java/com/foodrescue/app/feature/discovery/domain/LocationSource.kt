package com.foodrescue.app.feature.discovery.domain

import com.foodrescue.app.core.common.GeoPoint

internal interface LocationSource {
    fun isEnabled(): Boolean
    suspend fun currentPoint(): GeoPoint?
}
