package com.tonetxo.galmap.ui

import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.max
import kotlin.math.min

/** Full-screen back camera preview. Reports the lens intrinsics needed to place labels. */
@Composable
fun CameraPreview(
    displayRotation: Int,
    onIntrinsics: (CameraIntrinsics?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnIntrinsics by rememberUpdatedState(onIntrinsics)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    var preview by remember { mutableStateOf<Preview?>(null) }
    val currentRotation by rememberUpdatedState(displayRotation)

    AndroidView(factory = { previewView }, modifier = modifier)

    // A 90°→270° flip keeps the activity alive, so the use case must be told explicitly.
    LaunchedEffect(preview, displayRotation) {
        preview?.targetRotation = displayRotation
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val cameraProvider = providerFuture.get().also { provider = it }
            // 4:3 matches the sensor, so the stream is not cropped before the view crops it.
            val newPreview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()
                )
                .setTargetRotation(currentRotation)
                .build()
            newPreview.setSurfaceProvider(previewView.surfaceProvider)
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, newPreview)
            preview = newPreview
            currentOnIntrinsics(readIntrinsics(camera.cameraInfo))
        }, ContextCompat.getMainExecutor(context))

        onDispose { provider?.unbindAll() }
    }
}

@OptIn(ExperimentalCamera2Interop::class)
private fun readIntrinsics(cameraInfo: CameraInfo): CameraIntrinsics? = runCatching {
    val info = Camera2CameraInfo.from(cameraInfo)
    val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val physical = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val pixelArray = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
    val active = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
    if (focal == null || physical == null || pixelArray == null || active == null) return@runCatching null
    // Only the active area of the pixel array produces image.
    val widthMm = physical.width.toDouble() * active.width() / pixelArray.width
    val heightMm = physical.height.toDouble() * active.height() / pixelArray.height
    CameraIntrinsics(focal.toDouble(), max(widthMm, heightMm), min(widthMm, heightMm))
}.getOrNull()
