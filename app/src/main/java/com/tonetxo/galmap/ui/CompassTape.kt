package com.tonetxo.galmap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonetxo.galmap.R
import com.tonetxo.galmap.ar.GeoArMath
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.tan

private val CardinalStyle = TextStyle(color = Color(0xFFFFC247), fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val DegreeStyle = TextStyle(color = Color.White, fontSize = 10.sp)
private val HeadingStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

/** Horizontal compass using the same projection as the labels, so ticks line up with them. */
@Composable
fun CompassTape(trueHeadingDeg: Double?, focalPx: Double, modifier: Modifier = Modifier) {
    val cardinals = stringArrayResource(R.array.cardinal_points)
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        drawRect(Color.Black.copy(alpha = 0.4f))
        if (trueHeadingDeg == null || focalPx <= 0) return@Canvas
        val cx = size.width / 2
        val baseline = size.height

        val start = floor((trueHeadingDeg - 90) / 5).toInt() * 5
        for (deg in start..(start + 180) step 5) {
            val delta = GeoArMath.signedDeltaDegrees(trueHeadingDeg, deg.toDouble())
            if (abs(delta) >= 80) continue
            val x = (cx + focalPx * tan(Math.toRadians(delta))).toFloat()
            if (x < 0 || x > size.width) continue
            val normalized = GeoArMath.normalizeDegrees(deg.toDouble()).roundToInt()
            val tick = when {
                normalized % 45 == 0 -> 12.dp.toPx()
                normalized % 15 == 0 -> 8.dp.toPx()
                else -> 4.dp.toPx()
            }
            drawLine(Color.White, Offset(x, baseline), Offset(x, baseline - tick), strokeWidth = 1.dp.toPx())
            val text = when {
                normalized % 45 == 0 -> measurer.measure(cardinals[normalized / 45 % 8], CardinalStyle)
                normalized % 15 == 0 -> measurer.measure("$normalized", DegreeStyle)
                else -> null
            }
            text?.let { drawText(it, topLeft = Offset(x - it.size.width / 2, 2.dp.toPx())) }
        }

        val marker = Path().apply {
            moveTo(cx - 6.dp.toPx(), baseline)
            lineTo(cx + 6.dp.toPx(), baseline)
            lineTo(cx, baseline - 8.dp.toPx())
            close()
        }
        drawPath(marker, Color(0xFFFF5252))
        val heading = measurer.measure("${trueHeadingDeg.roundToInt() % 360}°", HeadingStyle)
        drawText(heading, topLeft = Offset(size.width - heading.size.width - 8.dp.toPx(), size.height - heading.size.height - 2.dp.toPx()))
    }
}
