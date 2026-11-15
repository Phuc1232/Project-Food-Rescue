package com.foodrescue.app.core.common

import com.foodrescue.app.core.common.GeoPoint

internal interface LocationSource {
    fun isEnabled(): Boolean
    suspend fun currentPoint(): GeoPoint?
}
