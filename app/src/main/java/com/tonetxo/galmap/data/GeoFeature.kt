package com.tonetxo.galmap.data

import kotlinx.serialization.Serializable

@Serializable
enum class FeatureType(val emoji: String, val atSeaLevel: Boolean) {
    PEAK("⛰️", false),
    VOLCANO("🌋", false),
    SADDLE("🥾", false),
    WATERFALL("💧", false),
    CAPE("🌊", true),
    ISLAND("🏝️", true),
    ISLET("🪨", true),
    ;

    /** Type shown in the filter: volcanoes are grouped with peaks and islets with islands. */
    val filterType: FeatureType
        get() = when (this) {
            VOLCANO -> PEAK
            ISLET -> ISLAND
            else -> this
        }

    companion object {
        val filterable = entries.filter { it.filterType == it }
    }
}

@Serializable
data class GeoFeature(
    val id: String,
    val name: String,
    val type: FeatureType,
    val lat: Double,
    val lon: Double,
    /** Metres above sea level, or null when unknown. */
    val elevationM: Double?,
    val wikidata: String? = null,
    val wikipedia: String? = null,
    /** From [Importance.MIN] to [Importance.MAX]; see [ImportanceRanker]. */
    val importance: Int = Importance.MIN,
)

object Importance {
    const val MIN = 0
    const val MAX = 3
    const val DEFAULT = 1
}

data class FeatureFilter(
    val types: Set<FeatureType> = FeatureType.filterable.toSet(),
    /** Hides islets, capes and minor sub-summits until the user asks for everything. */
    val minImportance: Int = Importance.DEFAULT,
) {
    fun accepts(feature: GeoFeature): Boolean =
        feature.type.filterType in types && feature.importance >= minImportance
}

data class UserLocation(
    val lat: Double,
    val lon: Double,
    /** Metres above mean sea level, or null when the fix has no altitude. */
    val altitudeM: Double?,
    val accuracyM: Float,
)
