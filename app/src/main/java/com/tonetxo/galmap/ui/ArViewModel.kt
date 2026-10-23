package com.tonetxo.galmap.ui

import android.app.Application
import android.content.Context
import android.hardware.GeomagneticField
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonetxo.galmap.ar.ArLabel
import com.tonetxo.galmap.ar.ArProjector
import com.tonetxo.galmap.ar.GeoArMath
import com.tonetxo.galmap.ar.PlacedScene
import com.tonetxo.galmap.data.FeatureRepository
import com.tonetxo.galmap.data.GeoFeature
import com.tonetxo.galmap.data.UserLocation
import com.tonetxo.galmap.sensors.DeviceOrientation
import com.tonetxo.galmap.sensors.LocationTracker
import com.tonetxo.galmap.sensors.OrientationTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.io.File
import kotlin.math.atan

data class Viewport(val width: Int, val height: Int)

data class CameraIntrinsics(val focalMm: Double, val sensorLongMm: Double, val sensorShortMm: Double)

data class FeaturesState(
    val items: List<GeoFeature> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class ArUiState(
    val labels: List<ArLabel> = emptyList(),
    val trueHeadingDeg: Double? = null,
    val focalPx: Double = 0.0,
    val compassUnreliable: Boolean = false,
    val location: UserLocation? = null,
    val placesInRadius: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val radiusKm: Int = DEFAULT_RADIUS_KM,
    val headingOffsetDeg: Float = 0f,
)

private const val DEFAULT_RADIUS_KM = 20

private data class Frame(
    val labels: List<ArLabel> = emptyList(),
    val trueHeadingDeg: Double? = null,
    val focalPx: Double = 0.0,
    val compassUnreliable: Boolean = false,
    val placesInRadius: Int = 0,
)

@OptIn(FlowPreview::class)
class ArViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("galmap", Context.MODE_PRIVATE)
    private val repository = FeatureRepository(File(app.filesDir, "features_cache.json"))

    private val radiusKm = MutableStateFlow(prefs.getInt(KEY_RADIUS_KM, DEFAULT_RADIUS_KM))
    private val headingOffsetDeg = MutableStateFlow(prefs.getFloat(KEY_HEADING_OFFSET, 0f))
    private val viewport = MutableStateFlow<Viewport?>(null)
    private val intrinsics = MutableStateFlow<CameraIntrinsics?>(null)

    /** Last focal length used, to convert calibration drags from pixels to degrees. */
    @Volatile
    private var focalPx = 0.0

    // Everything below is cold until the UI subscribes, so GPS and sensors stop with the screen.
    private val location: StateFlow<UserLocation?> = LocationTracker(app).locations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val orientation: StateFlow<DeviceOrientation?> = OrientationTracker(app).orientation()
        .catch { /* No rotation sensor: labels simply never appear. */ }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val features: StateFlow<FeaturesState> = channelFlow {
        var current = FeaturesState()
        send(current)
        // Only movements beyond REFETCH_DISTANCE_M reach the repository, which itself serves
        // from cache while still inside the downloaded circle.
        val significantLocation = location.filterNotNull().distinctUntilChanged { old, new ->
            GeoArMath.distanceMeters(old.lat, old.lon, new.lat, new.lon) < REFETCH_DISTANCE_M
        }
        combine(significantLocation, radiusKm.debounce(RADIUS_DEBOUNCE_MS)) { loc, r -> loc to r }
            .collectLatest { (loc, r) ->
                // Public Overpass servers are often overloaded, so keep retrying until it works.
                while (true) {
                    current = current.copy(loading = true, error = null)
                    send(current)
                    try {
                        current = FeaturesState(items = repository.featuresAround(loc.lat, loc.lon, r * 1000.0))
                        send(current)
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        current = current.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
                        send(current)
                        delay(RETRY_DELAY_MS)
                    }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeaturesState())

    private val scene = combine(location, features, radiusKm) { loc, fs, r ->
        loc?.let { ArProjector.place(it, fs.items, r * 1000.0, declination(it)) }
    }.flowOn(Dispatchers.Default)

    private val frame = combine(scene, orientation, viewport, intrinsics, headingOffsetDeg) { sc, ori, vp, intr, offset ->
        if (vp == null || ori == null) return@combine Frame()
        val focal = intr?.let {
            GeoArMath.focalLengthPx(vp.width.toDouble(), vp.height.toDouble(), it.focalMm, it.sensorLongMm, it.sensorShortMm)
        } ?: GeoArMath.focalFromHorizontalFov(vp.width.toDouble(), FALLBACK_HFOV_DEG)
        focalPx = focal
        val scn = sc ?: PlacedScene(emptyList(), 0.0)
        Frame(
            labels = ArProjector.project(scn, ori.rotation, vp.width.toDouble(), vp.height.toDouble(), focal, offset.toDouble()),
            trueHeadingDeg = ArProjector.trueHeadingDegrees(ori.rotation, scn.declinationDeg, offset.toDouble()),
            focalPx = focal,
            compassUnreliable = ori.compassUnreliable,
            placesInRadius = scn.features.size,
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<ArUiState> =
        combine(frame, features, location, radiusKm, headingOffsetDeg) { f, fs, loc, r, offset ->
            ArUiState(
                labels = f.labels,
                trueHeadingDeg = f.trueHeadingDeg,
                focalPx = f.focalPx,
                compassUnreliable = f.compassUnreliable,
                location = loc,
                placesInRadius = f.placesInRadius,
                loading = fs.loading,
                error = fs.error,
                radiusKm = r,
                headingOffsetDeg = offset,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ArUiState())

    fun setViewport(width: Int, height: Int) {
        if (width > 0 && height > 0) viewport.value = Viewport(width, height)
    }

    fun setCameraIntrinsics(value: CameraIntrinsics?) {
        intrinsics.value = value
    }

    fun setRadiusKm(km: Int) {
        radiusKm.value = km
        prefs.edit { putInt(KEY_RADIUS_KM, km) }
    }

    /** Horizontal drag of [dxPx] pixels rotates the labels by the same on-screen amount. */
    fun adjustHeadingOffset(dxPx: Float) {
        val f = focalPx.takeIf { it > 0 } ?: return
        val delta = Math.toDegrees(atan(dxPx / f)).toFloat()
        val updated = GeoArMath.signedDeltaDegrees(0.0, (headingOffsetDeg.value + delta).toDouble()).toFloat()
        headingOffsetDeg.value = updated
        prefs.edit { putFloat(KEY_HEADING_OFFSET, updated) }
    }

    fun resetHeadingOffset() {
        headingOffsetDeg.value = 0f
        prefs.edit { putFloat(KEY_HEADING_OFFSET, 0f) }
    }

    private fun declination(loc: UserLocation): Double =
        GeomagneticField(
            loc.lat.toFloat(),
            loc.lon.toFloat(),
            (loc.altitudeM ?: 0.0).toFloat(),
            System.currentTimeMillis(),
        ).declination.toDouble()

    private companion object {
        const val KEY_RADIUS_KM = "radius_km"
        const val KEY_HEADING_OFFSET = "heading_offset_deg"
        const val STOP_TIMEOUT_MS = 5_000L
        const val RADIUS_DEBOUNCE_MS = 400L
        const val REFETCH_DISTANCE_M = 300.0
        const val RETRY_DELAY_MS = 30_000L
        const val FALLBACK_HFOV_DEG = 60.0
    }
}
