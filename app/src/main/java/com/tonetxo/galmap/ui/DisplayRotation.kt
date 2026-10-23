package com.tonetxo.galmap.ui

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView

/**
 * Current Surface.ROTATION_* of the display showing this composition. Listens to the display
 * directly because flipping between the two landscapes (or portraits) does not recreate the
 * activity, so no configuration change would tell us.
 */
@Composable
fun rememberDisplayRotation(): Int {
    val view = LocalView.current
    var rotation by remember { mutableIntStateOf(view.display?.rotation ?: 0) }
    DisposableEffect(view) {
        val displayManager = view.context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                val display = view.display ?: return
                if (display.displayId == displayId) rotation = display.rotation
            }

            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        view.display?.let { rotation = it.rotation }
        onDispose { displayManager.unregisterDisplayListener(listener) }
    }
    return rotation
}
