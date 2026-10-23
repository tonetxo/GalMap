package com.tonetxo.galmap.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayRotationMathTest {

    /** Upright portrait, camera facing north (see GeoArMathTest). */
    private val portraitNorth = floatArrayOf(
        1f, 0f, 0f,
        0f, 0f, -1f,
        0f, 1f, 0f,
    )

    /**
     * Same camera direction with the phone turned clockwise to landscape, top edge to the
     * right (Surface.ROTATION_90): device X (right edge) points down, device Y (top) east.
     */
    private val landscapeNorth = floatArrayOf(
        0f, 1f, 0f,
        0f, 0f, -1f,
        -1f, 0f, 0f,
    )

    @Test
    fun `remap with zero turns is identity`() {
        assertTrue(portraitNorth.contentEquals(GeoArMath.remapForDisplay(portraitNorth, 0)))
    }

    @Test
    fun `landscape remap restores screen axes`() {
        // After remapping, screen right must point east and screen up must point up, as in portrait.
        val remapped = GeoArMath.remapForDisplay(landscapeNorth, 1)
        for (i in portraitNorth.indices) assertEquals(portraitNorth[i], remapped[i], 1e-6f)
    }

    @Test
    fun `peak up and right stays up and right in landscape`() {
        val enu = GeoArMath.enuDirection(10.0, 5.0)
        val remapped = GeoArMath.remapForDisplay(landscapeNorth, 1)
        val p = GeoArMath.project(remapped, enu, 1000.0, 1000.0, 500.0)!!
        assertTrue(p.x > 1000.0)
        assertTrue(p.y < 500.0)
    }

    @Test
    fun `four quarter turns compose to identity`() {
        var r = landscapeNorth
        repeat(4) { r = GeoArMath.remapForDisplay(r, 1) }
        for (i in r.indices) assertEquals(landscapeNorth[i], r[i], 1e-6f)
    }
}
