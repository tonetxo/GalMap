package com.tonetxo.galmap.ar

import com.tonetxo.galmap.data.FeatureFilter
import com.tonetxo.galmap.data.FeatureType
import com.tonetxo.galmap.data.GeoFeature
import com.tonetxo.galmap.data.Importance
import com.tonetxo.galmap.data.UserLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GeoArMathTest {

    /** Phone upright in portrait, back camera facing magnetic north. */
    private val facingNorth = floatArrayOf(
        1f, 0f, 0f,
        0f, 0f, -1f,
        0f, 1f, 0f,
    )

    /** Upright, camera facing [headingDeg]: rotation of [facingNorth] about the up axis. */
    private fun facing(headingDeg: Double): FloatArray {
        val h = Math.toRadians(headingDeg)
        val c = cos(h).toFloat()
        val s = sin(h).toFloat()
        // Columns are the device X (right), Y (up) and Z (towards the user) axes in ENU.
        return floatArrayOf(
            c, 0f, -s,
            -s, 0f, -c,
            0f, 1f, 0f,
        )
    }

    @Test
    fun `one degree of longitude at the equator is about 111 km`() {
        assertEquals(111_195.0, GeoArMath.distanceMeters(0.0, 0.0, 0.0, 1.0), 1.0)
    }

    @Test
    fun `Santiago to A Coruña distance`() {
        // Catedral de Santiago → Torre de Hércules, ~ 57 km.
        val d = GeoArMath.distanceMeters(42.8806, -8.5446, 43.3859, -8.4065)
        assertEquals(57_300.0, d, 1_000.0)
    }

    @Test
    fun `bearings to cardinal points`() {
        assertEquals(0.0, GeoArMath.bearingDegrees(42.0, -8.0, 43.0, -8.0), 1e-6)
        assertEquals(180.0, GeoArMath.bearingDegrees(43.0, -8.0, 42.0, -8.0), 1e-6)
        assertEquals(90.0, GeoArMath.bearingDegrees(0.0, 0.0, 0.0, 1.0), 1e-6)
        assertEquals(270.0, GeoArMath.bearingDegrees(0.0, 1.0, 0.0, 0.0), 1e-6)
    }

    @Test
    fun `curvature and refraction lower distant targets`() {
        // At 50 km a point at the observer's height sits ~ 0.2° below the horizon.
        val angle = GeoArMath.elevationAngleDegrees(50_000.0, 100.0, 100.0)
        assertEquals(-0.196, angle, 0.01)
        assertTrue(GeoArMath.elevationAngleDegrees(1_000.0, 0.0, 100.0) > 5.0)
    }

    @Test
    fun `signed delta wraps around north`() {
        assertEquals(20.0, GeoArMath.signedDeltaDegrees(350.0, 10.0), 1e-9)
        assertEquals(-20.0, GeoArMath.signedDeltaDegrees(10.0, 350.0), 1e-9)
    }

    @Test
    fun `target straight ahead projects to the centre`() {
        val p = GeoArMath.project(facingNorth, GeoArMath.enuDirection(0.0, 0.0), 1000.0, 500.0, 1000.0)
        assertNotNull(p)
        assertEquals(500.0, p!!.x, 1e-6)
        assertEquals(1000.0, p.y, 1e-6)
    }

    @Test
    fun `targets right and above appear right and above`() {
        val p = GeoArMath.project(facingNorth, GeoArMath.enuDirection(10.0, 5.0), 1000.0, 500.0, 1000.0)!!
        assertTrue(p.x > 500.0)
        assertTrue(p.y < 1000.0)
        // Pinhole: horizontal offset is f · tan(10°) for a target on the horizon.
        val h = GeoArMath.project(facingNorth, GeoArMath.enuDirection(10.0, 0.0), 1000.0, 500.0, 1000.0)!!
        assertEquals(500.0 + 1000.0 * Math.tan(Math.toRadians(10.0)), h.x, 1e-3)
    }

    @Test
    fun `targets behind the camera are not projected`() {
        assertNull(GeoArMath.project(facingNorth, GeoArMath.enuDirection(180.0, 0.0), 1000.0, 500.0, 1000.0))
    }

    @Test
    fun `camera heading and pitch from rotation`() {
        assertEquals(0.0, GeoArMath.cameraHeadingDegrees(facingNorth), 1e-6)
        assertEquals(0.0, GeoArMath.cameraPitchDegrees(facingNorth), 1e-6)
        assertEquals(90.0, GeoArMath.cameraHeadingDegrees(facing(90.0)), 1e-4)
        assertEquals(315.0, GeoArMath.cameraHeadingDegrees(facing(315.0)), 1e-4)
    }

    @Test
    fun `aligning with a target makes the true heading point at it`() {
        val rotation = facing(47.0) // compass is off: the camera really faces the target at 85°
        val declination = -1.5
        val offset = ArProjector.headingOffsetToAlign(rotation, declination, 85.0)
        assertEquals(85.0, ArProjector.trueHeadingDegrees(rotation, declination, offset), 1e-6)
    }

    @Test
    fun `yaw rotation adds to the camera heading and keeps pitch`() {
        val turned = GeoArMath.rotateYaw(facing(10.0), 20.0)
        assertEquals(30.0, GeoArMath.cameraHeadingDegrees(turned), 1e-4)
        assertEquals(0.0, GeoArMath.cameraPitchDegrees(turned), 1e-4)
        val back = GeoArMath.rotateYaw(turned, -30.0)
        for (i in facingNorth.indices) assertEquals(facingNorth[i], back[i], 1e-5f)
    }

    @Test
    fun `rotated camera centres the target it faces`() {
        val p = GeoArMath.project(facing(120.0), GeoArMath.enuDirection(120.0, 0.0), 800.0, 400.0, 900.0)!!
        assertEquals(400.0, p.x, 1e-3)
        assertEquals(900.0, p.y, 1e-3)
    }

    @Test
    fun `focal length for a 4-3 sensor filling a tall portrait view`() {
        // 8x6 mm sensor, 6 mm lens, 1080x2400 view: height limits → 2400/8 px per mm.
        val f = GeoArMath.focalLengthPx(1080.0, 2400.0, 6.0, 8.0, 6.0)
        assertEquals(6.0 * 300.0, f, 1e-6)
    }

    @Test
    fun `declination and manual offset shift labels consistently with heading`() {
        val observer = UserLocation(42.0, -8.0, 0.0, 5f)
        val peak = GeoFeature("node/1", "Pico", FeatureType.PEAK, 42.1, -8.0, 0.0) // due true north
        val declination = -2.0 // magnetic north is 2° west of true north
        val scene = ArProjector.place(observer, listOf(peak), 50_000.0, declination, FeatureFilter(minImportance = Importance.MIN))
        // Camera pointing at magnetic 2° → true north: the peak must be centred.
        val label = ArProjector.project(scene, facing(2.0), 0, 1000.0, 2000.0, 1000.0, 0.0).single()
        assertEquals(500.0, label.x.toDouble(), 0.5)
        assertEquals(0.0, GeoArMath.signedDeltaDegrees(0.0, ArProjector.trueHeadingDegrees(facing(2.0), declination, 0.0)), 1e-4)
    }
}
