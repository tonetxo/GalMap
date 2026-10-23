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
)

data class UserLocation(
    val lat: Double,
    val lon: Double,
    /** Metres above mean sea level, or null when the fix has no altitude. */
    val altitudeM: Double?,
    val accuracyM: Float,
)
