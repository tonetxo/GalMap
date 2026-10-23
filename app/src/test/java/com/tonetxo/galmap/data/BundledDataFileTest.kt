package com.tonetxo.galmap.data

import com.tonetxo.galmap.ar.GeoArMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/** Checks the generated asset is readable by the app's parser and contains known places. */
class BundledDataFileTest {

    private val features by lazy {
        val body = GZIPInputStream(File("src/main/assets/dataset.json.gz").inputStream()).bufferedReader().use { it.readText() }
        OverpassParser.parse(body)
    }

    @Test
    fun `dataset parses with the live parser`() {
        assertTrue("only ${features.size} features", features.size > 5_000)
    }

    @Test
    fun `dataset contains landmarks across Galicia`() {
        val names = features.map { it.name }.toSet()
        for (name in listOf("A Curota", "Iroite", "O Pico Sacro", "Illa de Sálvora", "Cabo Fisterra", "Pena Trevinca")) {
            assertTrue("missing $name", name in names)
        }
        assertEquals(FeatureType.ISLAND, features.first { it.name == "Illa de Sálvora" }.type)
    }

    @Test
    fun `ranking a dense coastal area stays fast`() {
        val ribeira = features.filter { GeoArMath.distanceMeters(42.5545, -8.9910, it.lat, it.lon) <= 45_000 }
        val start = System.nanoTime()
        val ranked = ImportanceRanker.rank(ribeira)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(ribeira.size, ranked.size)
        assertTrue("ranking ${ribeira.size} features took $ms ms", ms < 1_000)
    }
}

