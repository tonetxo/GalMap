package com.tonetxo.galmap.ar

import com.tonetxo.galmap.data.FeatureFilter
import com.tonetxo.galmap.data.GeoFeature
import com.tonetxo.galmap.data.UserLocation
import kotlin.math.max

/** A feature with its position relative to the observer; recomputed only when the observer moves. */
data class PlacedFeature(
    val feature: GeoFeature,
    val distanceM: Double,
    /** Clockwise from true north. */
    val bearingDeg: Double,
    val elevationDeg: Double,
    val priority: Double,
)

data class PlacedScene(
    val features: List<PlacedFeature>,
    /** Magnetic declination at the observer, east positive. */
    val declinationDeg: Double,
)

data class ArLabel(
    val feature: GeoFeature,
    val x: Float,
    val y: Float,
    val distanceM: Double,
)

object ArProjector {
    const val MAX_LABELS = 20

    /** Ignore points the observer is practically standing on. */
    private const val MIN_DISTANCE_M = 50.0

    /** Assumed elevation for ranking when a peak has no `ele` tag. */
    private const val UNKNOWN_ELEVATION_RANK_M = 100.0

    fun place(
        observer: UserLocation,
        features: List<GeoFeature>,
        radiusM: Double,
        declinationDeg: Double,
        filter: FeatureFilter = FeatureFilter(),
    ): PlacedScene {
        val observerAlt = observer.altitudeM ?: 0.0
        val placed = features.mapNotNull { f ->
            if (!filter.accepts(f)) return@mapNotNull null
            val distance = GeoArMath.distanceMeters(observer.lat, observer.lon, f.lat, f.lon)
            if (distance > radiusM || distance < MIN_DISTANCE_M) return@mapNotNull null
            // Without a known elevation, put the label on the horizon rather than at sea level.
            val targetAlt = f.elevationM ?: observerAlt
            PlacedFeature(
                feature = f,
                distanceM = distance,
                bearingDeg = GeoArMath.bearingDegrees(observer.lat, observer.lon, f.lat, f.lon),
                elevationDeg = GeoArMath.elevationAngleDegrees(distance, observerAlt, targetAlt),
                priority = priority(f, distance),
            )
        }
        return PlacedScene(placed, declinationDeg)
    }

    /**
     * Projects the scene with the current device rotation (magnetic frame). [headingOffsetDeg] is
     * the user's manual compass calibration. Labels are returned sorted by priority, most
     * important first, so the layout can drop the least relevant on collision.
     */
    fun project(
        scene: PlacedScene,
        rotation: FloatArray,
        displayQuarterTurns: Int,
        widthPx: Double,
        heightPx: Double,
        focalPx: Double,
        headingOffsetDeg: Double,
    ): List<ArLabel> {
        val correction = headingOffsetDeg - scene.declinationDeg
        val cx = widthPx / 2
        val cy = heightPx / 2
        val screenRotation = GeoArMath.remapForDisplay(rotation, displayQuarterTurns)
        return scene.features.asSequence()
            .mapNotNull { p ->
                val enu = GeoArMath.enuDirection(p.bearingDeg + correction, p.elevationDeg)
                val point = GeoArMath.project(screenRotation, enu, focalPx, cx, cy) ?: return@mapNotNull null
                if (point.x !in 0.0..widthPx || point.y !in 0.0..heightPx) null else p to point
            }
            .sortedByDescending { (p, _) -> p.priority }
            .take(MAX_LABELS)
            .map { (p, point) -> ArLabel(p.feature, point.x.toFloat(), point.y.toFloat(), p.distanceM) }
            .toList()
    }

    /** True heading of the camera, consistent with the corrections applied in [project]. */
    fun trueHeadingDegrees(rotation: FloatArray, declinationDeg: Double, headingOffsetDeg: Double): Double =
        GeoArMath.normalizeDegrees(GeoArMath.cameraHeadingDegrees(rotation) + declinationDeg - headingOffsetDeg)

    /** More important, higher and closer features first. */
    private fun priority(feature: GeoFeature, distanceM: Double): Double {
        val elevation = (feature.elevationM ?: UNKNOWN_ELEVATION_RANK_M).coerceAtLeast(0.0)
        return (feature.importance + 1) * (elevation + 100.0) / max(distanceM / 1000.0, 0.5)
    }
}
