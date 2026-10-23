package com.tonetxo.galmap.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportanceRankerTest {

    private fun peak(id: String, ele: Double, lat: Double, lon: Double = -9.0, wiki: String? = null) =
        GeoFeature(id, id, FeatureType.PEAK, lat, lon, ele, wikidata = wiki)

    private fun importanceById(features: List<GeoFeature>) =
        ImportanceRanker.rank(features).associate { it.id to it.importance }

    @Test
    fun `peaks rank by distance to the nearest higher peak`() {
        // ~111 m per 0.001° of latitude.
        val ranks = importanceById(
            listOf(
                peak("summit", 680.0, 42.60),
                peak("shoulder", 650.0, 42.605), // 0.55 km from a higher peak
                peak("hill", 400.0, 42.62),      // 2.2 km
                peak("ridge", 500.0, 42.65),     // 5.6 km
                peak("far", 600.0, 42.70),       // 11 km
            )
        )
        assertEquals(Importance.MAX, ranks["summit"])
        assertEquals(0, ranks["shoulder"])
        assertEquals(1, ranks["hill"])
        assertEquals(2, ranks["ridge"])
        assertEquals(Importance.MAX, ranks["far"])
    }

    @Test
    fun `isolated low hills are capped below main`() {
        val ranks = importanceById(listOf(peak("islandTop", 166.0, 42.60)))
        assertEquals(Importance.MAX - 1, ranks["islandTop"])
    }

    @Test
    fun `coast features rank by type unless they have a wiki link`() {
        fun f(id: String, type: FeatureType, wiki: String? = null) =
            GeoFeature(id, id, type, 42.5, -9.0, 0.0, wikidata = wiki)
        val ranks = importanceById(
            listOf(
                f("islet", FeatureType.ISLET),
                f("cape", FeatureType.CAPE),
                f("island", FeatureType.ISLAND),
                f("salvora", FeatureType.ISLAND, wiki = "Q1"),
                f("corrubedo", FeatureType.CAPE, wiki = "Q2"),
            )
        )
        assertEquals(Importance.MIN, ranks["islet"])
        assertEquals(Importance.MIN, ranks["cape"])
        assertEquals(2, ranks["island"])
        assertEquals(Importance.MAX, ranks["salvora"])
        assertEquals(Importance.MAX, ranks["corrubedo"])
    }

    @Test
    fun `filter groups islets with islands and volcanoes with peaks`() {
        val filter = FeatureFilter(types = setOf(FeatureType.ISLAND), minImportance = Importance.MIN)
        val islet = GeoFeature("i", "i", FeatureType.ISLET, 0.0, 0.0, 0.0)
        val volcano = GeoFeature("v", "v", FeatureType.VOLCANO, 0.0, 0.0, 100.0)
        assertEquals(true, filter.accepts(islet))
        assertEquals(false, filter.accepts(volcano))
        assertEquals(listOf(FeatureType.PEAK, FeatureType.SADDLE, FeatureType.WATERFALL, FeatureType.CAPE, FeatureType.ISLAND), FeatureType.filterable)
    }
}
