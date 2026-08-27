package com.foresightlabs.aether.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.foresightlabs.aether.data.telegram.TelegramClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Geo coordinate data representation independent of Android framework stubs.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float = 0f
)

/**
 * Pluggable location provider contract for testability and runtime precision adaptation.
 */
interface LocationProvider {
    fun requestLocationUpdates(
        intervalMs: Long,
        minDisplacementMeters: Float,
        onLocation: (GeoPoint) -> Unit
    ): LocationSubscription?
}

/**
 * Represents an active subscription to OS location updates.
 */
fun interface LocationSubscription {
    fun cancel()
}

/**
 * Pluggable gateway to Telegram live-location operations.
 */
interface LiveLocationGateway {
    suspend fun editLiveLocation(
        chatId: Long,
        messageId: Long,
        latitude: Double,
        longitude: Double,
        livePeriod: Int,
        heading: Int
    ): Result<Unit>

    suspend fun stopLiveLocation(
        chatId: Long,
        messageId: Long
    ): Result<Unit>
}

/**
 * Standard Android OS LocationManager implementation respecting runtime coarse/fine permissions.
 */
class SystemLocationProvider(private val context: Context) : LocationProvider {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(
        intervalMs: Long,
        minDisplacementMeters: Float,
        onLocation: (GeoPoint) -> Unit
    ): LocationSubscription? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return null

        val provider = when {
            hasFine && locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true -> LocationManager.GPS_PROVIDER
            (hasFine || hasCoarse) && locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true -> LocationManager.NETWORK_PROVIDER
            (hasFine || hasCoarse) && locationManager?.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) == true -> LocationManager.PASSIVE_PROVIDER
            else -> null
        } ?: return null

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(
                    GeoPoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        bearing = location.bearing
                    )
                )
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        return try {
            locationManager?.requestLocationUpdates(
                provider,
                intervalMs,
                minDisplacementMeters,
                listener,
                context.mainLooper
            )
            LocationSubscription {
                try {
                    locationManager?.removeUpdates(listener)
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Default Telegram gateway delegating to TelegramClient.
 */
class TelegramLiveLocationGateway(private val telegram: TelegramClient) : LiveLocationGateway {
    override suspend fun editLiveLocation(
        chatId: Long,
        messageId: Long,
        latitude: Double,
        longitude: Double,
        livePeriod: Int,
        heading: Int
    ): Result<Unit> {
        val res = telegram.editLiveLocation(chatId, messageId, latitude, longitude, livePeriod, heading)
        return if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("Edit failed"))
    }

    override suspend fun stopLiveLocation(chatId: Long, messageId: Long): Result<Unit> {
        val res = telegram.stopLiveLocation(chatId, messageId)
        return if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("Stop failed"))
    }
}

/**
 * Data class representing a tracked live location session.
 */
data class ActiveLiveLocationSession(
    val chatId: Long,
    val messageId: Long,
    val startTimeMillis: Long,
    val totalPeriodSeconds: Int,
    val subscription: LocationSubscription?,
    val expirationJob: Job?
)

/**
 * Single application-scoped owner and coordinator for active live-location sessions.
 * Ensures consistent lifecycle across UI triggers, system notification actions,
 * background tracking, and session auto-expiration.
 */
class LiveLocationCoordinator(
    private val context: Context?,
    private val locationProvider: LocationProvider,
    private val gateway: LiveLocationGateway,
    private val scope: CoroutineScope
) {
    private val _activeSessions = ConcurrentHashMap<Long, ActiveLiveLocationSession>() // Key: messageId
    val activeSessions: Map<Long, ActiveLiveLocationSession> get() = _activeSessions

    fun isSharing(messageId: Long): Boolean = _activeSessions.containsKey(messageId)

    fun startLiveSharing(chatId: Long, messageId: Long, livePeriodSeconds: Int) {
        // Stop any preexisting session for this message
        stopLiveSharing(chatId, messageId)

        var sessionRef: ActiveLiveLocationSession? = null

        val subscription = locationProvider.requestLocationUpdates(
            intervalMs = 10_000L,
            minDisplacementMeters = 10f
        ) { point ->
            sessionRef?.let { session ->
                onNewLocationFix(session, point)
            }
        }

        val expirationJob = scope.launch {
            delay(livePeriodSeconds * 1000L)
            stopLiveSharing(chatId, messageId)
        }

        val session = ActiveLiveLocationSession(
            chatId = chatId,
            messageId = messageId,
            startTimeMillis = System.currentTimeMillis(),
            totalPeriodSeconds = livePeriodSeconds,
            subscription = subscription,
            expirationJob = expirationJob
        )
        sessionRef = session
        _activeSessions[messageId] = session

        context?.let { LiveLocationService.start(it) }
    }

    private fun onNewLocationFix(session: ActiveLiveLocationSession, point: GeoPoint) {
        val elapsedSec = ((System.currentTimeMillis() - session.startTimeMillis) / 1000).toInt()
        val remainingSec = (session.totalPeriodSeconds - elapsedSec).coerceAtLeast(0)

        if (remainingSec <= 0) {
            stopLiveSharing(session.chatId, session.messageId)
            return
        }

        scope.launch {
            gateway.editLiveLocation(
                chatId = session.chatId,
                messageId = session.messageId,
                latitude = point.latitude,
                longitude = point.longitude,
                livePeriod = remainingSec,
                heading = point.bearing.toInt()
            )
        }
    }

    fun stopLiveSharing(chatId: Long, messageId: Long) {
        val session = _activeSessions.remove(messageId) ?: return
        session.subscription?.cancel()
        session.expirationJob?.cancel()

        scope.launch {
            gateway.stopLiveLocation(chatId, messageId)
        }

        if (_activeSessions.isEmpty()) {
            context?.let { LiveLocationService.stop(it) }
        }
    }

    fun stopAll() {
        val sessions = _activeSessions.values.toList()
        _activeSessions.clear()

        sessions.forEach { session ->
            session.subscription?.cancel()
            session.expirationJob?.cancel()
            scope.launch {
                gateway.stopLiveLocation(session.chatId, session.messageId)
            }
        }

        context?.let { LiveLocationService.stop(it) }
    }
}
