package com.tonetxo.galmap.data

import com.tonetxo.galmap.ar.GeoArMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Serves features around a point. Inside the bundled dataset (Galicia) everything is
 * offline; elsewhere it hits Overpass only when the requested circle is not inside the last
 * downloaded one. That last download is persisted so the app can start without coverage in
 * places it has already visited.
 */
class FeatureRepository(
    private val cacheFile: File,
    private val bundled: BundledDataset? = null,
    private val client: OverpassClient = OverpassClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var area: CachedArea? = null
    private var diskLoaded = false

    suspend fun featuresAround(lat: Double, lon: Double, radiusM: Double): List<GeoFeature> =
        mutex.withLock {
            area?.takeIf { it.covers(lat, lon, radiusM, clock()) }?.let { return it.features }

            val fetchRadius = radiusM + MARGIN_M
            bundled?.featuresAround(lat, lon, fetchRadius)?.let { features ->
                return CachedArea(CACHE_VERSION, lat, lon, fetchRadius, clock(), features)
                    .ranked().also { area = it }.features
            }

            loadFromDisk()?.takeIf { it.covers(lat, lon, radiusM, clock()) }?.let { return it.features }

            val fresh = CachedArea(CACHE_VERSION, lat, lon, fetchRadius, clock(), client.fetch(lat, lon, fetchRadius))
            saveToDisk(fresh)
            fresh.ranked().also { area = it }.features
        }

    private suspend fun loadFromDisk(): CachedArea? {
        if (diskLoaded) return null
        diskLoaded = true
        return withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString<CachedArea>(cacheFile.readText()) }.getOrNull()
        }?.takeIf { it.version == CACHE_VERSION }?.ranked()?.also { area = it }
    }

    private suspend fun saveToDisk(cached: CachedArea) = withContext(Dispatchers.IO) {
        runCatching { cacheFile.writeText(json.encodeToString(CachedArea.serializer(), cached)) }
    }

    @Serializable
    private data class CachedArea(
        val version: Int = 0,
        val centerLat: Double,
        val centerLon: Double,
        val radiusM: Double,
        val fetchedAtMs: Long,
        val features: List<GeoFeature>,
    ) {
        /** Importance is derived data, so it is computed on load and rule changes need no migration. */
        suspend fun ranked() = withContext(Dispatchers.Default) { copy(features = ImportanceRanker.rank(features)) }

        fun covers(lat: Double, lon: Double, radius: Double, nowMs: Long): Boolean =
            nowMs - fetchedAtMs < MAX_AGE_MS &&
                GeoArMath.distanceMeters(centerLat, centerLon, lat, lon) + radius <= radiusM
    }

    private companion object {
        /** Extra radius downloaded so that walking around does not trigger new requests. */
        const val MARGIN_M = 5_000.0
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

        /** Bump when [GeoFeature] gains data that old caches lack. */
        const val CACHE_VERSION = 3
    }
}
