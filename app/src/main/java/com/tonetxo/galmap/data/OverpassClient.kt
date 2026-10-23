package com.tonetxo.galmap.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Queries the Overpass API, falling back to mirrors when a server is busy or down. */
class OverpassClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) {

    suspend fun fetch(lat: Double, lon: Double, radiusM: Double): List<GeoFeature> =
        withContext(Dispatchers.IO) {
            val query = buildQuery(lat, lon, radiusM)
            var lastError: IOException? = null
            for (endpoint in endpoints) {
                try {
                    return@withContext OverpassParser.parse(post(endpoint, query))
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw lastError ?: IOException("No Overpass endpoints configured")
        }

    private fun post(url: String, query: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .post(FormBody.Builder().add("data", query).build())
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} (${request.url.host})")
            return response.body?.string() ?: throw IOException("Empty response (${request.url.host})")
        }
    }

    companion object {
        private const val USER_AGENT = "GalMap/0.1 (Android)"

        val DEFAULT_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )

        fun buildQuery(lat: Double, lon: Double, radiusM: Double): String {
            val around = String.format(Locale.US, "around:%.0f,%.6f,%.6f", radiusM, lat, lon)
            return """
                [out:json][timeout:25];
                (
                  nwr["natural"~"^(peak|volcano|saddle|cape)$"]["name"]($around);
                  nwr["mountain_pass"="yes"]["name"]($around);
                  nwr["place"~"^(island|islet)$"]["name"]($around);
                  nwr["waterway"="waterfall"]["name"]($around);
                );
                out center tags;
            """.trimIndent()
        }

        private fun defaultHttpClient() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .build()
    }
}
