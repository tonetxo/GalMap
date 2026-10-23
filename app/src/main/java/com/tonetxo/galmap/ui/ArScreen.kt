package com.tonetxo.galmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tonetxo.galmap.R
import kotlin.math.roundToInt

private val CompassHeight = 40.dp
private val PanelColor = Color.Black.copy(alpha = 0.55f)

private const val MIN_RADIUS_KM = 5
private const val MAX_RADIUS_KM = 40
private const val RADIUS_STEP_KM = 5

@Composable
fun ArScreen(viewModel: ArViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val topReservedPx = WindowInsets.statusBars.getTop(density) + with(density) { CompassHeight.toPx() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(onIntrinsics = viewModel::setCameraIntrinsics, modifier = Modifier.fillMaxSize())

        ArOverlay(
            labels = state.labels,
            topReservedPx = topReservedPx,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewModel.setViewport(it.width, it.height) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        viewModel.adjustHeadingOffset(dragAmount)
                    }
                },
        )

        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            CompassTape(state.trueHeadingDeg, state.focalPx, Modifier.fillMaxWidth().height(CompassHeight))
            if (state.compassUnreliable) {
                Text(
                    text = stringResource(R.string.compass_warning),
                    color = Color.Black,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFFFC247)).padding(8.dp),
                )
            }
        }

        ControlPanel(
            state = state,
            onRadiusChange = viewModel::setRadiusKm,
            onResetOffset = viewModel::resetHeadingOffset,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
        )
    }
}

@Composable
private fun ControlPanel(
    state: ArUiState,
    onRadiusChange: (Int) -> Unit,
    onResetOffset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(PanelColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(statusText(state), color = Color.White, style = MaterialTheme.typography.bodySmall)

        Text(
            stringResource(R.string.radius_label, state.radiusKm),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        Slider(
            value = state.radiusKm.toFloat(),
            onValueChange = { onRadiusChange(it.roundToInt()) },
            valueRange = MIN_RADIUS_KM.toFloat()..MAX_RADIUS_KM.toFloat(),
            steps = (MAX_RADIUS_KM - MIN_RADIUS_KM) / RADIUS_STEP_KM - 1,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.calibration_label, state.headingOffsetDeg),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = onResetOffset, enabled = state.headingOffsetDeg != 0f) {
                Text(stringResource(R.string.calibration_reset))
            }
        }
        Text(
            stringResource(R.string.calibration_hint),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun statusText(state: ArUiState): String = when {
    state.location == null -> stringResource(R.string.status_waiting_gps)
    state.loading -> stringResource(R.string.status_loading)
    state.error != null -> stringResource(R.string.status_error, state.error)
    else -> pluralStringResource(
        R.plurals.status_ready,
        state.placesInRadius,
        state.placesInRadius,
        state.location.accuracyM.roundToInt(),
    )
}
