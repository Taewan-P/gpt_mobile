package dev.chungjungsoo.gptmobile.data.agent.tool

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.diagnostics.AppLogRecorder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val altitude: Double?,
    val timestamp: Long,
    val provider: String?
)

@Singleton
class DeviceLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private fun hasFinePermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasPermission(): Boolean = hasFinePermission() ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun getCurrentLocation(timeoutMillis: Long = 30_000L): DeviceLocation? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!manager.isLocationEnabled) return null
        val fine = hasFinePermission()
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER) +
            if (fine) listOf(LocationManager.GPS_PROVIDER) else emptyList()
        val enabled = providers.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val cached = (enabled + LocationManager.PASSIVE_PROVIDER).mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)?.takeIf(::isFresh)
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }.maxByOrNull { it.time }
        if (cached != null) {
            AppLogRecorder.record("Location", "Using recent Android fix · provider=${cached.provider} · accuracy=${cached.accuracy}")
            return cached.toDeviceLocation()
        }
        if (enabled.isEmpty()) return null
        val timeout = timeoutMillis.coerceIn(1L, 30_000L)
        AppLogRecorder.record("Location", "Requesting fresh fix · providers=$enabled · precise=$fine · deadline=${timeout}ms")
        // A bounded active request wakes a cold provider, instead of waiting for another app
        // to populate its cache. The native fused provider does not require Google Play services.
        val location = withTimeoutOrNull(timeout) {
            callbackFlow {
                val listeners = mutableListOf<LocationListener>()
                val request = LocationRequest.Builder(1000L)
                    .setQuality(if (fine) LocationRequest.QUALITY_HIGH_ACCURACY else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                    .setMinUpdateIntervalMillis(1000L)
                    .setMaxUpdateDelayMillis(0L)
                    .setDurationMillis(timeout)
                    .setMaxUpdates(30)
                    .build()
                try {
                    enabled.forEach { provider ->
                        val listener = object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                if (isFresh(location)) trySend(location)
                            }
                        }
                        try {
                            manager.requestLocationUpdates(provider, request, context.mainExecutor, listener)
                            listeners.add(listener)
                        } catch (_: SecurityException) {
                            AppLogRecorder.record("Location", "Permission unavailable for $provider", "W")
                        } catch (_: IllegalArgumentException) {
                            AppLogRecorder.record("Location", "Provider unavailable: $provider", "W")
                        }
                    }
                    if (listeners.isEmpty()) close()
                    awaitClose()
                } finally {
                    // firstOrNull, timeout and caller cancellation all release every subscription.
                    listeners.forEach { listener -> runCatching { manager.removeUpdates(listener) } }
                }
            }.firstOrNull()
        }
        AppLogRecorder.record("Location", if (location == null) "Fresh fix deadline reached; no location returned" else "Fresh Android fix · provider=${location.provider} · accuracy=${location.accuracy}", if (location == null) "W" else "I")
        return location?.toDeviceLocation()
    }

    private fun isFresh(location: Location): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite() || location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        // Monotonic age survives a wall-clock correction during GPS acquisition.
        val timestamp = location.elapsedRealtimeNanos
        return if (timestamp > 0) SystemClock.elapsedRealtimeNanos() - timestamp in 0..120_000_000_000L else isRecentLocation(location.time, System.currentTimeMillis())
    }

    private fun Location.toDeviceLocation() = DeviceLocation(
        latitude = latitude,
        longitude = longitude,
        accuracy = if (hasAccuracy()) accuracy else null,
        altitude = if (hasAltitude()) altitude else null,
        timestamp = time,
        provider = provider
    )
}
