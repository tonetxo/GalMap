package com.tonetxo.galmap.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledDatasetTest {

    private val galicia = BundledDataset.Meta(south = 41.40, west = -9.70, north = 44.00, east = -6.20, generated = "test")

    @Test
    fun `circles inside Galicia are covered`() {
        assertTrue(galicia.covers(42.5545, -8.9910, 45_000.0)) // Ribeira, 40 km + margin
        assertTrue(galicia.covers(42.8806, -8.5446, 45_000.0)) // Santiago
    }

    @Test
    fun `circles crossing the border fall back to the network`() {
        assertFalse(galicia.covers(43.36, -5.85, 20_000.0))    // Oviedo
        assertFalse(galicia.covers(41.45, -8.50, 20_000.0))    // Braga: dataset ends too close
    }
}
