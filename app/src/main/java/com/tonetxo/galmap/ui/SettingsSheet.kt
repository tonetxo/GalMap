package com.tonetxo.galmap.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.tonetxo.galmap.R
import com.tonetxo.galmap.data.FeatureType
import com.tonetxo.galmap.data.Importance
import kotlin.math.roundToInt

private const val MIN_RADIUS_KM = 5
private const val MAX_RADIUS_KM = 40
private const val RADIUS_STEP_KM = 5
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

private val ImportanceNames = intArrayOf(
    R.string.importance_all,
    R.string.importance_relevant,
    R.string.importance_notable,
    R.string.importance_main,
)

@StringRes
private fun FeatureType.labelRes(): Int = when (this) {
    FeatureType.PEAK, FeatureType.VOLCANO -> R.string.type_peak
    FeatureType.SADDLE -> R.string.type_saddle
    FeatureType.WATERFALL -> R.string.type_waterfall
    FeatureType.CAPE -> R.string.type_cape
    FeatureType.ISLAND, FeatureType.ISLET -> R.string.type_island
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    onRadiusChange: (Int) -> Unit,
    onTypeToggle: (FeatureType, Boolean) -> Unit,
    onMinImportanceChange: (Int) -> Unit,
    onResetOffset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

            SectionTitle(stringResource(R.string.radius_label, settings.radiusKm))
            Slider(
                value = settings.radiusKm.toFloat(),
                onValueChange = { onRadiusChange(it.roundToInt()) },
                valueRange = MIN_RADIUS_KM.toFloat()..MAX_RADIUS_KM.toFloat(),
                steps = (MAX_RADIUS_KM - MIN_RADIUS_KM) / RADIUS_STEP_KM - 1,
            )

            SectionTitle(stringResource(R.string.filter_types))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (type in FeatureType.filterable) {
                    val selected = type in settings.filter.types
                    FilterChip(
                        selected = selected,
                        onClick = { onTypeToggle(type, !selected) },
                        label = { Text("${type.emoji} ${stringResource(type.labelRes())}") },
                    )
                }
            }

            SectionTitle(
                stringResource(R.string.importance_label, stringResource(ImportanceNames[settings.filter.minImportance]))
            )
            Slider(
                value = settings.filter.minImportance.toFloat(),
                onValueChange = { onMinImportanceChange(it.roundToInt()) },
                valueRange = Importance.MIN.toFloat()..Importance.MAX.toFloat(),
                steps = Importance.MAX - Importance.MIN - 1,
            )
            Hint(stringResource(R.string.importance_hint))

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.calibration_label, settings.headingOffsetDeg),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onResetOffset, enabled = settings.headingOffsetDeg != 0f) {
                    Text(stringResource(R.string.calibration_reset))
                }
            }
            Hint(stringResource(R.string.calibration_hint))

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            OsmAttribution()
        }
    }
}

/** Required by the ODbL: the places come from OpenStreetMap, live or bundled. */
@Composable
private fun OsmAttribution() {
    val text = stringResource(R.string.osm_attribution)
    val linkStyle = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    Text(
        buildAnnotatedString { withLink(LinkAnnotation.Url(OSM_COPYRIGHT_URL, linkStyle)) { append(text) } },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
