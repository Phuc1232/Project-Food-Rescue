package com.foodrescue.app.feature.discovery.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.feature.discovery.domain.LocationSource

internal class AndroidLocationSource(private val context: Context) : LocationSource {
    override fun isEnabled() = LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))

    @SuppressLint("MissingPermission")
    override suspend fun currentPoint(): GeoPoint? {
        val manager = context.getSystemService(LocationManager::class.java)
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            precise && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return withTimeoutOrNull(18_000) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                    if (continuation.isActive) continuation.resume(location?.let { GeoPoint(it.latitude, it.longitude, it.accuracy) })
                }
            }
        }
    }
}
