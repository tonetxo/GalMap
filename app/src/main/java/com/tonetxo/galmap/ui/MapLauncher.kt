package com.tonetxo.galmap.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tonetxo.galmap.data.GeoFeature
import java.util.Locale

private const val EARTH_PACKAGE = "com.google.earth"

/**
 * Shows [feature] marked on a map.
 *
 * The Google Earth app only drops a pin (and shows its info card) for a search by name;
 * with coordinates it just moves the camera. Names repeat a lot in Galicia, so Earth is used
 * only for places with a wiki link, whose names are unambiguous. Everything else opens a
 * `geo:` pin at the exact coordinates (Google Maps), with the website as a last resort.
 */
fun openInMap(context: Context, feature: GeoFeature) {
    val lat = String.format(Locale.US, "%.6f", feature.lat)
    val lon = String.format(Locale.US, "%.6f", feature.lon)

    val earth = Intent(Intent.ACTION_VIEW, Uri.parse("https://earth.google.com/web/search/${Uri.encode(feature.name)}/"))
        .setPackage(EARTH_PACKAGE)
        .takeIf { feature.wikidata != null || feature.wikipedia != null }
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(feature.name)})"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon"))

    for (intent in listOfNotNull(earth, geo, web)) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next option.
        }
    }
}
