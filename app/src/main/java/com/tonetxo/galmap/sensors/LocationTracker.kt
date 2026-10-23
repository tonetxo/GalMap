package com.tonetxo.galmap.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.altitude.AltitudeConverter
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tonetxo.galmap.data.UserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Emits the user's position with altitude above mean sea level, the datum OSM `ele` uses. */
class LocationTracker(context: Context) {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private val altitudeConverter by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) AltitudeConverter() else null
    }

    // Callers must hold a location permission; the UI gates on it before collecting.
    @SuppressLint("MissingPermission")
    fun locations(): Flow<UserLocation> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        client.lastLocation.addOnSuccessListener { last -> last?.let { trySend(it) } }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
        .conflate()
        .map(::toUserLocation)
        .flowOn(Dispatchers.IO)

    private fun toUserLocation(location: Location) = UserLocation(
        lat = location.latitude,
        lon = location.longitude,
        altitudeM = mslAltitude(location),
        accuracyM = location.accuracy,
    )

    /**
     * GNSS altitude is relative to the WGS84 ellipsoid, ~50 m above sea level in Galicia.
     * Android 14+ can convert it with an on-device geoid model (blocking, hence IO).
     */
    private fun mslAltitude(location: Location): Double? {
        if (!location.hasAltitude()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (location.hasMslAltitude()) return location.mslAltitudeMeters
            val converted = runCatching {
                altitudeConverter?.addMslAltitudeToLocation(appContext, location)
                location.mslAltitudeMeters
            }.getOrNull()
            if (converted != null) return converted
        }
        return location.altitude
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 2_000L
        const val MIN_UPDATE_INTERVAL_MS = 1_000L
    }
}
