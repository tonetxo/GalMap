package com.tonetxo.galmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

@Composable
fun ArScreen(viewModel: ArViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val topReservedPx = WindowInsets.statusBars.getTop(density) + with(density) { CompassHeight.toPx() }
    val displayRotation = rememberDisplayRotation()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(displayRotation) { viewModel.setDisplayRotation(displayRotation) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            displayRotation = displayRotation,
            onIntrinsics = viewModel::setCameraIntrinsics,
            modifier = Modifier.fillMaxSize(),
        )

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

        StatusBar(
            state = state,
            onOpenSettings = { showSettings = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(12.dp)
                .widthIn(max = 480.dp),
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            onRadiusChange = viewModel::setRadiusKm,
            onTypeToggle = viewModel::setTypeEnabled,
            onMinImportanceChange = viewModel::setMinImportance,
            onResetOffset = viewModel::resetHeadingOffset,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun StatusBar(state: ArUiState, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(PanelColor, RoundedCornerShape(12.dp))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            statusText(state),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title), tint = Color.White)
        }
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
