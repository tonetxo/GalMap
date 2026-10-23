package com.tonetxo.galmap.data

import com.tonetxo.galmap.ar.GeoArMath

/**
 * OSM rarely carries prominence or wiki links for Galician hills, so peaks are ranked by
 * topographic isolation: the distance to the nearest higher peak in the same dataset.
 * Peaks near the edge of the downloaded area may be overrated, which the download margin
 * keeps mostly outside the visible radius.
 */
object ImportanceRanker {

    /** Isolation needed for importance 1, 2 and 3. Tuned on real data around Santiago. */
    private val ISOLATION_THRESHOLDS_M = doubleArrayOf(1_000.0, 3_000.0, 8_000.0)

    /** Low hills can be very isolated (e.g. on islands) but are not main landmarks. */
    private const val MAIN_PEAK_MIN_ELEVATION_M = 300.0

    fun rank(features: List<GeoFeature>): List<GeoFeature> {
        val peaks = features.filter { it.type.filterType == FeatureType.PEAK && it.elevationM != null }
        return features.map { f -> f.copy(importance = importanceOf(f, peaks)) }
    }

    private fun importanceOf(f: GeoFeature, peaks: List<GeoFeature>): Int {
        if (f.wikidata != null || f.wikipedia != null) return Importance.MAX
        return when (f.type) {
            FeatureType.PEAK, FeatureType.VOLCANO -> {
                val elevation = f.elevationM ?: return Importance.MIN
                val level = levelFor(isolationMeters(f, elevation, peaks))
                if (elevation < MAIN_PEAK_MIN_ELEVATION_M) level.coerceAtMost(Importance.MAX - 1) else level
            }
            FeatureType.ISLAND -> 2
            FeatureType.SADDLE, FeatureType.WATERFALL -> 1
            // The Rías coast has hundreds of named rocks and points: only with "All".
            FeatureType.ISLET, FeatureType.CAPE -> Importance.MIN
        }
    }

    private fun isolationMeters(peak: GeoFeature, elevation: Double, peaks: List<GeoFeature>): Double {
        var nearest = Double.POSITIVE_INFINITY
        for (other in peaks) {
            if (other.elevationM!! <= elevation) continue
            val d = GeoArMath.distanceMeters(peak.lat, peak.lon, other.lat, other.lon)
            if (d < nearest) nearest = d
        }
        return nearest
    }

    private fun levelFor(isolationM: Double): Int =
        ISOLATION_THRESHOLDS_M.count { isolationM >= it }
}
