package com.tonetxo.galmap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonetxo.galmap.ar.ArLabel
import com.tonetxo.galmap.data.GeoFeature
import java.util.Locale
import kotlin.math.roundToInt

private val NameStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val DetailStyle = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
private val ChipColor = Color.Black.copy(alpha = 0.55f)
private val StemColor = Color.White.copy(alpha = 0.8f)
private val HighlightColor = Color(0xFFFFC247)

private const val MAX_STACK_LEVELS = 8

private class PlacedChip(
    val label: ArLabel,
    val rect: Rect,
    val name: TextLayoutResult,
    val detail: TextLayoutResult,
)

/** Chips from the last frame, for hit testing taps without recomposing on every frame. */
private class ChipHits {
    var chips: List<PlacedChip> = emptyList()

    fun featureAt(position: Offset): GeoFeature? =
        chips.lastOrNull { it.rect.inflate(TOUCH_SLOP_PX).contains(position) }?.label?.feature

    private companion object {
        const val TOUCH_SLOP_PX = 12f
    }
}

/**
 * Draws a marker and a chip for each label. Chips sit above their point; when one would
 * overlap an already placed (more important) chip it is stacked higher, and dropped if no
 * free level fits below [topReservedPx]. While a feature is [selected], its chip is
 * highlighted and a crosshair marks the screen centre for alignment.
 */
@Composable
fun ArOverlay(
    labels: List<ArLabel>,
    topReservedPx: Float,
    selected: GeoFeature?,
    onLabelTap: (GeoFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val hits = remember { ChipHits() }
    val currentOnLabelTap by rememberUpdatedState(onLabelTap)

    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { position -> hits.featureAt(position)?.let(currentOnLabelTap) }
        }
    ) {
        val chips = layoutChips(labels, textMeasurer, topReservedPx)
        hits.chips = chips
        for (chip in chips) {
            val anchor = Offset(chip.label.x, chip.label.y)
            drawLine(StemColor, anchor, Offset(anchor.x, chip.rect.bottom), strokeWidth = 1.5.dp.toPx())
            drawCircle(Color.White, radius = 3.dp.toPx(), center = anchor)
        }
        for (chip in chips) drawChip(chip, highlighted = chip.label.feature.id == selected?.id)
        if (selected != null) drawCrosshair()
    }
}

private fun DrawScope.layoutChips(labels: List<ArLabel>, measurer: TextMeasurer, topReservedPx: Float): List<PlacedChip> {
    val padding = 6.dp.toPx()
    val stem = 28.dp.toPx()
    val gap = 4.dp.toPx()
    val edge = 4.dp.toPx()
    val maxTextWidth = (size.width * 0.6f).roundToInt()
    val placed = ArrayList<PlacedChip>(labels.size)

    for (label in labels) {
        val name = measurer.measure(
            "${label.feature.type.emoji} ${label.feature.name}",
            NameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = maxTextWidth),
        )
        val detail = measurer.measure(detailText(label), DetailStyle, maxLines = 1)
        val chipSize = Size(
            maxOf(name.size.width, detail.size.width) + 2 * padding,
            name.size.height + detail.size.height + 2 * padding,
        )
        val left = (label.x - chipSize.width / 2).coerceIn(edge, (size.width - chipSize.width - edge).coerceAtLeast(edge))

        for (level in 0 until MAX_STACK_LEVELS) {
            val bottom = label.y - stem - level * (chipSize.height + gap)
            val top = bottom - chipSize.height
            if (top < topReservedPx) break
            val rect = Rect(left, top, left + chipSize.width, bottom)
            if (placed.none { it.rect.overlaps(rect) }) {
                placed += PlacedChip(label, rect, name, detail)
                break
            }
        }
    }
    return placed
}

private fun DrawScope.drawChip(chip: PlacedChip, highlighted: Boolean) {
    val padding = 6.dp.toPx()
    val corner = CornerRadius(8.dp.toPx())
    drawRoundRect(ChipColor, chip.rect.topLeft, chip.rect.size, corner)
    if (highlighted) {
        drawRoundRect(HighlightColor, chip.rect.topLeft, chip.rect.size, corner, style = Stroke(2.dp.toPx()))
    }
    drawText(chip.name, topLeft = Offset(chip.rect.left + padding, chip.rect.top + padding))
    drawText(chip.detail, topLeft = Offset(chip.rect.left + padding, chip.rect.top + padding + chip.name.size.height))
}

private fun DrawScope.drawCrosshair() {
    val c = center
    val arm = 22.dp.toPx()
    val hole = 6.dp.toPx()
    val stroke = 2.dp.toPx()
    drawCircle(HighlightColor, radius = arm, center = c, style = Stroke(stroke))
    drawLine(HighlightColor, Offset(c.x - arm - hole, c.y), Offset(c.x - hole, c.y), stroke)
    drawLine(HighlightColor, Offset(c.x + hole, c.y), Offset(c.x + arm + hole, c.y), stroke)
    drawLine(HighlightColor, Offset(c.x, c.y - arm - hole), Offset(c.x, c.y - hole), stroke)
    drawLine(HighlightColor, Offset(c.x, c.y + hole), Offset(c.x, c.y + arm + hole), stroke)
}

private fun detailText(label: ArLabel): String {
    val locale = Locale.getDefault()
    val km = label.distanceM / 1000.0
    val distance = if (km < 10) String.format(locale, "%.1f km", km) else String.format(locale, "%d km", km.roundToInt())
    val elevation = label.feature.elevationM?.takeIf { it > 0 }?.let { String.format(locale, "%,d m", it.roundToInt()) }
    return if (elevation != null) "$elevation · $distance" else distance
}
