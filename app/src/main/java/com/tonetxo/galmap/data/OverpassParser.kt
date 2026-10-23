package com.tonetxo.galmap.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Maps an Overpass `[out:json]` response into [GeoFeature]s. */
object OverpassParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val numberRegex = Regex("""-?\d+(?:[.,]\d+)?""")
    private const val FEET_TO_METRES = 0.3048

    @Serializable
    private data class Response(val elements: List<Element> = emptyList())

    @Serializable
    private data class Element(
        val type: String,
        val id: Long,
        val lat: Double? = null,
        val lon: Double? = null,
        val center: Center? = null,
        val tags: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class Center(val lat: Double, val lon: Double)

    fun parse(body: String): List<GeoFeature> =
        json.decodeFromString<Response>(body).elements
            .mapNotNull(::toFeature)
            .distinctBy { it.id }

    private fun toFeature(element: Element): GeoFeature? {
        val tags = element.tags
        val name = (tags["name:gl"] ?: tags["name"])?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val type = featureType(tags) ?: return null
        val lat = element.lat ?: element.center?.lat ?: return null
        val lon = element.lon ?: element.center?.lon ?: return null
        val elevation = parseElevation(tags["ele"]) ?: if (type.atSeaLevel) 0.0 else null
        return GeoFeature("${element.type}/${element.id}", name, type, lat, lon, elevation)
    }

    private fun featureType(tags: Map<String, String>): FeatureType? = when {
        tags["natural"] == "peak" -> FeatureType.PEAK
        tags["natural"] == "volcano" -> FeatureType.VOLCANO
        tags["natural"] == "saddle" || tags["mountain_pass"] == "yes" -> FeatureType.SADDLE
        tags["natural"] == "cape" -> FeatureType.CAPE
        tags["place"] == "island" || tags["place"] == "islet" -> FeatureType.ISLAND
        tags["waterway"] == "waterfall" -> FeatureType.WATERFALL
        else -> null
    }

    /** Extracts metres from free-form `ele` values such as "1234", "1234 m", "560,5" or "1500 ft". */
    fun parseElevation(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val value = numberRegex.find(raw)?.value?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val isFeet = raw.contains("ft", ignoreCase = true) || raw.contains('\'')
        return if (isFeet) value * FEET_TO_METRES else value
    }
}
