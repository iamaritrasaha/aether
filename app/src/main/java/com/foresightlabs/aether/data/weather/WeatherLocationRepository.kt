package com.foresightlabs.aether.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.foresightlabs.aether.data.preferences.ManualWeatherLocation
import com.foresightlabs.aether.data.preferences.WeatherLocationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Resolved location used to query weather forecasts.
 */
data class ResolvedWeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String?,
    val isManual: Boolean,
    val timezoneId: String? = null
)

/**
 * Authoritative location resolver for Aether Weather.
 *
 * Privacy & Accuracy model:
 * - ACCESS_COARSE_LOCATION only (no GPS/GNSS requirement)
 * - Automatic mode resolves fresh Android NETWORK_PROVIDER or PASSIVE_PROVIDER fixes
 * - Stale fixes (> 30 min) trigger a one-shot NETWORK_PROVIDER request
 * - Best-effort Android Geocoder: falls back cleanly to "Current location" if reverse
 *   geocoding fails without blocking weather retrieval
 * - Zero third-party IP geolocation calls
 */
object WeatherLocationRepository {

    const val FRESHNESS_THRESHOLD_MS = 30 * 60 * 1000L // 30 minutes

    fun hasCoarseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Resolves the target location for weather fetching.
     */
    suspend fun resolveLocation(
        context: Context,
        locationMode: WeatherLocationMode = WeatherLocationMode.AUTOMATIC,
        manualLocation: ManualWeatherLocation? = null
    ): ResolvedWeatherLocation? = withContext(Dispatchers.IO) {
        // 1. Manually Selected City
        if (locationMode == WeatherLocationMode.MANUAL && manualLocation != null) {
            return@withContext ResolvedWeatherLocation(
                latitude = manualLocation.latitude,
                longitude = manualLocation.longitude,
                locationLabel = manualLocation.name,
                isManual = true,
                timezoneId = manualLocation.timezone
            )
        }

        // 2. Automatic Mode: Check permission
        if (!hasCoarseLocationPermission(context)) {
            return@withContext null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val now = System.currentTimeMillis()

        // 2 & 3: Check fresh last-known NETWORK_PROVIDER and PASSIVE_PROVIDER
        val networkLastKnown = getLastKnown(locationManager, LocationManager.NETWORK_PROVIDER)
        val passiveLastKnown = getLastKnown(locationManager, LocationManager.PASSIVE_PROVIDER)
        val gpsLastKnown = getLastKnown(locationManager, LocationManager.GPS_PROVIDER)

        val candidates = listOfNotNull(networkLastKnown, passiveLastKnown, gpsLastKnown)
            .filter { (now - it.time) < FRESHNESS_THRESHOLD_MS }
            .sortedByDescending { it.time }

        var resolvedLoc = candidates.firstOrNull()

        // 4. One-shot NETWORK_PROVIDER current location request if no fresh fix
        if (resolvedLoc == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            resolvedLoc = requestSingleNetworkLocation(context, locationManager)
        }

        // Fallback: If still no location, allow slightly older last-known if reasonably recent (< 2 hours)
        if (resolvedLoc == null) {
            val olderCandidates = listOfNotNull(networkLastKnown, passiveLastKnown, gpsLastKnown)
                .filter { (now - it.time) < 2 * 60 * 60 * 1000L }
                .sortedByDescending { it.time }
            resolvedLoc = olderCandidates.firstOrNull()
        }

        if (resolvedLoc == null) {
            return@withContext null
        }

        val localityName = resolveLocalityName(context, resolvedLoc.latitude, resolvedLoc.longitude)

        ResolvedWeatherLocation(
            latitude = resolvedLoc.latitude,
            longitude = resolvedLoc.longitude,
            locationLabel = localityName ?: "Current location",
            isManual = false
        )
    }

    private fun getLastKnown(locationManager: LocationManager, provider: String): Location? {
        return try {
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.getLastKnownLocation(provider)
            } else null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun requestSingleNetworkLocation(
        context: Context,
        locationManager: LocationManager
    ): Location? {
        if (!hasCoarseLocationPermission(context)) return null
        return withTimeoutOrNull(4500L) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            locationManager.removeUpdates(this)
                        } catch (_: Exception) {}
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {
                        try {
                            locationManager.removeUpdates(this)
                        } catch (_: Exception) {}
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (_: Exception) {}
                }

                try {
                    locationManager.requestSingleUpdate(
                        LocationManager.NETWORK_PROVIDER,
                        listener,
                        Looper.getMainLooper()
                    )
                } catch (_: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    /**
     * Best-effort Android Geocoder reverse lookup for human-readable locality.
     */
    fun resolveLocalityName(context: Context, lat: Double, lon: Double): String? {
        return try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val address = addresses?.firstOrNull() ?: return null
            address.locality ?: address.subAdminArea ?: address.adminArea
        } catch (_: Exception) {
            null
        }
    }
}
