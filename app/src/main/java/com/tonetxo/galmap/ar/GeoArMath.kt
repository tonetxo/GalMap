package com.tonetxo.galmap.ar

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class ScreenPoint(val x: Double, val y: Double)

/** Geodesy and camera projection for location-based AR. Pure Kotlin so it can be unit tested. */
object GeoArMath {
    const val EARTH_RADIUS_M = 6_371_000.0

    /** Standard terrestrial refraction coefficient: light bends down, so distant points look higher. */
    const val REFRACTION_K = 0.13

    /** Great-circle distance (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial bearing from point 1 to point 2, clockwise from true north, in [0, 360). */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    /** Apparent elevation angle of a target, corrected for earth curvature and refraction. */
    fun elevationAngleDegrees(distanceM: Double, observerAltM: Double, targetAltM: Double): Double {
        val drop = distanceM * distanceM / (2 * EARTH_RADIUS_M) * (1 - REFRACTION_K)
        return Math.toDegrees(atan2(targetAltM - observerAltM - drop, distanceM))
    }

    fun normalizeDegrees(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** Shortest signed angle from [from] to [to], in (-180, 180]. */
    fun signedDeltaDegrees(from: Double, to: Double): Double {
        val d = normalizeDegrees(to - from)
        return if (d > 180.0) d - 360.0 else d
    }

    /** Unit vector (east, north, up) for a direction given by bearing and elevation angle. */
    fun enuDirection(bearingDeg: Double, elevationDeg: Double): DoubleArray {
        val b = Math.toRadians(bearingDeg)
        val e = Math.toRadians(elevationDeg)
        return doubleArrayOf(cos(e) * sin(b), cos(e) * cos(b), sin(e))
    }

    /**
     * Projects a world direction onto the screen of the back camera, which looks along the
     * device's -Z axis. [rotation] is the row-major device→world matrix produced by
     * `SensorManager.getRotationMatrixFromVector`, so device roll and pitch are handled
     * without remapping. Returns null when the point is behind the camera.
     */
    fun project(rotation: FloatArray, enu: DoubleArray, focalPx: Double, cx: Double, cy: Double): ScreenPoint? {
        val (e, n, u) = enu
        // device = Rᵀ · world
        val dx = rotation[0] * e + rotation[3] * n + rotation[6] * u
        val dy = rotation[1] * e + rotation[4] * n + rotation[7] * u
        val dz = rotation[2] * e + rotation[5] * n + rotation[8] * u
        val depth = -dz
        if (depth <= 1e-3) return null
        return ScreenPoint(cx + focalPx * dx / depth, cy - focalPx * dy / depth)
    }

    /**
     * Re-expresses [rotation] so its first two columns are the screen's right and up axes
     * when the display is rotated [quarterTurns] × 90° (Display.getRotation / 90), which is
     * what SensorManager.remapCoordinateSystem does. The optical axis (Z) is unchanged.
     */
    fun remapForDisplay(rotation: FloatArray, quarterTurns: Int): FloatArray {
        val r = rotation
        return when (Math.floorMod(quarterTurns, 4)) {
            1 -> floatArrayOf(r[1], -r[0], r[2], r[4], -r[3], r[5], r[7], -r[6], r[8])
            2 -> floatArrayOf(-r[0], -r[1], r[2], -r[3], -r[4], r[5], -r[6], -r[7], r[8])
            3 -> floatArrayOf(-r[1], r[0], r[2], -r[4], r[3], r[5], -r[7], r[6], r[8])
            else -> r
        }
    }

    /** Heading of the camera's optical axis in the rotation matrix's frame, in [0, 360). */
    fun cameraHeadingDegrees(rotation: FloatArray): Double =
        normalizeDegrees(Math.toDegrees(atan2(-rotation[2].toDouble(), -rotation[5].toDouble())))

    /** Pitch of the camera's optical axis above the horizon, in [-90, 90]. */
    fun cameraPitchDegrees(rotation: FloatArray): Double =
        Math.toDegrees(asin((-rotation[8].toDouble()).coerceIn(-1.0, 1.0)))

    fun focalFromHorizontalFov(widthPx: Double, fovDeg: Double): Double =
        widthPx / 2 / tan(Math.toRadians(fovDeg / 2))

    /**
     * Focal length in screen pixels for a preview that fills the view (center crop), in
     * either orientation. The preview stream with [streamAspect] is itself a center crop of
     * the active sensor area.
     */
    fun focalLengthPx(
        viewWidthPx: Double,
        viewHeightPx: Double,
        focalMm: Double,
        sensorLongMm: Double,
        sensorShortMm: Double,
        streamAspect: Double = 4.0 / 3.0,
    ): Double {
        var visibleLong = sensorLongMm
        var visibleShort = sensorShortMm
        if (sensorLongMm / sensorShortMm > streamAspect) {
            visibleLong = sensorShortMm * streamAspect
        } else {
            visibleShort = sensorLongMm / streamAspect
        }
        // The preview is rotated so the sensor's long side runs along the view's long side.
        val pxPerMm = max(max(viewWidthPx, viewHeightPx) / visibleLong, min(viewWidthPx, viewHeightPx) / visibleShort)
        return focalMm * pxPerMm
    }
}
